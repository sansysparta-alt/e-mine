-- e-mine · V8 — la recevabilité est déclarée par l'agent du guichet (US-05.1, décision du porteur du
-- projet du 21/11/2026)
-- Dans le schéma fourni, fn_paiement_confirme passait automatiquement la demande de « déposée » à
-- « recevable » dès que les frais de dossier étaient payés, sans examen des pièces. Désormais :
--   * le paiement des frais n'a plus d'effet sur le statut de la demande ;
--   * le passage à « recevable » est fait par l'agent du guichet, et la base le refuse tant que les
--     frais (ou le paiement unique d'une attestation) ne sont pas payés (HINT DEM-006).
-- Le paiement des droits fait toujours passer la demande de « décision » à « droits payés ».
-- Adaptation du schéma à signaler au Ministère.

CREATE OR REPLACE FUNCTION fn_paiement_confirme() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.objet IN ('DROITS','ATTESTATION') THEN
    UPDATE demande SET statut = 'droits_payes'
     WHERE id = NEW.demande_id AND statut = 'decision';
  END IF;
  IF NEW.payeur_id IS NOT NULL THEN
    INSERT INTO notification (destinataire_id, canal, modele, demande_id, paiement_id, contenu)
    VALUES (NEW.payeur_id, 'sms', 'PAIEMENT_CONFIRME', NEW.demande_id, NEW.id,
            jsonb_build_object('reference', NEW.reference, 'montant_gnf', NEW.montant_gnf,
                               'moyen', NEW.moyen, 'recu', NEW.recu_numero));
  END IF;
  RETURN NULL;
END $$;

-- Condition de recevabilité : frais de dossier (ou attestation) payés.
CREATE FUNCTION fn_demande_condition_recevabilite() RETURNS trigger LANGUAGE plpgsql AS
$$
BEGIN
  IF NEW.statut = 'recevable' AND OLD.statut IS DISTINCT FROM 'recevable'
     AND NOT EXISTS (SELECT 1 FROM paiement p
                      WHERE p.demande_id = NEW.id
                        AND p.objet IN ('FRAIS_DOSSIER','ATTESTATION')
                        AND p.statut = 'paye') THEN
    RAISE EXCEPTION 'Demande % non recevable : frais de dossier non payés', NEW.id
      USING ERRCODE = 'P0001', HINT = 'DEM-006';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER trg_demande_condition_recevabilite BEFORE UPDATE OF statut ON demande
  FOR EACH ROW EXECUTE FUNCTION fn_demande_condition_recevabilite();
