-- e-mine · amorçage 1/3 — rôles (super-utilisateur, hors Flyway)
-- Exécuté automatiquement par l'image PostgreSQL à la création du volume (docker-entrypoint-initdb.d),
-- et par les tests Testcontainers. Idempotent. Les mots de passe sont posés par 03-mots-de-passe.sh.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'emine_owner')    THEN CREATE ROLE emine_owner NOLOGIN; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'emine_app')      THEN CREATE ROLE emine_app NOLOGIN; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'emine_readonly') THEN CREATE ROLE emine_readonly NOLOGIN; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'emine_auditeur') THEN CREATE ROLE emine_auditeur NOLOGIN; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'emine_public')   THEN CREATE ROLE emine_public NOLOGIN; END IF;
END $$;
