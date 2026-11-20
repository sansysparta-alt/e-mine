-- e-mine · amorçage 2/3 — base, extensions et schéma (super-utilisateur, hors Flyway)
-- La base « emine » est créée par l'image PostgreSQL (POSTGRES_DB) ; on lui donne son propriétaire.

ALTER DATABASE emine OWNER TO emine_owner;

CREATE EXTENSION IF NOT EXISTS citext;     -- e-mail insensible à la casse
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- recherche approchée (raison sociale)
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- fonctions cryptographiques (seed)

CREATE SCHEMA IF NOT EXISTS emine AUTHORIZATION emine_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

ALTER ROLE emine_owner    SET search_path = emine, public;
ALTER ROLE emine_app      SET search_path = emine, public;
ALTER ROLE emine_readonly SET search_path = emine, public;
ALTER ROLE emine_auditeur SET search_path = emine, public;
ALTER ROLE emine_public   SET search_path = emine, public;
