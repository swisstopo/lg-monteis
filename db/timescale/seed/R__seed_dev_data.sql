-- Local/dev only seed data — NOT run in production
-- Repeatable migration: re-applies whenever this file's checksum changes.
-- Delete-then-insert so the script is the single source of truth.

-- TRUNCATE works fine on hypertables
TRUNCATE TABLE sensor_reading;

-- sensor_parameter_id values below are the fixed ids assigned to the matching sensor_parameter
-- rows in db/meta/seed/R__seed_dev_data.sql (same das_parameter_alias) - TimescaleDB and the meta
-- DB are separate physical databases (that's the whole point of the FDW split), so this can't be
-- a join; the ids just have to be kept in sync by hand between the two seed scripts.
INSERT INTO sensor_reading (timestamp, das_key, sensor_parameter_id, raw_value, norm_value, version, status)
SELECT
    data.ts,
    data.das_key,
    data.sensor_parameter_id,
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
             s.das_key,
             s.sensor_parameter_id,
             round(((50 + 30 * sin(extract(epoch FROM gs.ts) / 3600.0 + s.phase_shift)))::numeric, 2) AS raw_value,
             round(((50 + 30 * sin(extract(epoch FROM gs.ts) / 3600.0 + s.phase_shift)) * 0.98)::numeric, 2) AS norm_value
         FROM generate_series(
                      now() - interval '365 days',
                      now(),
                      interval '5 minutes'
              ) AS gs(ts)
                  CROSS JOIN (VALUES
                                  ('TEMP-1-P1', 0.0, '00000000-0000-7000-8000-000000000401'::uuid),
                                  ('PRESS-1&2-P1', 2.1, '00000000-0000-7000-8000-000000000402'::uuid),
                                  ('FLOW-2-P1', 4.2, '00000000-0000-7000-8000-000000000404'::uuid),
                                  ('DISP-2-P1', 3.7, '00000000-0000-7000-8000-000000000403'::uuid),
                                  ('FLOW-Admin-P1', 5.8, '00000000-0000-7000-8000-000000000405'::uuid)
         ) AS s(das_key, phase_shift, sensor_parameter_id)
     ) AS data;

-- Bulk INSERT leaves the hypertable with no statistics, and autovacuum may not
-- reach it for a long time. Without stats the meta DB's postgres_fdw planner
-- (use_remote_estimate = true) costs every remote path from garbage, which is
-- how a single-sensor range scan ends up picking a bitmap scan plus a sort.
-- ANALYZE on the hypertable propagates to all chunks.
ANALYZE sensor_reading;