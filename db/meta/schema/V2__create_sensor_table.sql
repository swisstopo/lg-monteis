CREATE TYPE sensor_type AS ENUM ('WIND_SPEED', 'STRESS_RADIAL', 'TEMPERATURE', 'VOLUME', 'OTHER');
CREATE TYPE unit AS ENUM ('SECONDS', 'METER', 'KILOGRAM', 'AMPERE', 'KELVIN', 'MOLE', 'CANDELA');

CREATE TABLE sensors
(
    id          BIGSERIAL PRIMARY KEY,
    code        TEXT UNIQUE      NOT NULL,
    name        TEXT             NOT NULL,
    type        sensor_type      NOT NULL,
    unit        unit             NOT NULL,
    lower_alarm_bound DOUBLE PRECISION NOT NULL,
    upper_alarm_bound DOUBLE PRECISION NOT NULL,
    x_local     DOUBLE PRECISION NOT NULL,
    y_local     DOUBLE PRECISION NOT NULL,
    z_local     DOUBLE PRECISION NOT NULL,
    active      BOOLEAN          NOT NULL DEFAULT true,
    comment     TEXT,
    formula_id     BIGINT NOT NULL REFERENCES formulas (id) ON DELETE RESTRICT,
    version     INTEGER          NOT NULL DEFAULT 1
);

CREATE INDEX idx_sensors_formula_id ON sensors (formula_id);

