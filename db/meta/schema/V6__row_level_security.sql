-- -----------------------------------------------------------------------------
-- Row-Level Security (MON-111)
--
-- Read authorization is enforced by PostgreSQL. For each transaction the
-- application propagates the caller's security context (access level and visible
-- experiment IDs) into transaction-local GUCs. The helper functions below expose
-- that context to RLS policies and security views.
-- -----------------------------------------------------------------------------

CREATE FUNCTION is_admin() RETURNS boolean AS $$
    SELECT current_setting('app.access_level', true) = 'ADMIN'
$$ LANGUAGE sql STABLE;

CREATE FUNCTION current_experiment_ids() RETURNS bigint[] AS $$
    SELECT COALESCE(
        string_to_array(current_setting('app.user_experiment_ids', true), ',')::bigint[],
        ARRAY[]::bigint[]
    )
$$ LANGUAGE sql STABLE;

-- Shared access predicates reused by RLS policies and security views.
CREATE FUNCTION can_access_experiment(target_experiment_id bigint) RETURNS boolean AS $$
    SELECT is_admin() OR target_experiment_id = ANY (current_experiment_ids())
$$ LANGUAGE sql STABLE;

CREATE FUNCTION can_access_sensor(target_sensor_id bigint) RETURNS boolean AS $$
    SELECT is_admin() OR EXISTS (
        SELECT 1 FROM experiment_sensor es
        WHERE es.sensor_id = target_sensor_id
          AND es.experiment_id = ANY (current_experiment_ids())
    )
$$ LANGUAGE sql STABLE;

-- -----------------------------------------------------------------------------
-- Native RLS for local tables.
--
-- Reads are filtered by experiment membership. Writes remain unrestricted
-- because all database access uses the shared application role (core_app);
-- command authorization is enforced in the application layer.
-- -----------------------------------------------------------------------------
ALTER TABLE sensors ENABLE ROW LEVEL SECURITY;
CREATE POLICY sensors_read ON sensors FOR SELECT
    USING (can_access_sensor(sensors.id));
CREATE POLICY sensors_insert ON sensors FOR INSERT WITH CHECK (true);
CREATE POLICY sensors_update ON sensors FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY sensors_delete ON sensors FOR DELETE USING (true);

ALTER TABLE experiments ENABLE ROW LEVEL SECURITY;
-- Experiments are checked directly by ID so experiments without sensors remain
-- visible to authorized users.
CREATE POLICY experiments_read ON experiments FOR SELECT
    USING (can_access_experiment(experiments.id));
CREATE POLICY experiments_insert ON experiments FOR INSERT WITH CHECK (true);
CREATE POLICY experiments_update ON experiments FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY experiments_delete ON experiments FOR DELETE USING (true);

-- -----------------------------------------------------------------------------
-- raw_sensor_reading is a FOREIGN TABLE and therefore cannot use native RLS.
-- A security-barrier view provides equivalent filtering. The application must
-- query this view instead of the foreign table.
-- -----------------------------------------------------------------------------
CREATE VIEW sensor_reading_secured WITH (security_barrier = true, security_invoker = true) AS
SELECT r.*
FROM raw_sensor_reading r
WHERE r.sensor_id = ANY (
    SELECT s.code FROM sensors s
    WHERE can_access_sensor(s.id)
);

GRANT SELECT ON sensor_reading_secured TO core_app;
