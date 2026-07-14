-- Initial schema for PostgresDB (owned by CoreAPI)

CREATE TABLE formulas
(
    id         BIGSERIAL PRIMARY KEY,
    expression TEXT UNIQUE NOT NULL,
    version    INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE sensors
(
    id          BIGSERIAL PRIMARY KEY,
    code        TEXT UNIQUE      NOT NULL,
    name        TEXT             NOT NULL,
    upper_bound DOUBLE PRECISION NOT NULL,
    lower_bound DOUBLE PRECISION NOT NULL,
    formula_id     BIGINT NOT NULL REFERENCES formulas (id) ON DELETE RESTRICT,
    version     INTEGER          NOT NULL DEFAULT 1
);

CREATE INDEX idx_sensors_formula_id ON sensors (formula_id);

-- Foreign table mapping CoreAPI's view of TimescaleDB's sensor_reading
-- Lives in PostgresDB's migrations (FDW mapping DDL belongs with the consumer, not the producer)

CREATE FOREIGN TABLE IF NOT EXISTS raw_sensor_reading (
    "timestamp" TIMESTAMPTZ NOT NULL,
    sensor_id TEXT NOT NULL,
    raw_value DOUBLE PRECISION NOT NULL,
    norm_value DOUBLE PRECISION,
    version SMALLINT,
    status TEXT
    )
    SERVER timescale_server
    OPTIONS (schema_name 'public', table_name 'sensor_reading');


-- Javers
CREATE SEQUENCE jv_commit_pk_seq CACHE 100;
CREATE SEQUENCE jv_global_id_pk_seq CACHE 100;
CREATE SEQUENCE jv_snapshot_pk_seq CACHE 100;

CREATE TABLE jv_global_id
(
    global_id_pk BIGINT NOT NULL,
    local_id     VARCHAR(191),
    fragment     VARCHAR(200),
    type_name    VARCHAR(200),
    owner_id_fk  BIGINT,
    CONSTRAINT jv_global_id_pk PRIMARY KEY (global_id_pk),
    CONSTRAINT jv_global_id_owner_id_fk FOREIGN KEY (owner_id_fk) REFERENCES jv_global_id (global_id_pk)
);

CREATE TABLE jv_commit
(
    commit_pk           BIGINT NOT NULL,
    author              VARCHAR(200),
    commit_date         TIMESTAMP,
    commit_date_instant VARCHAR(30),
    commit_id           NUMERIC(22, 2),
    CONSTRAINT jv_commit_pk PRIMARY KEY (commit_pk)
);

CREATE TABLE jv_commit_property
(
    commit_fk      BIGINT       NOT NULL,
    property_name  VARCHAR(191) NOT NULL,
    property_value VARCHAR(600),
    CONSTRAINT jv_commit_property_pk PRIMARY KEY (commit_fk, property_name),
    CONSTRAINT jv_commit_property_commit_fk FOREIGN KEY (commit_fk) REFERENCES jv_commit (commit_pk)
);

CREATE TABLE jv_snapshot
(
    snapshot_pk        BIGINT NOT NULL,
    type               VARCHAR(200),
    version            BIGINT,
    state              TEXT,
    changed_properties VARCHAR(2000),
    managed_type       VARCHAR(200),
    commit_fk          BIGINT,
    global_id_fk       BIGINT,
    CONSTRAINT jv_snapshot_pk PRIMARY KEY (snapshot_pk),
    CONSTRAINT jv_snapshot_commit_fk FOREIGN KEY (commit_fk) REFERENCES jv_commit (commit_pk),
    CONSTRAINT jv_snapshot_global_id_fk FOREIGN KEY (global_id_fk) REFERENCES jv_global_id (global_id_pk)
);