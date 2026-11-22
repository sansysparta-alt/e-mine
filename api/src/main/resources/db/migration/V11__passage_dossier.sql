-- e-mine · V11 — « Passer ce dossier » tracé (correctifs ergonomie, choix du porteur du projet du
-- 22/11/2026). Dans le traitement en série, un agent peut passer un dossier sans agir ; il en donne
-- un motif court. Chaque passage est conservé (qui, quand, motif), visible de tous les agents sur la
-- fiche ; au 3e passage à la même étape, le dossier est signalé « Bloqué » aux décideurs.
-- Ajout au schéma fourni, à signaler au Ministère.

CREATE TABLE passage_dossier (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  demande_id  bigint NOT NULL REFERENCES demande(id),
  auteur_id   bigint NOT NULL REFERENCES utilisateur(id),
  statut      statut_demande NOT NULL,
  motif       text NOT NULL CHECK (char_length(btrim(motif)) BETWEEN 3 AND 300),
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX passage_dossier_demande_idx ON passage_dossier (demande_id, created_at);

COMMENT ON TABLE passage_dossier IS
  'Dossier passé sans action dans le traitement en série : auteur, statut du moment, motif';

-- Journal inaltérable : on ajoute, on ne modifie ni ne supprime.
REVOKE UPDATE, DELETE ON passage_dossier FROM emine_app;

-- Réservé aux profils internes ; l'auteur est toujours l'utilisateur connecté.
ALTER TABLE passage_dossier ENABLE ROW LEVEL SECURITY;
CREATE POLICY passage_dossier_acces ON passage_dossier TO emine_app
  USING (fn_est_interne())
  WITH CHECK (fn_est_interne() AND auteur_id = fn_current_user_id());
CREATE POLICY passage_dossier_lecture ON passage_dossier FOR SELECT TO emine_readonly USING (true);

CREATE TRIGGER trg_passage_dossier_audit AFTER INSERT ON passage_dossier
  FOR EACH ROW EXECUTE FUNCTION fn_audit();
GRANT SELECT ON passage_dossier TO emine_readonly;
