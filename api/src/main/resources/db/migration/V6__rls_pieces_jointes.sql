-- e-mine · V6 — RLS sur les pièces jointes (US-03.2, décision du porteur du projet du 20/11/2026)
-- Le schéma fourni protégeait demande et paiement par la RLS, mais pas piece_jointe : un usager
-- aurait pu lire les métadonnées (nom, empreinte, clé de stockage) des pièces d'un autre si un
-- filtre manquait dans le code. Même règle que pour demande : un profil interne voit tout, un
-- usager ne voit et n'ajoute que les pièces de ses propres demandes. À signaler au Ministère.

ALTER TABLE piece_jointe ENABLE ROW LEVEL SECURITY;

CREATE POLICY piece_jointe_acces ON piece_jointe TO emine_app
  USING (fn_est_interne()
         OR EXISTS (SELECT 1 FROM demande d
                     WHERE d.id = piece_jointe.demande_id AND d.demandeur_id = fn_current_user_id()))
  WITH CHECK (fn_est_interne()
         OR EXISTS (SELECT 1 FROM demande d
                     WHERE d.id = piece_jointe.demande_id AND d.demandeur_id = fn_current_user_id()));

-- Le rôle de reporting lit toutes les lignes, comme pour demande et paiement.
CREATE POLICY piece_jointe_lecture ON piece_jointe FOR SELECT TO emine_readonly USING (true);
