-- =====================================================================
-- V32 — Remboursements validés par le Responsable fiscal (US-04.10) et règle de remboursement
--       des frais gouvernée (décision V1-4 du porteur du projet)
-- =====================================================================
-- L'argent est rendu HORS plateforme, par le Trésor. e-mine trace la demande de remboursement,
-- sa validation (ou son refus motivé) et la référence de l'ordre de remboursement du Trésor.
--
-- Règle V1-4 : les frais de dossier ne sont pas remboursés en cas de rejet de la demande, sauf
-- double paiement ou erreur (montant en trop, paiement rejeté). Le remboursement sur rejet se
-- règle par le circuit commun des paramètres : proposé par l'administrateur, validé par le
-- Ministre, appliqué à sa date d'effet ; une demande garde la règle de son dépôt.

-- =================================================================================================
-- 1. Règle de remboursement versionnée (même circuit que la règle des délais, V27)
-- =================================================================================================
CREATE TABLE version_regle_remboursement (
  version              integer PRIMARY KEY CHECK (version > 0),
  statut               text NOT NULL DEFAULT 'a_valider'
                         CHECK (statut IN ('a_valider', 'publiee', 'refusee', 'retiree')),
  rembourser_si_rejet  boolean NOT NULL,
  date_effet           date NOT NULL,
  motif                text NOT NULL CHECK (char_length(btrim(motif)) BETWEEN 10 AND 2000),
  proposee_par         bigint REFERENCES utilisateur(id),
  proposee_le          timestamptz NOT NULL DEFAULT now(),
  decidee_par          bigint REFERENCES utilisateur(id),
  decidee_le           timestamptz,
  motif_refus          text
    CHECK (motif_refus IS NULL OR char_length(btrim(motif_refus)) BETWEEN 10 AND 2000),
  CHECK ((statut = 'refusee') = (motif_refus IS NOT NULL)),
  CHECK (statut = 'a_valider' OR decidee_le IS NOT NULL)
);
CREATE UNIQUE INDEX version_regle_remboursement_une_ouverte ON version_regle_remboursement ((true))
  WHERE statut = 'a_valider';
COMMENT ON TABLE version_regle_remboursement IS
  'Règle de remboursement des frais sur rejet : proposée par l''administrateur, validée par le Ministre (V32)';

INSERT INTO version_regle_remboursement (version, statut, rembourser_si_rejet, date_effet, motif,
                                         decidee_le)
VALUES (1, 'publiee', false, '2026-01-01',
        'Décision V1-4 du porteur du projet : frais de dossier non remboursés en cas de rejet, sauf double paiement ou erreur',
        now());

CREATE FUNCTION fn_version_regle_remboursement_fige() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF (NEW.version, NEW.rembourser_si_rejet, NEW.motif, NEW.proposee_par, NEW.proposee_le)
     IS DISTINCT FROM
     (OLD.version, OLD.rembourser_si_rejet, OLD.motif, OLD.proposee_par, OLD.proposee_le) THEN
    RAISE EXCEPTION 'Version % : une version proposée ne se modifie pas', OLD.version
      USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  IF OLD.statut <> 'a_valider' THEN
    RAISE EXCEPTION 'Version % : déjà décidée', OLD.version USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_version_regle_remboursement_fige BEFORE UPDATE ON version_regle_remboursement
  FOR EACH ROW EXECUTE FUNCTION fn_version_regle_remboursement_fige();
CREATE TRIGGER trg_version_regle_remboursement_nodelete BEFORE DELETE ON version_regle_remboursement
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();
CREATE TRIGGER trg_version_regle_remboursement_audit
  AFTER INSERT OR UPDATE ON version_regle_remboursement
  FOR EACH ROW EXECUTE FUNCTION fn_audit();

REVOKE INSERT, UPDATE ON version_regle_remboursement FROM emine_app;
GRANT SELECT ON version_regle_remboursement TO emine_app, emine_readonly;

CREATE FUNCTION fn_regle_remboursement_version_en_vigueur(p_date date DEFAULT fn_aujourdhui_conakry())
RETURNS integer LANGUAGE sql STABLE AS
$$
  SELECT max(version) FROM version_regle_remboursement
   WHERE statut = 'publiee' AND date_effet <= p_date
$$;

CREATE FUNCTION fn_proposer_regle_remboursement(p_rembourser_si_rejet boolean, p_motif text,
                                                p_date_effet date)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  d version_regle_remboursement%ROWTYPE;
  v integer;
