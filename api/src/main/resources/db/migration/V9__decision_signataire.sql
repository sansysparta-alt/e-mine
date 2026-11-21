-- e-mine · V9 — décision du signataire (US-05.4, choix du porteur du projet du 21/11/2026)
-- 1. Sans avis technique en V1 : le signataire décide dès que la demande est recevable.
--    recevable → décision (favorable) ; recevable → rejetée (rejet motivé). Les transitions du
--    schéma fourni (en instruction → avis rendu → décision) restent valables pour le plan complet.
-- 2. D4 : le signataire de chaque document est paramétré dans le catalogue. Le texte « signe_par »
--    est complété par le profil qui signe (et décide), pour que la règle ne soit jamais codée en dur.
-- Adaptation du schéma à signaler au Ministère.

INSERT INTO transition_statut (entite, de, vers) VALUES
  ('demande', 'recevable', 'decision'),
  ('demande', 'recevable', 'rejetee')
ON CONFLICT DO NOTHING;

ALTER TABLE type_document ADD COLUMN signataire_profil_id smallint REFERENCES profil(id);

UPDATE type_document td
   SET signataire_profil_id = p.id
  FROM profil p
 WHERE p.code = CASE
                  WHEN td.signe_par = 'Ministre' THEN 'MINISTRE'
                  WHEN td.signe_par IN ('Directeur national', 'Directeur général', 'Chef du BNE')
                    THEN 'DIRECTEUR'
                END;

-- Échoue si un document du catalogue n'a pas de signataire reconnu (aucun document sans signataire).
ALTER TABLE type_document ALTER COLUMN signataire_profil_id SET NOT NULL;

COMMENT ON COLUMN type_document.signataire_profil_id IS
  'Profil qui décide et signe ce document (D4) ; libellé affiché : signe_par';
