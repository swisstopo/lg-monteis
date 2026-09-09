-- 1. Create the sensor_parameter table
-- Within a single sensor, its parameters (readings) must be distinguishable by their DAS-side
-- alias - a sensor can't have two parameters both claiming the same das_parameter_alias. Not
-- globally unique: different sensors are free to reuse the same parameter alias.
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
    comment TEXT,
    version     INTEGER          NOT NULL DEFAULT 1,
    CONSTRAINT sensor_parameter_sensor_id_das_parameter_alias_key
        UNIQUE (sensor_id, das_parameter_alias)
);

-- 2. Create index on the new table
CREATE INDEX idx_sensor_parameter_formula_id ON sensor_parameter (formula_id);

-- 3. Drop the old constraints and indexes from the sensors table
ALTER TABLE sensors DROP CONSTRAINT IF EXISTS sensors_type_id_fkey;
ALTER TABLE sensors DROP CONSTRAINT IF EXISTS sensors_formula_id_fkey;
DROP INDEX IF EXISTS idx_sensors_formula_id;

-- 4. Modify the sensors table: add new columns, drop extracted columns, and restore the
-- uniqueness the old `code` column had (V2's `code TEXT UNIQUE NOT NULL`) on its replacement -
-- a sensor is identified on the external DAS supplier's side by the combination of which DAS
-- system it comes from and its alias on that system (das_sensor_alias) - e.g. two different
-- SolExperts devices can't share the same alias, but a SolExperts device and some other DAS
-- system's device could coincidentally use the same alias string.
--
-- `das` is NOT NULL DEFAULT (rather than nullable): there is currently exactly one legal value,
-- so defaulting existing/seeded rows to it is safe and avoids having to touch the checked-in seed
-- data or backfill anything by hand. The DEFAULT is kept permanently (not dropped after backfill)
-- as a pragmatic safety net for any future direct SQL inserts that don't specify it; the API layer
-- still requires an explicit value via WriteSensorDto's @NotNull.
--
-- das_sensor_alias stays nullable, so NULLs remain unrestricted by the new unique constraint
-- (Postgres treats them as distinct for UNIQUE purposes).
ALTER TABLE sensors
    ADD COLUMN fulcrum_id UUID,
    ADD COLUMN das_sensor_alias TEXT,
    ADD COLUMN main_experiment UUID,
    ADD COLUMN das TEXT NOT NULL DEFAULT 'SOL_EXPERTS',
    ADD CONSTRAINT sensors_das_das_sensor_alias_key UNIQUE (das, das_sensor_alias),
    DROP COLUMN code CASCADE,
    DROP COLUMN type_id CASCADE,
    DROP COLUMN unit CASCADE,
    DROP COLUMN lower_alarm_limit CASCADE,
    DROP COLUMN upper_alarm_limit CASCADE,
    DROP COLUMN formula_id CASCADE;

CREATE VIEW sensor_reading_secured WITH (security_invoker = true) AS
SELECT r.*
FROM raw_sensor_reading r
         JOIN sensor_parameter s ON s.das_parameter_alias = r.sensor_id;

GRANT SELECT ON sensor_reading_secured TO core_app;