BEGIN
  IF NOT fn_a_permission('PARAMETRER') OR fn_current_user_id() IS NULL THEN
    RAISE EXCEPTION 'Seul l''administrateur propose une règle de remboursement'
      USING ERRCODE = 'P0001', HINT = 'PERM-001';
  END IF;
  PERFORM pg_advisory_xact_lock(hashtext('version_regle_remboursement'));
  IF EXISTS (SELECT 1 FROM version_regle_remboursement WHERE statut = 'a_valider') THEN
    RAISE EXCEPTION 'Une proposition attend déjà la décision du Ministre'
      USING ERRCODE = 'P0001', HINT = 'DOC-008';
  END IF;
  IF p_date_effet IS NULL OR p_date_effet < fn_aujourdhui_conakry() THEN
    RAISE EXCEPTION 'La date d''effet est aujourd''hui ou plus tard'
      USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  SELECT * INTO d FROM version_regle_remboursement
   WHERE statut = 'publiee' ORDER BY version DESC LIMIT 1;
  IF d.rembourser_si_rejet IS NOT DISTINCT FROM p_rembourser_si_rejet THEN
    RAISE EXCEPTION 'Règle de remboursement : aucune modification'
      USING ERRCODE = 'P0001', HINT = 'DOC-010';
  END IF;
  SELECT max(version) + 1 INTO v FROM version_regle_remboursement;
  INSERT INTO version_regle_remboursement (version, rembourser_si_rejet, date_effet, motif,
                                           proposee_par)
  VALUES (v, p_rembourser_si_rejet, p_date_effet, btrim(p_motif), fn_current_user_id());
  RETURN v;
END $$;
REVOKE ALL ON FUNCTION fn_proposer_regle_remboursement(boolean, text, date) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_proposer_regle_remboursement(boolean, text, date) TO emine_app;

CREATE FUNCTION fn_decider_regle_remboursement(p_version integer, p_decision text,
                                               p_motif text DEFAULT NULL)
RETURNS date
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  r version_regle_remboursement%ROWTYPE;
  effet date;
