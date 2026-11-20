#!/bin/sh
# e-mine · amorçage 3/3 — connexion des rôles applicatifs
# Les mots de passe viennent des variables d'environnement du conteneur (fichier .env, jamais versionné).
#   emine_owner : propriétaire du schéma, utilisé par Flyway pour les migrations
#   emine_app   : connexion de l'API (soumise à la RLS)
set -eu

: "${EMINE_OWNER_PASSWORD:?EMINE_OWNER_PASSWORD manquant (voir .env.example)}"
: "${EMINE_APP_PASSWORD:?EMINE_APP_PASSWORD manquant (voir .env.example)}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${POSTGRES_DB:-emine}" \
  -v owner_pw="$EMINE_OWNER_PASSWORD" -v app_pw="$EMINE_APP_PASSWORD" <<'SQL'
ALTER ROLE emine_owner LOGIN PASSWORD :'owner_pw';
ALTER ROLE emine_app   LOGIN PASSWORD :'app_pw';
SQL
