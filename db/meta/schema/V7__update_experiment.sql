CREATE TYPE status AS ENUM ('ACTIVE', 'HISTORIC');

ALTER TABLE experiments
    ADD COLUMN owner TEXT,
    ADD COLUMN experiment_start DATE NOT NULL,
    ADD COLUMN experiment_end DATE NOT NULL,
    ADD COLUMN status TEXT