BEGIN
  SELECT * INTO r FROM version_regle_remboursement WHERE version = p_version FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Version % de la règle de remboursement inconnue', p_version
      USING ERRCODE = 'P0001', HINT = 'SYS-002';
  END IF;
  IF r.statut <> 'a_valider' THEN
    RAISE EXCEPTION 'Version % : déjà décidée', p_version USING ERRCODE = 'P0001', HINT = 'DOC-009';
  END IF;
  IF p_decision IN ('approuver', 'refuser') AND NOT fn_a_permission('VALIDER_CATALOGUE') THEN
    RAISE EXCEPTION 'Seul le Ministre décide de la règle de remboursement'
      USING ERRCODE = 'P0001', HINT = 'PERM-001';
  ELSIF p_decision = 'retirer' AND NOT fn_a_permission('PARAMETRER') THEN
    RAISE EXCEPTION 'Seul l''administrateur retire sa proposition'
      USING ERRCODE = 'P0001', HINT = 'PERM-001';
  ELSIF p_decision NOT IN ('approuver', 'refuser', 'retirer') THEN
    RAISE EXCEPTION 'Décision % inconnue', p_decision USING ERRCODE = 'P0001', HINT = 'VAL-001';
  END IF;
  IF p_decision = 'approuver' THEN
    effet := greatest(r.date_effet, fn_aujourdhui_conakry(),
                      (SELECT max(date_effet) FROM version_regle_remboursement
                        WHERE statut = 'publiee'));
    UPDATE version_regle_remboursement SET statut = 'publiee', date_effet = effet,
           decidee_par = fn_current_user_id(), decidee_le = now()
     WHERE version = r.version;
    RETURN effet;
  END IF;
  UPDATE version_regle_remboursement
     SET statut = CASE p_decision WHEN 'refuser' THEN 'refusee' ELSE 'retiree' END,
         motif_refus = CASE p_decision WHEN 'refuser' THEN btrim(p_motif) END,
         decidee_par = fn_current_user_id(), decidee_le = now()
   WHERE version = r.version;
  RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION fn_decider_regle_remboursement(integer, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_decider_regle_remboursement(integer, text, text) TO emine_app;

-- Une demande garde la règle de remboursement en vigueur à son dépôt.
ALTER TABLE demande ADD COLUMN regle_remboursement_version integer
  REFERENCES version_regle_remboursement(version);
COMMENT ON COLUMN demande.regle_remboursement_version IS
  'Règle de remboursement des frais en vigueur au dépôt (V32)';
UPDATE demande SET regle_remboursement_version = 1 WHERE statut <> 'brouillon';

CREATE FUNCTION fn_demande_regle_remboursement() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut <> 'brouillon' AND (TG_OP = 'INSERT' OR OLD.statut = 'brouillon') THEN
    NEW.regle_remboursement_version := fn_regle_remboursement_version_en_vigueur();
  ELSIF TG_OP = 'UPDATE' AND OLD.statut <> 'brouillon'
        AND NEW.regle_remboursement_version IS DISTINCT FROM OLD.regle_remboursement_version THEN
    RAISE EXCEPTION 'Demande % : la règle de remboursement du dépôt ne se modifie pas', OLD.id
      USING ERRCODE = 'P0001', HINT = 'DEM-001';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_demande_regle_remboursement BEFORE INSERT OR UPDATE ON demande
  FOR EACH ROW EXECUTE FUNCTION fn_demande_regle_remboursement();

-- =================================================================================================
-- 2. Remboursements : numéro, origine, exécution par le Trésor, RLS
-- =================================================================================================
ALTER TABLE remboursement
  ADD COLUMN numero          text UNIQUE CHECK (numero ~ '^RMB-[0-9]{4}-[0-9]{6}$'),
  ADD COLUMN ligne_releve_id bigint REFERENCES ligne_releve(id),
  ADD COLUMN explication     text CHECK (explication IS NULL
                                         OR char_length(btrim(explication)) BETWEEN 1 AND 2000),
  ADD COLUMN motif_refus     text CHECK (motif_refus IS NULL
                                         OR char_length(btrim(motif_refus)) BETWEEN 10 AND 2000),
  ADD COLUMN reference_ordre text CHECK (reference_ordre IS NULL
                                         OR char_length(btrim(reference_ordre)) BETWEEN 3 AND 60),
  ADD COLUMN execute_par     bigint REFERENCES utilisateur(id),
  ADD COLUMN execute_le      timestamptz,
  ADD CONSTRAINT ck_remboursement_refus CHECK ((statut = 'refuse') = (motif_refus IS NOT NULL)),
  ADD CONSTRAINT ck_remboursement_execute
    CHECK ((statut = 'execute') = (reference_ordre IS NOT NULL AND execute_le IS NOT NULL
                                   AND execute_par IS NOT NULL));

COMMENT ON COLUMN remboursement.reference_ordre IS
  'Référence de l''ordre de remboursement émis par le Trésor (exécution hors plateforme)';

-- Une ligne de relevé n'est remboursée qu'une fois.
CREATE UNIQUE INDEX ux_remboursement_ligne ON remboursement (ligne_releve_id)
  WHERE ligne_releve_id IS NOT NULL AND statut <> 'refuse';
CREATE INDEX ix_remboursement_statut ON remboursement (statut, created_at);

-- Numéro attribué par la base (D3) et cycle de vie contrôlé :
-- demande → valide → execute, ou demande → refuse. Rien ne revient en arrière.
CREATE FUNCTION fn_remboursement_avant() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF TG_OP = 'INSERT' THEN
    NEW.numero := COALESCE(NEW.numero, fn_next_numero('RMB', 6));
    IF NEW.statut <> 'demande' THEN
      RAISE EXCEPTION 'Un remboursement commence à l''état « demande »'
        USING ERRCODE = 'P0001', HINT = 'PAY-006';
    END IF;
    RETURN NEW;
  END IF;
  IF NEW.statut IS DISTINCT FROM OLD.statut
     AND NOT ((OLD.statut = 'demande' AND NEW.statut IN ('valide', 'refuse'))
              OR (OLD.statut = 'valide' AND NEW.statut = 'execute')) THEN
    RAISE EXCEPTION 'Remboursement % : passage de % à % interdit', OLD.numero, OLD.statut, NEW.statut
      USING ERRCODE = 'P0001', HINT = 'PAY-006';
  END IF;
  IF (NEW.paiement_id, NEW.motif, NEW.montant_gnf, NEW.demande_par, NEW.numero)
     IS DISTINCT FROM (OLD.paiement_id, OLD.motif, OLD.montant_gnf, OLD.demande_par, OLD.numero) THEN
    RAISE EXCEPTION 'Remboursement % : objet, motif et montant ne se modifient pas', OLD.numero
      USING ERRCODE = 'P0001', HINT = 'PAY-006';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_remboursement_avant BEFORE INSERT OR UPDATE ON remboursement
  FOR EACH ROW EXECUTE FUNCTION fn_remboursement_avant();
CREATE TRIGGER trg_remboursement_nodelete BEFORE DELETE ON remboursement
  FOR EACH ROW EXECUTE FUNCTION fn_interdire_modification();

-- RLS : un usager ne voit que les remboursements de ses paiements ; les relevés bancaires sont
-- réservés aux profils internes.
ALTER TABLE remboursement ENABLE ROW LEVEL SECURITY;
CREATE POLICY remboursement_acces ON remboursement TO emine_app
  USING (fn_est_interne() OR EXISTS (SELECT 1 FROM paiement p WHERE p.id = remboursement.paiement_id))
  WITH CHECK (fn_est_interne());
CREATE POLICY remboursement_lecture ON remboursement FOR SELECT TO emine_readonly USING (true);

ALTER TABLE releve_bancaire ENABLE ROW LEVEL SECURITY;
CREATE POLICY releve_bancaire_acces ON releve_bancaire TO emine_app
  USING (fn_est_interne()) WITH CHECK (fn_est_interne());
CREATE POLICY releve_bancaire_lecture ON releve_bancaire FOR SELECT TO emine_readonly USING (true);
ALTER TABLE ligne_releve ENABLE ROW LEVEL SECURITY;
CREATE POLICY ligne_releve_acces ON ligne_releve TO emine_app
  USING (fn_est_interne()) WITH CHECK (fn_est_interne());
CREATE POLICY ligne_releve_lecture ON ligne_releve FOR SELECT TO emine_readonly USING (true);

-- Refus du remboursement d'un paiement entier : le paiement redevient « payé ».
INSERT INTO transition_statut (entite, de, vers) VALUES ('paiement', 'remboursement_demande', 'paye');

-- =================================================================================================
-- 3. Notification : remboursement effectué (ordre du Trésor enregistré)
-- =================================================================================================
-- Remboursement d'un paiement entier : le paiement passe à « remboursé » et sa propre
-- notification part (V16, V31). Remboursement d'une partie (excédent, doublon, versement tardif,
-- somme reçue d'un paiement rejeté) : la notification part du remboursement.
CREATE FUNCTION fn_remboursement_notifier() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  p paiement%ROWTYPE;
BEGIN
  SELECT * INTO p FROM paiement WHERE id = NEW.paiement_id;
  IF p.statut = 'remboursement_demande' THEN
    UPDATE paiement SET statut = 'rembourse' WHERE id = p.id;
  ELSE
    PERFORM fn_notifier(p.payeur_id, 'REMBOURSEMENT', p.demande_id, p.id,
                        jsonb_build_object('reference', p.reference,
                                           'montant_gnf', NEW.montant_gnf,
                                           'numero', NEW.numero,
                                           'ordre', NEW.reference_ordre));
  END IF;
  RETURN NULL;
END $$;
CREATE TRIGGER trg_remboursement_notifier AFTER UPDATE OF statut ON remboursement
  FOR EACH ROW WHEN (NEW.statut = 'execute' AND OLD.statut IS DISTINCT FROM NEW.statut)
  EXECUTE FUNCTION fn_remboursement_notifier();

-- =================================================================================================
-- 4. Demande rejetée : remboursement des frais proposé si la règle de son dépôt le prévoit
-- =================================================================================================
-- Proposition seulement : le Responsable fiscal valide ou refuse (aucune décision automatique).
CREATE FUNCTION fn_demande_rejetee_remboursement() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  p paiement%ROWTYPE;
BEGIN
  IF NOT COALESCE((SELECT r.rembourser_si_rejet FROM version_regle_remboursement r
                    WHERE r.version = NEW.regle_remboursement_version), false) THEN
    RETURN NULL;
  END IF;
  FOR p IN SELECT * FROM paiement
            WHERE demande_id = NEW.id AND statut = 'paye'
              AND objet IN ('FRAIS_DOSSIER', 'ATTESTATION')
  LOOP
    INSERT INTO remboursement (paiement_id, motif, montant_gnf, demande_par, explication)
    VALUES (p.id, 'rejet_demande', COALESCE(p.montant_recu_gnf, p.montant_gnf),
            COALESCE(fn_current_user_id(), p.payeur_id, NEW.demandeur_id),
            'Demande rejetée : remboursement des frais prévu par la règle de son dépôt');
    UPDATE paiement SET statut = 'remboursement_demande' WHERE id = p.id;
  END LOOP;
  RETURN NULL;
END $$;
CREATE TRIGGER trg_demande_rejetee_remboursement AFTER UPDATE OF statut ON demande
  FOR EACH ROW WHEN (NEW.statut = 'rejetee' AND OLD.statut IS DISTINCT FROM NEW.statut)
  EXECUTE FUNCTION fn_demande_rejetee_remboursement();

-- =================================================================================================
-- 5. Décision « rembourser » sur une ligne de relevé (doublon, versement tardif)
-- =================================================================================================
ALTER TABLE ligne_releve DROP CONSTRAINT ligne_releve_decision_check;
ALTER TABLE ligne_releve ADD CONSTRAINT ligne_releve_decision_check
  CHECK (decision IN ('valide', 'rejete', 'associe', 'classe', 'rembourse'));
