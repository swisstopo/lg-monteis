TRUNCATE TABLE sensors CASCADE;

ALTER TABLE sensors
    ADD COLUMN fulcrum_id TEXT,
    ADD COLUMN experiment_id UUID
        REFERENCES experiments (id)
        ON DELETE CASCADE;
