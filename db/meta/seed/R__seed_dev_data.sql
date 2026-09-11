-- Local/dev only seed data — NOT run in production
-- Repeatable migration: re-applies whenever this file's checksum changes.
-- Delete-then-insert so the script is the single source of truth.

-- 0. Truncate tables (added sensor_parameter to the list)
TRUNCATE TABLE experiment_sensor, experiments, sensor_parameter, sensors, sensor_types, formulas CASCADE;

-- 1. Insert formulas (Parsington-compatible expressions using 'x')
INSERT INTO formulas (id, expression, version)
VALUES
    -- TEMP-1: Example conversion (e.g., Celsius to Fahrenheit)
    ('00000000-0000-7000-8000-000000000001', 'x * 1.8 + 32', 1),
    -- PRESS-1&2: Example scaling (e.g., kPa to Pa)
    ('00000000-0000-7000-8000-000000000002', 'x * 1000', 1),
    -- DISP-2: Example precision adjustment (e.g., mm to meters)
    ('00000000-0000-7000-8000-000000000003', 'x / 1000', 1),
    -- FLOW-2 / FLOW-Admin: 1:1 passthrough (no modification to the raw value)
    ('00000000-0000-7000-8000-000000000004', 'x', 1);


-- 2. Insert sensor types
INSERT INTO sensor_types (id, name, version)
VALUES
    ('00000000-0000-7000-8000-000000000101', 'Temperature', 1),
    ('00000000-0000-7000-8000-000000000102', 'Stress Radial', 1),
    ('00000000-0000-7000-8000-000000000103', 'Other', 1),
    ('00000000-0000-7000-8000-000000000104', 'Volume', 1);


-- 3. Insert Experiments
-- MOVED UP: Must be inserted before sensors because sensors now have a main_experiment column
INSERT INTO experiments (
    "id", "name", "comment",
    "version", "owner",
    "start",
    "end"
)
VALUES
    ('00000000-0000-7000-8000-000000000301', 'Mont Terri Alpha', 'Initial temperature and pressure survey', 1, 'User1',
     DATE '2024-01-15', DATE '2024-06-30'),
    ('00000000-0000-7000-8000-000000000302', 'Mont Terri Beta', 'Deep borehole displacement and pressure monitoring', 1, 'User2',
     DATE '2024-07-01', DATE '2025-03-31');


-- 4. Insert corresponding sample sensors
-- Note: 'code' mapped to 'das_sensor_alias'. Measurement specs are removed from this insert.
INSERT INTO sensors (
    id, das_sensor_alias, name, comment,
    x, y, z,
    active, main_experiment, version
)
VALUES
    ('00000000-0000-7000-8000-000000000201', 'TEMP-1', 'monteis-001', 'Air temperature sensor near ventilation intake',
     100, 200, 300, true, '00000000-0000-7000-8000-000000000301', 1),

    ('00000000-0000-7000-8000-000000000202', 'PRESS-1&2', 'monteis-002', 'Radial stress/pressure sensor',
     110, 210, 310, true, '00000000-0000-7000-8000-000000000301', 1),

    ('00000000-0000-7000-8000-000000000203', 'DISP-2', 'monteis-003', 'Displacement monitoring sensor',
     120, 220, 320, true, '00000000-0000-7000-8000-000000000302', 1),

    ('00000000-0000-7000-8000-000000000204', 'FLOW-2', 'monteis-004', 'Flow/volume monitoring sensor',
     130, 230, 330, true, '00000000-0000-7000-8000-000000000302', 1),

    ('00000000-0000-7000-8000-000000000205', 'FLOW-Admin', 'ADMIN', 'Admin-only flow sensor',
     140, 240, 340, true, NULL, 1);


