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
                      now() - interval '365 days',
                      now(),
                      interval '5 minutes'
              ) AS gs(ts)
                  CROSS JOIN (VALUES
                                  ('TEMP-1', 0.0),
                                  ('PRESS-1&2', 2.1),
                                  ('FLOW-2', 4.2),
                                  ('DISP-2', 3.7),
                                  ('FLOW-Admin', 5.8)
         ) AS s(sensor_id, phase_shift)
     ) AS data;

-- Bulk INSERT leaves the hypertable with no statistics, and autovacuum may not
-- reach it for a long time. Without stats the meta DB's postgres_fdw planner
-- (use_remote_estimate = true) costs every remote path from garbage, which is
-- how a single-sensor range scan ends up picking a bitmap scan plus a sort.
-- ANALYZE on the hypertable propagates to all chunks.
ANALYZE sensor_reading;