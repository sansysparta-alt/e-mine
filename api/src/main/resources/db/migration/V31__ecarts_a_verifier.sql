-- =====================================================================
-- V31 — Traitement des écarts « À vérifier » par le Responsable fiscal (US-04.9)
-- =====================================================================
-- Ce que le rapprochement automatique (V30) n'a pas confirmé attend une décision humaine,
-- motivée et tracée : valider un paiement reçu en trop, rejeter un paiement incomplet, associer
-- une ligne sans référence au bon paiement, classer une ligne sans suite. Les remboursements
-- (doublon, tardif, excédent, somme reçue d'un paiement rejeté) suivent US-04.10.

-- 1. Décision sur une ligne de relevé : qui, quand, quoi, pourquoi.
ALTER TABLE ligne_releve
  ADD COLUMN decision    text CHECK (decision IN ('valide', 'rejete', 'associe', 'classe')),
  ADD COLUMN motif       text CHECK (motif IS NULL OR length(btrim(motif)) BETWEEN 1 AND 2000),
  ADD COLUMN traitee_par bigint REFERENCES utilisateur(id),
  ADD COLUMN traitee_le  timestamptz,
  ADD CONSTRAINT ck_ligne_releve_decision
    CHECK ((decision IS NULL) = (traitee_le IS NULL) AND (decision IS NULL) = (traitee_par IS NULL));

COMMENT ON COLUMN ligne_releve.decision IS
  'Décision du Responsable fiscal sur un écart ou une ligne inconnue (US-04.9)';

-- File « À vérifier » : lignes en écart ou inconnues, pas encore traitées.
DROP INDEX IF EXISTS ix_ligne_releve_statut;
CREATE INDEX ix_ligne_releve_a_verifier ON ligne_releve (created_at)
  WHERE statut IN ('ecart', 'inconnue') AND decision IS NULL;

-- 2. Paiement rejeté : motif communiqué au demandeur, qui doit payer à nouveau.
ALTER TABLE paiement ADD COLUMN motif_rejet text
  CHECK (motif_rejet IS NULL OR length(btrim(motif_rejet)) BETWEEN 1 AND 2000);

INSERT INTO notification_evenement (modele, libelle_fr, libelle_en, sms, email, interne) VALUES
  ('PAIEMENT_REJETE', 'Paiement rejeté, nouveau paiement attendu',
   'Payment rejected, new payment expected', true, true, true);

CREATE OR REPLACE FUNCTION fn_paiement_notifier() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut = 'a_verifier' THEN
    PERFORM fn_notifier(NEW.payeur_id, 'PAIEMENT_A_VERIFIER', NEW.demande_id, NEW.id,
                        jsonb_build_object('reference', NEW.reference,
                                           'montant_gnf', NEW.montant_gnf));
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
                     AND NEW.statut IN ('a_verifier', 'rejete', 'rembourse'))
  EXECUTE FUNCTION fn_paiement_notifier();
