-- -----------------------------------------------------------------------------
-- Replace read-all and write-all with one all-experiments flag (MON-196)
--
-- Until now RLS read two separate privilege flags, app.read_all (V6) and
-- app.write_all (V16), plus the id lists app.user_experiment_ids and
-- app.user_write_experiment_ids. The application now derives every value from
-- AccessPolicy and sets exactly three transaction-local GUCs:
--   app.all_experiments       'true' = read and write every experiment
--   app.read_experiment_ids   comma-separated UUIDs the caller may read
--   app.write_experiment_ids  comma-separated UUIDs the caller may update
--                             (always a subset of the read ids)
-- The old settings are no longer read by any function, so setting them grants
-- nothing.
--
-- can_access_all_experiments() replaces can_read_all() and can_write_all(). The
-- remaining functions keep their signatures and are replaced in place, so the
-- existing policies (experiments_read, experiments_update, sensors_read,
-- sensor_parameter_read) and the sensor_reading_secured view pick up the new
-- logic without being recreated.
--
-- Consequence: the one flag also covers writes, so the system context
-- (SystemSecurityContext, which already had read-all) now passes the
-- experiments_update WITH CHECK as well. This is accepted (ADR-002): the
-- application layer still gates every write, and the system jobs only write
-- Javers snapshots, not experiment rows.
--
-- Unset or empty settings fail closed: no flag, empty id arrays. A null target
-- id never yields TRUE for a caller without the flag.
--
-- sensor_reading_secured stays without security_barrier so that its predicate
-- keeps being pushed down to the TimescaleDB foreign table (see V6).
-- -----------------------------------------------------------------------------

CREATE FUNCTION can_access_all_experiments() RETURNS boolean AS $$
    SELECT current_setting('app.all_experiments', true) = 'true'
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
    SELECT can_access_all_experiments() OR target_experiment_id = ANY (current_experiment_ids())
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_access_sensor(target_sensor_id uuid) RETURNS boolean AS $$
    SELECT can_access_all_experiments() OR EXISTS (
        SELECT 1 FROM experiment_sensor es
        WHERE es.sensor_id = target_sensor_id
          AND es.experiment_id = ANY (current_experiment_ids())
    )
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_write_experiment(target_experiment_id uuid) RETURNS boolean AS $$
    SELECT can_access_all_experiments() OR target_experiment_id = ANY (current_write_experiment_ids())
$$ LANGUAGE sql STABLE;

DROP FUNCTION can_read_all();
DROP FUNCTION can_write_all();
