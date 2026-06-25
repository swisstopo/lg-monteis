CREATE EXTENSION IF NOT EXISTS postgres_fdw;

-- Corresponds to ${META_FLYWAY_USER} and ${META_FLYWAY_PWD}
CREATE USER core_flyway WITH ENCRYPTED PASSWORD 'pwd_core_flyway';
CREATE SCHEMA IF NOT EXISTS public;

-- Allow core flyway to establish connections via fdw
GRANT USAGE ON FOREIGN DATA WRAPPER postgres_fdw TO core_flyway;
-- Allow core flyway to alter the schema
GRANT CREATE ON SCHEMA public TO core_flyway;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO core_flyway;
GRANT USAGE, CREATE ON SCHEMA public TO core_flyway;

-- Corresponds to ${CORE_APP_META_DB_USER} and ${CORE_APP_META_DB_PWD}
CREATE USER core_app WITH ENCRYPTED PASSWORD 'pwd_core_app';
GRANT USAGE ON SCHEMA public TO core_app;

-- Allow core app user to access future tables (CRUD)
ALTER DEFAULT PRIVILEGES FOR ROLE core_flyway IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO core_app;

ALTER DEFAULT PRIVILEGES FOR ROLE core_flyway IN SCHEMA public
GRANT USAGE, SELECT ON SEQUENCES TO core_app;

-- Setup a connection to the foreign database (timescale db)
CREATE SERVER timescale_server
FOREIGN DATA WRAPPER postgres_fdw
OPTIONS (host 'ts_db', port '5432', dbname 'monteis-tsdb');
-- Map the current user (core_app) to the user of timescale, only read permission on specific tables
-- Corresponds to ${FDW_READ_USER} and ${FDW_READ_PWD}
CREATE USER MAPPING IF NOT EXISTS FOR core_app
SERVER timescale_server
OPTIONS (user 'fdw_user', password 'pwd_fdw_user');

-- Allow the flyway user to link foreign tables through the fdw tunnel in migrations.
GRANT USAGE ON FOREIGN SERVER timescale_server TO core_flyway;
