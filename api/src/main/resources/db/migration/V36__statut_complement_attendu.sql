-- US-04.12 : un paiement au montant inférieur n'est plus seulement rejeté ; le Responsable fiscal
-- peut demander le complément. Nouvelle valeur du type statut_paiement, seule dans ce fichier :
-- une valeur d'enum ne s'utilise pas dans la transaction qui la crée (V37 l'emploie).
ALTER TYPE statut_paiement ADD VALUE IF NOT EXISTS 'complement_attendu' AFTER 'a_verifier';
