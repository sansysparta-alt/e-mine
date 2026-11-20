-- e-mine · V3 — comptes gérés par Keycloak (décision D8)
-- Le lien se fait par l'identifiant Keycloak (claim « sub » du jeton OIDC).
-- Mot de passe, échecs de connexion et verrouillage sont gérés par Keycloak :
-- mot_de_passe_hash devient facultatif et n'est plus utilisé.

ALTER TABLE utilisateur ADD COLUMN keycloak_id uuid UNIQUE;
ALTER TABLE utilisateur ALTER COLUMN mot_de_passe_hash DROP NOT NULL;

COMMENT ON COLUMN utilisateur.keycloak_id IS 'Identifiant du compte Keycloak (claim sub du jeton OIDC)';
COMMENT ON COLUMN utilisateur.mot_de_passe_hash IS 'Obsolète depuis V3 (D8) : mots de passe gérés par Keycloak';
COMMENT ON COLUMN utilisateur.echecs_connexion IS 'Obsolète depuis V3 (D8) : verrouillage géré par Keycloak';
COMMENT ON COLUMN utilisateur.verrouille_jusqua IS 'Obsolète depuis V3 (D8) : verrouillage géré par Keycloak';
