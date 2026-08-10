ALTER TABLE experiments
    ADD COLUMN owner TEXT,
    ADD COLUMN experiment_start DATE NOT NULL,
    ADD COLUMN experiment_end DATE NOT NULL;

ALTER TABLE experiments
    RENAME COLUMN description TO comment;