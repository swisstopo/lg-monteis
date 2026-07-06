-- Local/dev only seed data — NOT run in production
-- Repeatable migration: re-applies whenever this file's checksum changes.
-- Delete-then-insert so the script is the single source of truth —
-- edits and removals here are reflected on the next run, not just additions.

TRUNCATE TABLE sensors, formulas RESTART IDENTITY CASCADE;

-- 1. Insert sample sensors
INSERT INTO sensors (id, code, upper_bound, lower_bound, version)
VALUES (1, 'TEMP-001', 100.0, -50.0, 1),
       (2, 'PRESS-001', 5000.0, 0.0, 1),
       (3, 'DISP-001', 50.0, -50.0, 1),
       (4, 'FLOW-001', 1500.0, 0.0, 1);

-- 2. Insert corresponding formulas (Parsington-compatible expressions using 'x')
INSERT INTO formulas (id, sensor_id, expression, version)
VALUES
    -- TEMP-001: Example conversion (e.g., Celsius to Fahrenheit)
    (1, 1, 'x * 1.8 + 32', 1),

    -- PRESS-001: Example scaling (e.g., kPa to Pa)
    (2, 2, 'x * 1000', 1),

    -- DISP-001: Example precision adjustment (e.g., mm to meters)
    (3, 3, 'x / 1000', 1),

    -- FLOW-001: 1:1 passthrough (no modification to the raw value)
    (4, 4, 'x', 1);

-- 3. Sync the sequences with the explicitly inserted IDs
-- This ensures that the next time you omit the ID (e.g., during application usage),
-- PostgreSQL knows to start generating IDs from 5.
SELECT setval('sensors_id_seq', (SELECT MAX(id) FROM sensors));
SELECT setval('formulas_id_seq', (SELECT MAX(id) FROM formulas));