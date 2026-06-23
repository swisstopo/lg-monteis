CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Corresponds to ${TS_FLYWAY_USER} and ${TS_FLYWAY_PWD}
CREATE USER ts_flyway WITH ENCRYPTED PASSWORD 'pwd_ts_flyway';
CREATE SCHEMA IF NOT EXISTS public;

-- Flyway user needs permission to alter schema
GRANT CREATE ON SCHEMA public TO ts_flyway;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ts_flyway;
GRANT USAGE, CREATE ON SCHEMA public TO ts_flyway;

-- Pipeline user needs grants to write to timescale db
-- Corresponds to ${PIPELINE_APP_TS_DB_USER} and ${PIPELINE_APP_TS_DB_PWD}
CREATE USER pipeline_app WITH ENCRYPTED PASSWORD 'pwd_pipeline_app';
GRANT USAGE ON SCHEMA public TO pipeline_app;

-- Pipeline user needs grants to write to future tables (otherwise each migration needs to include a permission change manually)
ALTER DEFAULT PRIVILEGES FOR ROLE ts_flyway IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO pipeline_app;
ALTER DEFAULT PRIVILEGES FOR ROLE ts_flyway IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO pipeline_app;

-- The fdw_user is a user such that we can actually read the values from the foreign table, he only has read on specific tables
-- The fdw_user must not have default access to all tables we keep its privileges minimal (read only on wanted tables, included in schema migration)
-- Corresponds to ${FDW_READ_USER} and ${FDW_READ_PWD}
CREATE USER fdw_user WITH ENCRYPTED PASSWORD 'pwd_fdw_user';