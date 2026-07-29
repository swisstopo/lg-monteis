CREATE TYPE unit AS ENUM ('SECONDS', 'METER', 'KILOGRAM', 'AMPERE', 'KELVIN', 'MOLE', 'CANDELA');

CREATE TABLE sensors
(
    id          BIGSERIAL PRIMARY KEY,
    code        TEXT UNIQUE      NOT NULL,
    name        TEXT             NOT NULL,
    type_id     BIGINT NOT NULL  REFERENCES sensor_types (id) ON DELETE RESTRICT,
    unit        unit             NOT NULL,
    lower_alarm_bound DOUBLE PRECISION NOT NULL,
    upper_alarm_bound DOUBLE PRECISION NOT NULL,
    x           INTEGER NOT NULL,
    y     INTEGER NOT NULL,
    z     INTEGER NOT NULL,
    active      BOOLEAN          NOT NULL DEFAULT true,
    comment     TEXT,
    formula_id     BIGINT NOT NULL REFERENCES formulas (id) ON DELETE RESTRICT,
    version     INTEGER          NOT NULL DEFAULT 1
);

CREATE INDEX idx_sensors_formula_id ON sensors (formula_id);

