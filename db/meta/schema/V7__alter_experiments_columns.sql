-- -----------------------------------------------------------------------------
-- Reshape the experiments table to the create/update-experiment model:
-- drop the free-text description, add owner + a start/end period + an optional
-- comment. The columns are added NOT NULL, so existing rows must be cleared
-- first. This is safe: the experiments table was only just introduced, carries
-- no production data yet, and the dev rows are repopulated by the repeatable
-- seed migration that runs after this one.
-- -----------------------------------------------------------------------------

TRUNCATE TABLE experiments CASCADE;

ALTER TABLE experiments DROP COLUMN description;
ALTER TABLE experiments ADD COLUMN "owner" TEXT NOT NULL;
ALTER TABLE experiments ADD COLUMN "start" DATE NOT NULL;
ALTER TABLE experiments ADD COLUMN "end" DATE NOT NULL;
ALTER TABLE experiments ADD COLUMN "comment" TEXT;
