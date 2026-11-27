-- US-06.8, décision V1-11 du Ministère : seul le signataire d'un document l'annule (révocation) ;
-- la Brigade anti-fraude le lui signale, sans effet sur le document : la vérification publique
-- reste « authentique » tant que le signataire n'a pas décidé. Remplace la règle « Directeur ou
-- Brigade » de US-06.6. Tout est tracé (journal d'audit par les triggers existants).

-- 1. Le signataire d'un document : le profil figé au dépôt de sa demande (D4, V18) et, quand
--    l'agent a un titre officiel (V25), le titre de la fiche du dépôt (« Directeur national »…).
CREATE FUNCTION fn_est_signataire_document(p_utilisateur bigint, p_demande bigint)
RETURNS boolean LANGUAGE sql STABLE AS
$$
  SELECT EXISTS (
    SELECT 1
      FROM utilisateur u
      JOIN demande d ON d.id = p_demande AND d.signataire_profil_id = u.profil_id
      LEFT JOIN version_document v ON v.id = d.version_document_id
     WHERE u.id = p_utilisateur
       AND (u.titre IS NULL OR v.signe_par IS NULL OR u.titre = v.signe_par))
$$;

-- 2. Révocation : réservée au signataire (contrôle en base, sous la session de l'appelant).
CREATE OR REPLACE FUNCTION fn_document_delivre_revocation() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF OLD.statut = 'non_valide' AND NEW.statut = 'valide' THEN
    RAISE EXCEPTION 'Un document révoqué ne redevient pas valide'
      USING ERRCODE = 'P0001', HINT = 'DOC-006';
  END IF;
  IF OLD.statut = 'valide' AND NEW.statut = 'non_valide' THEN
    IF NOT fn_est_signataire_document(fn_current_user_id(), NEW.demande_id) THEN
      RAISE EXCEPTION 'Document % : seul son signataire l''annule', NEW.numero
        USING ERRCODE = 'P0001', HINT = 'PERM-001';
    END IF;
    NEW.revoque_le := now();
    NEW.revoque_par := fn_current_user_id();
    SELECT a.fr, a.en INTO NEW.revoque_autorite, NEW.revoque_autorite_en
      FROM fn_autorite_revocation(NEW.revoque_par, NEW.demande_id) a;
  END IF;
  RETURN NEW;
END $$;

-- 3. Signalement d'un document par la Brigade, examiné par le signataire : il annule le document
--    ou classe le signalement, toujours avec un motif.
CREATE TABLE signalement_document (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  document_id     bigint NOT NULL REFERENCES document_delivre(id),
  auteur_id       bigint NOT NULL REFERENCES utilisateur(id),
  motif           text NOT NULL CHECK (char_length(btrim(motif)) BETWEEN 10 AND 2000),
  statut          text NOT NULL DEFAULT 'ouvert' CHECK (statut IN ('ouvert', 'annule', 'classe')),
  decide_par      bigint REFERENCES utilisateur(id),
  decide_le       timestamptz,
  motif_decision  text CHECK (motif_decision IS NULL
                              OR char_length(btrim(motif_decision)) BETWEEN 10 AND 2000),
  created_at      timestamptz NOT NULL DEFAULT now(),
  CHECK ((statut = 'ouvert') = (decide_par IS NULL AND decide_le IS NULL AND motif_decision IS NULL))
);
COMMENT ON TABLE signalement_document IS
  'Signalements d''un document délivré par la Brigade anti-fraude, décidés par le signataire (V35)';
CREATE INDEX ix_signalement_document ON signalement_document (document_id, statut);

CREATE FUNCTION fn_signalement_document() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  demande_doc bigint;
  statut_doc  statut_document;
BEGIN
  SELECT dd.demande_id, dd.statut INTO demande_doc, statut_doc
    FROM document_delivre dd WHERE dd.id = NEW.document_id;
  IF TG_OP = 'INSERT' THEN
    IF NOT EXISTS (SELECT 1 FROM utilisateur u JOIN profil p ON p.id = u.profil_id
                    WHERE u.id = fn_current_user_id() AND p.code = 'BRIGADE') THEN
      RAISE EXCEPTION 'Seule la Brigade anti-fraude signale un document'
        USING ERRCODE = 'P0001', HINT = 'PERM-001';
    END IF;
    IF statut_doc <> 'valide' THEN
      RAISE EXCEPTION 'Le document est déjà annulé' USING ERRCODE = 'P0001', HINT = 'DOC-006';
    END IF;
    NEW.auteur_id := fn_current_user_id();
    NEW.statut := 'ouvert';
    RETURN NEW;
  END IF;
  IF (NEW.document_id, NEW.auteur_id, NEW.motif, NEW.created_at)
     IS DISTINCT FROM (OLD.document_id, OLD.auteur_id, OLD.motif, OLD.created_at)
     OR OLD.statut <> 'ouvert' THEN
    RAISE EXCEPTION 'Signalement % : déjà décidé, ou contenu figé', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DOC-010';
  END IF;
  IF NOT fn_est_signataire_document(fn_current_user_id(), demande_doc) THEN
    RAISE EXCEPTION 'Signalement % : seul le signataire du document le décide', OLD.id
      USING ERRCODE = 'P0001', HINT = 'PERM-001';
  END IF;
  IF NEW.statut = 'annule' AND statut_doc <> 'non_valide' THEN
    RAISE EXCEPTION 'Signalement % : le document doit d''abord être annulé', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DOC-006';
  END IF;
  NEW.decide_par := fn_current_user_id();
  NEW.decide_le := now();
  RETURN NEW;
END $$;
CREATE TRIGGER trg_signalement_document BEFORE INSERT OR UPDATE ON signalement_document
  FOR EACH ROW EXECUTE FUNCTION fn_signalement_document();
CREATE TRIGGER trg_signalement_document_nodelete BEFORE DELETE ON signalement_document
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();

-- Réservé aux profils internes (la Brigade et le signataire) ; l'usager n'en voit rien.
ALTER TABLE signalement_document ENABLE ROW LEVEL SECURITY;
CREATE POLICY signalement_document_acces ON signalement_document TO emine_app
  USING (fn_est_interne()) WITH CHECK (fn_est_interne());
CREATE POLICY signalement_document_lecture ON signalement_document FOR SELECT TO emine_readonly
  USING (true);
