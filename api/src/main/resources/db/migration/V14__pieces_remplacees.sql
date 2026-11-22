-- e-mine · V14 — Pièces remplacées en réponse à un complément (retour du porteur du projet du
-- 22/11/2026). Quand le demandeur répond à un complément en joignant une pièce déjà présente au
-- dossier (même type), la nouvelle pièce remplace l'ancienne ; l'ancienne n'est pas supprimée :
-- elle reste consultable comme « version précédente » (traçabilité). Une pièce d'un type nouveau
-- s'ajoute en supplément. Ajout au schéma fourni, à signaler au Ministère.

ALTER TABLE piece_jointe
  ADD COLUMN remplacee_le  timestamptz,
  ADD COLUMN remplacee_par bigint REFERENCES piece_jointe(id),
  ADD CONSTRAINT piece_remplacement_complet CHECK ((remplacee_le IS NULL) = (remplacee_par IS NULL));

COMMENT ON COLUMN piece_jointe.remplacee_par IS
  'Pièce qui remplace celle-ci (réponse à un complément) ; la version précédente reste consultable';
