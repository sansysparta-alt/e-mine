-- e-mine · V7 — déclaration sur l'honneur au dépôt (US-03.4)
-- Le schéma fourni ne conservait pas l'acceptation de la déclaration sur l'honneur exigée au dépôt.
-- Elle est horodatée ici (l'auteur et l'adresse IP figurent déjà dans le journal d'audit).
-- Pas de contrainte CHECK : les demandes reprises de l'existant n'ont pas cette information ;
-- la règle est appliquée par l'API au dépôt.

ALTER TABLE demande ADD COLUMN declaration_honneur_le timestamptz;

COMMENT ON COLUMN demande.declaration_honneur_le IS
  'Acceptation de la déclaration sur l''honneur par le demandeur, au dépôt (US-03.4)';
