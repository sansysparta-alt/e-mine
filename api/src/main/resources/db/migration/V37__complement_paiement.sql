-- =====================================================================
-- V37 — Complément d'un paiement insuffisant (US-04.12)
-- =====================================================================
-- Décision du porteur du projet (27/11/2026) : « Demander le complément » sur un paiement « à
-- vérifier » au montant inférieur. Le paiement initial passe à `complement_attendu` (il n'est pas
-- rejeté) ; le demandeur paie le reste par le circuit normal, avec un paiement complémentaire lié
-- (`complement_de`). Complément payé : le paiement initial passe « payé » au montant total, daté
-- du complément ; une seule recette, comptée une fois. Le complément a son propre reçu, qui renvoie
-- au paiement initial. Échéance dépassée : rien d'automatique ; le Responsable fiscal peut rejeter
-- (la somme reçue est alors proposée au remboursement, US-04.10).

-- 1. Données du complément sur le paiement initial ; lien du paiement complémentaire.
ALTER TABLE paiement
  ADD COLUMN complement_de        uuid REFERENCES paiement(id),
  ADD COLUMN motif_complement     text
    CHECK (motif_complement IS NULL OR length(btrim(motif_complement)) BETWEEN 1 AND 2000),
  ADD COLUMN complement_echeance  timestamptz,
  ADD CONSTRAINT ck_paiement_complement_attendu
    CHECK (statut <> 'complement_attendu'
           OR (complement_echeance IS NOT NULL AND montant_recu_gnf IS NOT NULL
               AND montant_recu_gnf < montant_gnf));
COMMENT ON COLUMN paiement.complement_de IS
  'Paiement initial dont ce paiement règle le reste (US-04.12) ; jamais compté comme recette';
COMMENT ON COLUMN paiement.motif_complement IS
  'Motif de la demande de complément, communiqué au demandeur (US-04.12)';
COMMENT ON COLUMN paiement.complement_echeance IS
  'Échéance indicative du complément (US-04.12) ; dépassée, rien n''est automatique';
CREATE INDEX ix_paiement_complement_de ON paiement (complement_de) WHERE complement_de IS NOT NULL;

-- Un seul paiement « payé » par demande et par objet : le complément payé n'en est pas un second.
DROP INDEX ux_paiement_paye;
CREATE UNIQUE INDEX ux_paiement_paye ON paiement (demande_id, objet)
  WHERE statut = 'paye' AND complement_de IS NULL;

-- 2. Transitions : demander le complément, puis « payé » (complément reçu) ou « rejeté ».
INSERT INTO transition_statut (entite, de, vers) VALUES
  ('paiement', 'a_verifier', 'complement_attendu'),
  ('paiement', 'complement_attendu', 'paye'),
  ('paiement', 'complement_attendu', 'rejete');

-- Décision tracée sur la ligne de relevé.
ALTER TABLE ligne_releve DROP CONSTRAINT ligne_releve_decision_check;
ALTER TABLE ligne_releve ADD CONSTRAINT ligne_releve_decision_check
  CHECK (decision IN ('valide', 'rejete', 'associe', 'classe', 'rembourse', 'complement'));

-- 3. Cohérence d'un paiement complémentaire, contrôlée en base : il règle exactement le reste
--    d'un paiement initial en attente de complément (même demande, même objet), sans imbrication.
CREATE FUNCTION fn_paiement_complement() RETURNS trigger LANGUAGE plpgsql AS
$$
DECLARE
  initial paiement%ROWTYPE;
BEGIN
  IF TG_OP = 'UPDATE' THEN
    IF NEW.complement_de IS DISTINCT FROM OLD.complement_de THEN
      RAISE EXCEPTION 'Le lien d''un complément est figé' USING ERRCODE = 'P0001', HINT = 'PAY-019';
    END IF;
    RETURN NEW;
  END IF;
  IF NEW.complement_de IS NULL THEN
    RETURN NEW;
  END IF;
  SELECT * INTO initial FROM paiement WHERE id = NEW.complement_de;
  IF initial.id IS NULL OR initial.complement_de IS NOT NULL
     OR initial.statut <> 'complement_attendu'
     OR NEW.demande_id <> initial.demande_id OR NEW.objet <> initial.objet
     OR NEW.montant_gnf <> initial.montant_gnf - initial.montant_recu_gnf THEN
    RAISE EXCEPTION 'Aucun complément de ce montant n''est attendu pour ce paiement'
      USING ERRCODE = 'P0001', HINT = 'PAY-019';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_paiement_complement BEFORE INSERT OR UPDATE OF complement_de ON paiement
  FOR EACH ROW EXECUTE FUNCTION fn_paiement_complement();

