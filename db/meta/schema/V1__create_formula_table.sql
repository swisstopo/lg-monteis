CREATE TABLE formulas
(
    id         BIGSERIAL PRIMARY KEY,
    expression TEXT UNIQUE NOT NULL,
    version    INTEGER NOT NULL DEFAULT 1
);