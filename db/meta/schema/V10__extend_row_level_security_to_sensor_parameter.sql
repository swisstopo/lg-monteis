-- -----------------------------------------------------------------------------
-- V6's row-level security covers `sensors` and `experiments`. V9 split sensor-level
-- measurement config out into `sensor_parameter`, and repointed sensor_reading_secured's join
-- from `sensors` to `sensor_parameter` - but `sensor_parameter` itself was never given RLS.
--
-- That join is what used to enforce access control: sensor_reading_secured has
-- security_invoker = true, so it runs as the calling role, and querying `sensors` inside it
-- triggered the sensors_read policy (see V6), filtering the join results to only rows the
-- caller's experiments can see. With the join now targeting `sensor_parameter` instead - a
-- table with no RLS at all - that filtering silently stopped happening: any caller could read
-- any sensor's raw readings through the view regardless of experiment membership.
--
-- Fix: give `sensor_parameter` the same RLS shape as `sensors`. A sensor_parameter's
-- accessibility is entirely determined by its parent sensor, so reads reuse the existing
-- can_access_sensor(uuid) function unchanged, applied to sensor_parameter.sensor_id. Writes
-- remain unrestricted at the DB level, same reasoning as V6: all access goes through the shared
-- core_app role, and command authorization is enforced in the application layer.
-- -----------------------------------------------------------------------------
ALTER TABLE sensor_parameter ENABLE ROW LEVEL SECURITY;
CREATE POLICY sensor_parameter_read ON sensor_parameter FOR SELECT
    USING (can_access_sensor(sensor_parameter.sensor_id));
CREATE POLICY sensor_parameter_insert ON sensor_parameter FOR INSERT WITH CHECK (true);
CREATE POLICY sensor_parameter_update ON sensor_parameter FOR UPDATE USING (true) WITH CHECK (true);
CREATE POLICY sensor_parameter_delete ON sensor_parameter FOR DELETE USING (true);
