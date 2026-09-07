-- 1. Create the sensor_parameter table
CREATE TABLE sensor_parameter
(
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    sensor_id UUID NOT NULL REFERENCES sensors (id) ON DELETE CASCADE,
    name TEXT,
    das_parameter_alias TEXT,
    type_id UUID NOT NULL REFERENCES sensor_types (id) ON DELETE RESTRICT,
    unit unit NOT NULL,
    formula_id UUID NOT NULL REFERENCES formulas (id) ON DELETE RESTRICT,
    lower_alarm_limit DOUBLE PRECISION NOT NULL,
    upper_alarm_limit DOUBLE PRECISION NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    comment TEXT
);

-- 2. Create index on the new table
CREATE INDEX idx_sensor_parameter_formula_id ON sensor_parameter (formula_id);

-- 3. Drop the old constraints and indexes from the sensors table
ALTER TABLE sensors DROP CONSTRAINT IF EXISTS sensors_type_id_fkey;
ALTER TABLE sensors DROP CONSTRAINT IF EXISTS sensors_formula_id_fkey;
DROP INDEX IF EXISTS idx_sensors_formula_id;

-- 4 Modify the sensors table: add new columns and drop extracted columns
ALTER TABLE sensors
    ADD COLUMN fulcrum_id UUID,
    ADD COLUMN das_sensor_alias TEXT,
    ADD COLUMN main_experiment UUID,
    DROP COLUMN code CASCADE,
    DROP COLUMN type_id CASCADE,
    DROP COLUMN unit CASCADE,
    DROP COLUMN lower_alarm_limit CASCADE,
    DROP COLUMN upper_alarm_limit CASCADE,
    DROP COLUMN formula_id CASCADE;