-- -----------------------------------------------------------------------------
-- Rename the RLS session settings to the AccessPolicy vocabulary (MON-196)
--
-- Until now RLS read the privilege flags app.read_all (V6) and app.write_all
-- (V16) plus the id lists app.user_experiment_ids and
-- app.user_write_experiment_ids. The application now derives every value from
-- AccessPolicy and sets four transaction-local GUCs:
--   app.read_all_experiments   'true' = read every experiment
--   app.write_all_experiments  'true' = update every experiment
--   app.read_experiment_ids    comma-separated UUIDs the caller may read
--   app.write_experiment_ids   comma-separated UUIDs the caller may update
--                              (always a subset of the read ids)
-- The old settings are no longer read by any function, so setting them grants
-- nothing.
--
-- Read and write stay two flags although every role grants both together:
-- the system context (SystemSecurityContext, background jobs without an HTTP
-- request) reads every experiment but must never update one, and for it RLS is
-- the only guard, as no filter chain runs.
--
-- can_read_all_experiments() and can_write_all_experiments() replace
-- can_read_all() and can_write_all(). The remaining functions keep their
-- signatures and are replaced in place, so the existing policies
-- (experiments_read, experiments_update, sensors_read, sensor_parameter_read)
-- and the sensor_reading_secured view pick up the new logic without being
-- recreated.
--
-- Unset or empty settings fail closed: no flag, empty id arrays. A null target
-- id never yields TRUE for a caller without the matching flag.
--
-- sensor_reading_secured stays without security_barrier so that its predicate
-- keeps being pushed down to the TimescaleDB foreign table (see V6).
-- -----------------------------------------------------------------------------

CREATE FUNCTION can_read_all_experiments() RETURNS boolean AS $$
    SELECT current_setting('app.read_all_experiments', true) = 'true'
$$ LANGUAGE sql STABLE;

CREATE FUNCTION can_write_all_experiments() RETURNS boolean AS $$
    SELECT current_setting('app.write_all_experiments', true) = 'true'
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION current_experiment_ids() RETURNS uuid[] AS $$
    SELECT COALESCE(
        string_to_array(NULLIF(current_setting('app.read_experiment_ids', true), ''), ',')::uuid[],
        ARRAY[]::uuid[]
    )
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION current_write_experiment_ids() RETURNS uuid[] AS $$
    SELECT COALESCE(
        string_to_array(NULLIF(current_setting('app.write_experiment_ids', true), ''), ',')::uuid[],
        ARRAY[]::uuid[]
    )
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_access_experiment(target_experiment_id uuid) RETURNS boolean AS $$
    SELECT can_read_all_experiments() OR target_experiment_id = ANY (current_experiment_ids())
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_access_sensor(target_sensor_id uuid) RETURNS boolean AS $$
    SELECT can_read_all_experiments() OR EXISTS (
        SELECT 1 FROM experiment_sensor es
        WHERE es.sensor_id = target_sensor_id
          AND es.experiment_id = ANY (current_experiment_ids())
    )
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_write_experiment(target_experiment_id uuid) RETURNS boolean AS $$
    SELECT can_write_all_experiments() OR target_experiment_id = ANY (current_write_experiment_ids())
$$ LANGUAGE sql STABLE;

DROP FUNCTION can_read_all();
DROP FUNCTION can_write_all();
