-- =====================================================================
-- V29 — Paiement par virement bancaire (US-04.7, lot 5 V1 « paiement complet »)
-- =====================================================================
-- Le demandeur choisit le virement : la base crée la référence PAY-AAAA-NNNNNN, valable 7 jours
-- (fn_paiement_avant, schéma fourni). Il effectue le virement à sa banque en indiquant cette
-- référence, puis joint son justificatif (PDF, JPG ou PNG, 5 Mo au plus) et déclare « J'ai
-- effectué le virement » : le paiement passe à « en_attente » jusqu'au rapprochement du relevé
-- bancaire par le Responsable fiscal (US-04.8). Le justificatif n'est pas une preuve de paiement :
-- seul le relevé de la banque confirme l'encaissement.

-- 1. Date de la déclaration du demandeur ; le justificatif est une pièce jointe de la demande
--    (stockage, empreinte et RLS communs), rattachée au paiement par justificatif_piece_id.
ALTER TABLE paiement ADD COLUMN virement_declare_le timestamptz;

COMMENT ON COLUMN paiement.virement_declare_le IS
  'Virement déclaré effectué par le demandeur, justificatif joint (US-04.7)';

ALTER TABLE paiement ADD CONSTRAINT ck_paiement_virement_declare
  CHECK (virement_declare_le IS NULL
         OR (moyen = 'VIREMENT' AND justificatif_piece_id IS NOT NULL));

-- 2. Alerte « référence bientôt expirée » : seulement tant que le virement n'est pas déclaré
--    (un virement déclaré attend la banque, le demandeur n'a plus rien à faire).
CREATE OR REPLACE FUNCTION fn_alerter_references_expirantes() RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = emine, pg_temp AS
$$
DECLARE
  p record;
  n integer := 0;
BEGIN
  FOR p IN
    SELECT pa.* FROM paiement pa
     WHERE pa.statut = 'initie'
       AND pa.virement_declare_le IS NULL
       AND pa.date_expiration > now()
       AND pa.date_expiration <= now() + interval '2 days'
       AND pa.moyen = 'VIREMENT'
       AND NOT EXISTS (SELECT 1 FROM notification n
                        WHERE n.paiement_id = pa.id AND n.modele = 'REFERENCE_EXPIRE_BIENTOT')
     FOR UPDATE SKIP LOCKED
  LOOP
    PERFORM fn_notifier(p.payeur_id, 'REFERENCE_EXPIRE_BIENTOT', p.demande_id, p.id,
                        jsonb_build_object('reference', p.reference, 'montant_gnf', p.montant_gnf,
                                           'dateExpiration', p.date_expiration));
    n := n + 1;
  END LOOP;
  RETURN n;
END $$;

REVOKE ALL ON FUNCTION fn_alerter_references_expirantes() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_alerter_references_expirantes() TO emine_app;

-- 3. Paiement en cours de vérification : un virement déclaré, ou un paiement « à vérifier »
--    (montant reçu différent, US-04.4 / US-04.9). Le demandeur n'a rien à faire ; la demande ne
--    figure plus dans « Frais à payer » ou « Droits à payer ».
CREATE INDEX ix_paiement_en_verification ON paiement (demande_id)
  WHERE (moyen = 'VIREMENT' AND statut = 'en_attente') OR statut = 'a_verifier';
