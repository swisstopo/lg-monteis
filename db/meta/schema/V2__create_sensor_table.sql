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