-- 5. Insert Sensor Parameters
-- This maps the old measurement limits and formulas to the new child table.
-- Explicit ids (following the same 0400-series convention as formulas/types/sensors/experiments
-- below) rather than the DEFAULT uuidv7() - MeasurementQueryRepositoryIT looks these up by fixed
-- id, the same way every other seeded entity in this file is referenced.
INSERT INTO sensor_parameter (
    id, sensor_id, name, das_parameter_alias, type_id, unit, formula_id,
    upper_alarm_limit, lower_alarm_limit, active, comment
)
VALUES
    -- Param for TEMP-1
    ('00000000-0000-7000-8000-000000000401', '00000000-0000-7000-8000-000000000201', 'Temperature Param', 'TEMP-1-P1', '00000000-0000-7000-8000-000000000101', 'KELVIN',
     '00000000-0000-7000-8000-000000000001', 100.0, -50.0, true, 'Primary temperature reading'),

    -- Param for PRESS-1&2
    ('00000000-0000-7000-8000-000000000402', '00000000-0000-7000-8000-000000000202', 'Pressure Param', 'PRESS-1&2-P1', '00000000-0000-7000-8000-000000000102', 'KILOGRAM',
     '00000000-0000-7000-8000-000000000002', 5000.0, 0.0, true, 'Primary pressure reading'),

    -- Param for DISP-2
    ('00000000-0000-7000-8000-000000000403', '00000000-0000-7000-8000-000000000203', 'Displacement Param', 'DISP-2-P1', '00000000-0000-7000-8000-000000000103', 'METER',
     '00000000-0000-7000-8000-000000000003', 50.0, -50.0, true, 'Primary displacement reading'),

    -- Param for FLOW-2
    ('00000000-0000-7000-8000-000000000404', '00000000-0000-7000-8000-000000000204', 'Flow Param', 'FLOW-2-P1', '00000000-0000-7000-8000-000000000104', 'SECONDS',
     '00000000-0000-7000-8000-000000000004', 1500.0, 0.0, true, 'Primary flow reading'),

    -- Param for ADMIN
    ('00000000-0000-7000-8000-000000000405', '00000000-0000-7000-8000-000000000205', 'Admin Flow Param', 'FLOW-Admin-P1', '00000000-0000-7000-8000-000000000103', 'METER',
     '00000000-0000-7000-8000-000000000004', 1500.0, 0.0, true, 'Admin flow parameter');


-- 6. Link Sensors to secondary Experiments (Many-to-Many)
INSERT INTO experiment_sensor (experiment_id, sensor_id)
VALUES
    -- Experiment 1 (Alpha) contains: TEMP-1 and PRESS-1&2
    ('00000000-0000-7000-8000-000000000301', '00000000-0000-7000-8000-000000000201'),
    ('00000000-0000-7000-8000-000000000301', '00000000-0000-7000-8000-000000000202'),

    -- Experiment 2 (Beta) contains: PRESS-1&2, DISP-2, and FLOW-2
    ('00000000-0000-7000-8000-000000000302', '00000000-0000-7000-8000-000000000202'),
    ('00000000-0000-7000-8000-000000000302', '00000000-0000-7000-8000-000000000203'),
    ('00000000-0000-7000-8000-000000000302', '00000000-0000-7000-8000-000000000204');


-- 7. Bulk load-testing sensors
-- Generates sensors, their corresponding parameters, and links them to Experiment 1
WITH bulk_sensors AS (
INSERT INTO sensors (
    das_sensor_alias, name, comment,
    x, y, z,
    active, main_experiment, version
)
SELECT
    'BULK-' || i,                                      -- das_sensor_alias
    'bulk-sensor-' || i,                               -- name
    'Auto-generated load testing sensor ' || i,        -- comment
    random() * 100,                                    -- random x_local
    random() * 100,                                    -- random y_local
    random() * 100,                                    -- random z_local
    true,                                              -- active
    '00000000-0000-7000-8000-000000000301',            -- main_experiment (Alpha)
    1                                                  -- version
FROM generate_series(1, 10) AS i
    RETURNING id, das_sensor_alias
),
bulk_parameters AS (
INSERT INTO sensor_parameter (
    sensor_id, name, das_parameter_alias, type_id, unit, formula_id,
    upper_alarm_limit, lower_alarm_limit, active, comment
)
SELECT
    id,
    'Bulk Param',
    'BULK-P1',
    '00000000-0000-7000-8000-000000000101',           -- type
    'METER',                                          -- unit
    '00000000-0000-7000-8000-000000000004',           -- formula_id (passthrough)
    100.0,                                            -- upper_alarm_bound
    -50.0,                                            -- lower_alarm_bound
    true,
    'Auto-generated parameter'
FROM bulk_sensors
    RETURNING id
    )
INSERT INTO experiment_sensor (experiment_id, sensor_id)
SELECT '00000000-0000-7000-8000-000000000301', id
FROM bulk_sensors;


-- Add a second parameter to BULK-1
INSERT INTO sensor_parameter (
    sensor_id,
    name,
    das_parameter_alias,
    type_id,
    unit,
    formula_id,
    upper_alarm_limit,
    lower_alarm_limit,
    active,
    comment
)
SELECT
    id,
    'Bulk Param 2',
    'BULK-P2',
    '00000000-0000-7000-8000-000000000101',
    'METER',
    '00000000-0000-7000-8000-000000000004',
    200.0,
    -100.0,
    true,
    'Second auto-generated parameter'
FROM sensors
WHERE das_sensor_alias = 'BULK-1';