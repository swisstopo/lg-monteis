-- Local/dev only seed data — NOT run in production
-- Repeatable migration: re-applies whenever this file's checksum changes.
-- Delete-then-insert so the script is the single source of truth —
-- edits and removals here are reflected on the next run, not just additions.
--
-- IDs are fixed literal UUIDs (not generated via uuidv7()) so the seed stays
-- reproducible across runs and so docker/keycloak/realm/patch.local.json's
-- test-user group attributes (experiment_ids) can reference the same fixed
-- experiment IDs. Bulk-generated sensors below are the exception: nothing
-- else references their IDs, so they get real uuidv7() values.

TRUNCATE TABLE experiment_sensor, experiments, sensors, sensor_types, formulas CASCADE;

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

-- 3. Insert corresponding sample sensors
-- Naming convention: <TYPE>-<experiment membership>, so RLS visibility is obvious from the code
-- alone — e.g. PRESS-1&2 is visible to users in experiment 1 OR 2, FLOW-Admin belongs to no
-- experiment and is only ever visible to admins.
INSERT INTO sensors (
    id, code, name, type_id, unit, comment,
    x, y, z,
    upper_alarm_limit, lower_alarm_limit, active, formula_id, version
)
VALUES
    ('00000000-0000-7000-8000-000000000201', 'TEMP-1', 'monteis-001',
     '00000000-0000-7000-8000-000000000101', 'KELVIN', 'Air temperature sensor near ventilation intake',
     100, 200, 300, 100.0, -50.0, true, '00000000-0000-7000-8000-000000000001', 1),
    ('00000000-0000-7000-8000-000000000202', 'PRESS-1&2', 'monteis-002',
     '00000000-0000-7000-8000-000000000102', 'KILOGRAM', 'Radial stress/pressure sensor',
     110, 210, 310, 5000.0, 0.0, true, '00000000-0000-7000-8000-000000000002', 1),
    ('00000000-0000-7000-8000-000000000203', 'DISP-2', 'monteis-003',
     '00000000-0000-7000-8000-000000000103', 'METER', 'Displacement monitoring sensor',
     120, 220, 320, 50.0, -50.0, true, '00000000-0000-7000-8000-000000000003', 1),
    ('00000000-0000-7000-8000-000000000204', 'FLOW-2', 'monteis-004',
     '00000000-0000-7000-8000-000000000104', 'SECONDS', 'Flow/volume monitoring sensor',
     130, 230, 330, 1500.0, 0.0, true, '00000000-0000-7000-8000-000000000004', 1),
    ('00000000-0000-7000-8000-000000000205', 'FLOW-Admin', 'ADMIN',
     '00000000-0000-7000-8000-000000000103', 'METER', 'Admin-only flow sensor',
     140, 240, 340, 1500.0, 0.0, true, '00000000-0000-7000-8000-000000000004', 1);

-- 4. Insert Experiments
-- Fixed IDs matching docker/keycloak/realm/patch.local.json's "Experiment Alpha"/"Beta" groups.
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

-- Note: patch.local.json's "Experiment Gamma" group grants
-- experiment_ids = ['00000000-0000-7000-8000-000000000303'], which intentionally
-- matches no seeded experiment here (mirrors the previous integer-id seed, which
-- likewise never inserted an experiment with id 3).

-- 5. Link Sensors to Experiments (Many-to-Many)
INSERT INTO experiment_sensor (experiment_id, sensor_id)
VALUES
    -- Experiment 1 (Alpha) contains: TEMP-1 and PRESS-1&2
    ('00000000-0000-7000-8000-000000000301', '00000000-0000-7000-8000-000000000201'),
    ('00000000-0000-7000-8000-000000000301', '00000000-0000-7000-8000-000000000202'),

    -- Experiment 2 (Beta) contains: PRESS-1&2, DISP-2, and FLOW-2
    -- (Notice PRESS-1&2 is shared between both experiments)
    ('00000000-0000-7000-8000-000000000302', '00000000-0000-7000-8000-000000000202'),
    ('00000000-0000-7000-8000-000000000302', '00000000-0000-7000-8000-000000000203'),
    ('00000000-0000-7000-8000-000000000302', '00000000-0000-7000-8000-000000000204');

-- 6. Bulk load-testing experiments. Unlike the
-- fixed sensors above, these IDs aren't referenced anywhere else, so they use
-- real generated uuidv7() values.
-- CHANGE THE NUMBER 6 BELOW TO GENERATE MORE OR FEWER EXPERIMENTS

INSERT INTO experiments (
    "name", "comment",
    "version", "owner",
    "start",
    "end"
)
SELECT
    'bulk-experiment-' || i,                               -- name
    'Auto-generated load testing experiment ' || i,   -- comment
    1,                                                -- version
    'User' || ((i - 1) % 5 + 1),                      -- owner
    -- start
    CURRENT_DATE + (
                       CASE
                           WHEN i % 3 = 0 THEN -90                   -- past
                           WHEN i % 3 = 1 THEN -15                   -- started before today
                           ELSE 30                                   -- future
                           END
                       ) * INTERVAL '1 day',

    -- end
    CURRENT_DATE + (
                       CASE
                           WHEN i % 3 = 0 THEN -30                   -- ended in the past
                           WHEN i % 3 = 1 THEN 30                    -- ends in the future
                           ELSE 90                                   -- future
                           END
                       ) * INTERVAL '1 day'
FROM generate_series(1, 6) AS i;

-- FLOW-Admin (Sensor ID 5) is intentionally linked to no experiment —
-- only admins can see it.

-- 7. Bulk load-testing sensors, all linked to Experiment 1 (Alpha). Unlike the
-- fixed sensors above, these IDs aren't referenced anywhere else, so they use
-- real generated uuidv7() values.
-- CHANGE THE NUMBER 10 BELOW TO GENERATE MORE OR FEWER SENSORS
WITH bulk_sensors AS (
    INSERT INTO sensors (
        code, name, type_id, unit, comment,
        x, y, z,
        upper_alarm_limit, lower_alarm_limit, active, formula_id, version
    )
    SELECT
        'BULK-' || i,                                     -- code (e.g. BULK-1)
        'bulk-sensor-' || i,                               -- name
        '00000000-0000-7000-8000-000000000101',            -- type
        'METER',                                           -- unit
        'Auto-generated load testing sensor ' || i,        -- comment
        random() * 100,                                    -- random x_local
        random() * 100,                                    -- random y_local
        random() * 100,                                    -- random z_local
        100.0,                                              -- upper_alarm_bound
        -50.0,                                              -- lower_alarm_bound
        true,                                               -- active
        '00000000-0000-7000-8000-000000000004',             -- formula_id (passthrough)
        1                                                   -- version
    FROM generate_series(1, 10) AS i
    RETURNING id
)
INSERT INTO experiment_sensor (experiment_id, sensor_id)
SELECT '00000000-0000-7000-8000-000000000301', id
FROM bulk_sensors;
