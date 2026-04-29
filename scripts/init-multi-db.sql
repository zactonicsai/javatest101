-- =============================================================================
-- Postgres init: create additional databases beyond the default POSTGRES_DB.
--
-- Postgres' docker entrypoint runs *.sql files via psql in the order they
-- appear (alphabetical). This file runs ONCE on a fresh data volume.
--
-- If you previously brought the stack up with a broken init, run:
--   docker compose down -v
-- to wipe the volume so this file gets re-applied.
-- =============================================================================

CREATE DATABASE temporal;
GRANT ALL PRIVILEGES ON DATABASE temporal TO appuser;

CREATE DATABASE keycloak;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO appuser;
