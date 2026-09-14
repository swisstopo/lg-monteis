-- -----------------------------------------------------------------------------
-- Bound sensor_reading.sensor_id, which has been unconstrained TEXT since
-- V1__init.sql. Matches the width used for the meta DB's FDW mapping of this
-- same column (raw_sensor_reading.sensor_id, see
-- db/meta/schema/V11__set_text_column_lengths.sql) so the two stay in lockstep.
--
-- Safe as a plain ALTER COLUMN ... TYPE: no current data (including the dev
-- seed) exceeds this limit, so no USING/truncation is needed.
-- -----------------------------------------------------------------------------

ALTER TABLE sensor_reading ALTER COLUMN sensor_id TYPE VARCHAR(500);