-- 4. Complément payé : le paiement initial passe « payé » au montant total, à la date du
--    complément (une seule recette). Les effets habituels (droits payés, notification « paiement
--    confirmé ») sont ceux du paiement initial ; le complément ne notifie pas une seconde fois.
CREATE OR REPLACE FUNCTION fn_paiement_confirme() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.complement_de IS NOT NULL THEN
    UPDATE paiement
       SET statut = 'paye', montant_recu_gnf = montant_gnf, date_paiement = NEW.date_paiement
     WHERE id = NEW.complement_de AND statut = 'complement_attendu';
    RETURN NULL;
  END IF;
  IF NEW.objet IN ('DROITS','ATTESTATION') THEN
    UPDATE demande SET statut = 'droits_payes'
     WHERE id = NEW.demande_id AND statut = 'decision';
  END IF;
  PERFORM fn_notifier(NEW.payeur_id, 'PAIEMENT_CONFIRME', NEW.demande_id, NEW.id,
                      jsonb_build_object('reference', NEW.reference, 'montant_gnf', NEW.montant_gnf,
                                         'moyen', NEW.moyen, 'recu', NEW.recu_numero));
  RETURN NULL;
END $$;

-- 5. Notification du complément attendu : reste à payer, référence, échéance, motif.
INSERT INTO notification_evenement (modele, libelle_fr, libelle_en, sms, email, interne) VALUES
  ('PAIEMENT_COMPLEMENT', 'Complément de paiement attendu', 'Payment top-up expected',
   true, true, true);

CREATE OR REPLACE FUNCTION fn_paiement_notifier() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut = 'a_verifier' THEN
    PERFORM fn_notifier(NEW.payeur_id, 'PAIEMENT_A_VERIFIER', NEW.demande_id, NEW.id,
                        jsonb_build_object('reference', NEW.reference,
                                           'montant_gnf', NEW.montant_gnf));
  ELSIF NEW.statut = 'complement_attendu' THEN
    PERFORM fn_notifier(NEW.payeur_id, 'PAIEMENT_COMPLEMENT', NEW.demande_id, NEW.id,
                        jsonb_build_object(
                          'reference', NEW.reference,
                          'montant_gnf', NEW.montant_gnf,
                          'reste_gnf', NEW.montant_gnf - NEW.montant_recu_gnf,
                          'echeance', to_char(NEW.complement_echeance AT TIME ZONE 'Africa/Conakry',
                                              'DD/MM/YYYY'),
                          'motif', COALESCE(NEW.motif_complement, '')));
  ELSIF NEW.statut = 'rejete' THEN
    PERFORM fn_notifier(NEW.payeur_id, 'PAIEMENT_REJETE', NEW.demande_id, NEW.id,
                        jsonb_build_object('reference', NEW.reference,
                                           'montant_gnf', NEW.montant_gnf,
                                           'motif', COALESCE(NEW.motif_rejet, '')));
  ELSIF NEW.statut = 'rembourse' THEN
    PERFORM fn_notifier(NEW.payeur_id, 'REMBOURSEMENT', NEW.demande_id, NEW.id,
                        jsonb_build_object('reference', NEW.reference,
                                           'montant_gnf', COALESCE(
                                             (SELECT r.montant_gnf FROM remboursement r
                                               WHERE r.paiement_id = NEW.id AND r.statut = 'execute'
                                               ORDER BY r.created_at DESC LIMIT 1),
                                             NEW.montant_gnf)));
  END IF;
  RETURN NULL;
END $$;

DROP TRIGGER trg_paiement_notifier ON paiement;
CREATE TRIGGER trg_paiement_notifier AFTER UPDATE OF statut ON paiement
  FOR EACH ROW WHEN (NEW.statut IS DISTINCT FROM OLD.statut
                     AND NEW.statut IN ('a_verifier', 'complement_attendu', 'rejete', 'rembourse'))
  EXECUTE FUNCTION fn_paiement_notifier();

-- 6. Encaissements mensuels (vue du schéma fourni) : le complément n'est pas une recette de plus.
CREATE OR REPLACE VIEW v_encaissement_mensuel WITH (security_invoker = true) AS
SELECT date_trunc('month', date_paiement)::date AS mois, moyen, objet,
       count(*) AS nb_paiements, sum(montant_recu_gnf) AS total_gnf
  FROM paiement
 WHERE statut = 'paye' AND complement_de IS NULL
 GROUP BY 1, 2, 3;
