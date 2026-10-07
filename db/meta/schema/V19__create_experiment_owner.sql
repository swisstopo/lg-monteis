-- -----------------------------------------------------------------------------
-- Experiment owners (MON-177)
--
-- only the keycloak user id is stored, name and email are read from keycloak
-- whenever they are shown, so no personal data ends up in this db.
-- experiments.owner held the creator's subject and was never displayed, it is
-- dropped without migrating the values.
--
-- reading follows the experiment. assigning owners is admin only (filter chain),
-- the write policies repeat the experiment write rule as defence in depth.
-- -----------------------------------------------------------------------------

CREATE TABLE experiment_owner
(
    experiment_id UUID NOT NULL REFERENCES experiments (id) ON DELETE CASCADE,
    user_id       UUID NOT NULL,
    PRIMARY KEY (experiment_id, user_id)
);

CREATE INDEX experiment_owner_user_id_idx ON experiment_owner (user_id);

ALTER TABLE experiment_owner ENABLE ROW LEVEL SECURITY;

CREATE POLICY experiment_owner_read ON experiment_owner FOR SELECT
    USING (can_access_experiment(experiment_owner.experiment_id));
CREATE POLICY experiment_owner_insert ON experiment_owner FOR INSERT
    WITH CHECK (can_write_experiment(experiment_owner.experiment_id));
CREATE POLICY experiment_owner_delete ON experiment_owner FOR DELETE
    USING (can_write_experiment(experiment_owner.experiment_id));

ALTER TABLE experiments DROP COLUMN owner;
