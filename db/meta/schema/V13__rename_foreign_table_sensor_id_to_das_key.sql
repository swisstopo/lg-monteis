-- Companion to db/timescale/schema/V3__rename_sensor_id_to_das_key.sql: postgres_fdw matches
-- foreign table columns to the remote table by name, so the local column must be renamed too.
ALTER FOREIGN TABLE raw_sensor_reading RENAME COLUMN sensor_id TO das_key;
