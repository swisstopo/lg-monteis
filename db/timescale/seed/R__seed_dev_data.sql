-- Local/dev only seed data — NOT run in production
-- Repeatable migration: re-applies whenever this file's checksum changes.
-- Delete-then-insert so the script is the single source of truth.

-- TRUNCATE works fine on hypertables
TRUNCATE TABLE sensor_reading;

INSERT INTO sensor_reading (timestamp, sensor_id, raw_value, norm_value, version, status)
SELECT
    data.ts,
    data.sensor_id,
    data.raw_value,
    data.norm_value,
    0,
    CASE
        WHEN data.norm_value < 20 THEN 'too_low'::range_category
        WHEN data.norm_value > 78 THEN 'too_high'::range_category
        ELSE 'correct'::range_category
        END
FROM (
         SELECT
             gs.ts,
             s.sensor_id,
             round(((50 + 30 * sin(extract(epoch FROM gs.ts) / 3600.0 + s.phase_shift)))::numeric, 2) AS raw_value,
             round(((50 + 30 * sin(extract(epoch FROM gs.ts) / 3600.0 + s.phase_shift)) * 0.98)::numeric, 2) AS norm_value
         FROM generate_series(
                      now() - interval '10 hours',
                      now(),
                      interval '5 minutes'
              ) AS gs(ts)
                  CROSS JOIN (VALUES
                                  ('TEMP-001', 0.0),
                                  ('PRESS-001', 2.1),
                                  ('FLOW-001', 4.2)
         ) AS s(sensor_id, phase_shift)
     ) AS data;