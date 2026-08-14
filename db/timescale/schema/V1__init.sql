-- 1. Enum für Range-Status
-- Bleibt speichereffizient (4 Bytes) und hochgradig komprimierbar
CREATE TYPE range_category AS ENUM ('too_low', 'correct', 'too_high');

-- 2. Base Table erstellen (Raw Data)
-- Hinweis: sensor_id ist sensor_code in der Metadaten DB
CREATE TABLE IF NOT EXISTS sensor_reading (
                                              timestamp   TIMESTAMPTZ NOT NULL,
                                              sensor_id   TEXT NOT NULL,
                                              raw_value   DOUBLE PRECISION NOT NULL,
                                              norm_value  DOUBLE PRECISION,
                                              version     SMALLINT DEFAULT 0,
                                              status      range_category,

    -- Composite Primary Key: Erzwingt Eindeutigkeit pro Sensor & Zeitstempel
                                              PRIMARY KEY (timestamp, sensor_id)
    );

-- Serves every per-sensor range scan, which is the shape both read paths use:
--   WHERE sensor_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp
-- The ordering comes straight off this index, so postgres_fdw can push the ORDER BY
-- down and no sort is needed on either side.
--
-- Do NOT add a plain (sensor_id) index on top of this. It is fully covered here, and
-- when it existed the planner preferred a BitmapOr over it and then had to sort:
--   with it:    Bitmap Index Scan -> Sort (quicksort 3166kB)
--   without it: ChunkAppend -> per-chunk Index Scan, no sort
CREATE INDEX sensor_reading_sensor_time_idx
    ON sensor_reading (sensor_id, timestamp DESC);

GRANT SELECT ON TABLE sensor_reading TO "${fdw_read_user}";

-- 3. In eine Hypertable konvertieren (Partitionierung nach 'timestamp')
--
-- create_default_indexes => FALSE: the default would add sensor_reading_timestamp_idx
-- on (timestamp DESC), which is redundant here because the primary key already leads
-- with timestamp and PostgreSQL scans it backwards for DESC ordering. Measured with and
-- without on the 525k-row dev seed, the time-ordered read (ORDER BY timestamp DESC
-- LIMIT 100) is identical at ~1.9 ms, so the extra index only costs ~7 MB and one more
-- B-tree insert per row — which matters for an ingest workload.
SELECT create_hypertable('sensor_reading', 'timestamp', create_default_indexes => FALSE);
