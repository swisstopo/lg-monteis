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

GRANT SELECT ON TABLE sensor_reading TO fdw_user;

-- 3. In eine Hypertable konvertieren (Partitionierung nach 'timestamp')
SELECT create_hypertable('sensor_reading', 'timestamp');