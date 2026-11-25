-- e-mine · V25 — Autorité exacte d'une révocation (lot 4, correction 5). L'usager voit qui a révoqué
-- son document (« Directeur national », « Brigade anti-fraude »…) et non « le Ministère ». Ajouts au
-- schéma fourni, à signaler au Ministère.
--
-- 1. Titre anglais des signataires (D4), pour les textes bilingues (notifications, écrans).
ALTER TABLE titre_signataire ADD COLUMN libelle_en text;
UPDATE titre_signataire t SET libelle_en = v.en
  FROM (VALUES ('Ministre', 'Minister'), ('Directeur général', 'Director General'),
               ('Directeur national', 'National Director'), ('Chef du BNE', 'Head of the BNE'))
       AS v(fr, en)
 WHERE t.libelle = v.fr;
UPDATE titre_signataire SET libelle_en = libelle WHERE libelle_en IS NULL;
ALTER TABLE titre_signataire ALTER COLUMN libelle_en SET NOT NULL;

-- 2. Titre officiel d'un agent, parmi ceux des signataires de son profil : le profil Directeur
--    regroupe le Directeur général, le Directeur national et le Chef du BNE (D2), que seule cette
--    information distingue. Facultatif ; renseigné par l'administration des comptes.
ALTER TABLE titre_signataire ADD CONSTRAINT titre_signataire_profil UNIQUE (libelle, profil_id);
ALTER TABLE utilisateur ADD COLUMN titre text;
ALTER TABLE utilisateur ADD CONSTRAINT utilisateur_titre_du_profil
  FOREIGN KEY (titre, profil_id) REFERENCES titre_signataire (libelle, profil_id);
COMMENT ON COLUMN utilisateur.titre IS
  'Titre officiel de l''agent (Directeur national…), parmi les signataires de son profil';

-- 3. Autorité de la révocation, posée par la base avec la date et l'auteur (V24) : le titre officiel
--    de l'auteur ; à défaut, le titre du signataire de la fiche du dépôt quand c'est son profil qui
--    révoque ; sinon le profil de l'auteur (Brigade anti-fraude).
ALTER TABLE document_delivre
  ADD COLUMN revoque_autorite    text,
  ADD COLUMN revoque_autorite_en text;
COMMENT ON COLUMN document_delivre.revoque_autorite IS
  'Autorité qui a révoqué le document (titre du signataire ou profil), affichée à l''usager';

CREATE FUNCTION fn_autorite_revocation(p_utilisateur bigint, p_demande bigint,
                                       OUT fr text, OUT en text)
LANGUAGE sql STABLE AS
$$
  SELECT COALESCE(tu.libelle, ts.libelle, p.libelle),
         COALESCE(tu.libelle_en, ts.libelle_en,
                  CASE p.code WHEN 'BRIGADE' THEN 'Anti-fraud brigade'
                              WHEN 'DIRECTEUR' THEN 'Director General / National Director'
                              WHEN 'MINISTRE' THEN 'Minister / Secretary General'
                              ELSE p.libelle END)
    FROM utilisateur u
    JOIN profil p ON p.id = u.profil_id
    LEFT JOIN titre_signataire tu ON tu.libelle = u.titre
    LEFT JOIN demande d ON d.id = p_demande AND d.signataire_profil_id = u.profil_id
    LEFT JOIN version_document v ON v.id = d.version_document_id
    LEFT JOIN titre_signataire ts ON ts.libelle = v.signe_par
   WHERE u.id = p_utilisateur
$$;

CREATE OR REPLACE FUNCTION fn_document_delivre_revocation() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF OLD.statut = 'non_valide' AND NEW.statut = 'valide' THEN
    RAISE EXCEPTION 'Un document révoqué ne redevient pas valide'
      USING ERRCODE = 'P0001', HINT = 'DOC-006';
  END IF;
  IF OLD.statut = 'valide' AND NEW.statut = 'non_valide' THEN
    NEW.revoque_le := now();
    NEW.revoque_par := fn_current_user_id();
    SELECT a.fr, a.en INTO NEW.revoque_autorite, NEW.revoque_autorite_en
      FROM fn_autorite_revocation(NEW.revoque_par, NEW.demande_id) a;
  END IF;
  RETURN NEW;
END $$;

-- Révocations passées : même règle, d'après l'auteur enregistré.
UPDATE document_delivre dd
   SET revoque_autorite = a.fr, revoque_autorite_en = a.en
  FROM document_delivre x CROSS JOIN LATERAL fn_autorite_revocation(x.revoque_par, x.demande_id) a
 WHERE x.id = dd.id AND dd.statut = 'non_valide' AND dd.revoque_par IS NOT NULL;

-- 4. Notification : l'autorité dans la langue du destinataire.
CREATE OR REPLACE FUNCTION fn_document_delivre_notifier() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  d demande%ROWTYPE;
BEGIN
  SELECT * INTO d FROM demande WHERE id = NEW.demande_id;
  IF FOUND THEN
    PERFORM fn_notifier(d.demandeur_id, 'DOCUMENT_REVOQUE', d.id, NULL,
                        fn_contenu_demande(d.id)
                          || jsonb_build_object('motif', NEW.motif_invalidation,
                                                'document_numero', NEW.numero,
                                                'autorite_fr', COALESCE(NEW.revoque_autorite, 'le Ministère'),
                                                'autorite_en', COALESCE(NEW.revoque_autorite_en, 'the Ministry')));
  END IF;
  RETURN NULL;
END $$;
