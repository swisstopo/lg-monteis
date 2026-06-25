-- Initial schema for PostgresDB (owned by CoreAPI)

CREATE TABLE customer (
                          id          BIGSERIAL PRIMARY KEY,
                          name        TEXT NOT NULL,
                          email       TEXT NOT NULL UNIQUE,
                          created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE device (
                        id           BIGSERIAL PRIMARY KEY,
                        customer_id  BIGINT NOT NULL REFERENCES customer(id) ON DELETE CASCADE,
                        external_id  TEXT NOT NULL UNIQUE,
                        created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_customer_id ON device(customer_id);

-- Foreign table mapping CoreAPI's view of TimescaleDB's sensor_reading
-- Lives in PostgresDB's migrations (FDW mapping DDL belongs with the consumer, not the producer)

CREATE FOREIGN TABLE IF NOT EXISTS raw_sensor_reading (
    "timestamp"  TIMESTAMPTZ       NOT NULL,
    sensor_id    TEXT              NOT NULL,
    raw_value    DOUBLE PRECISION  NOT NULL,
    norm_value   DOUBLE PRECISION,
    version      SMALLINT,
    status       TEXT
    )
    SERVER timescale_server
    OPTIONS (schema_name 'public', table_name 'sensor_reading');