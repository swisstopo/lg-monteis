CREATE TABLE formulas
(
    id         BIGSERIAL PRIMARY KEY,
    expression TEXT UNIQUE NOT NULL,
    version    INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE sensor_types
(
    id         BIGSERIAL PRIMARY KEY,
    type       TEXT UNIQUE NOT NULL,
    version    INTEGER NOT NULL DEFAULT 1
);