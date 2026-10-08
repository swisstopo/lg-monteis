-- -----------------------------------------------------------------------------
-- Organisations
--
-- A pick list the admin maintains, and the many-to-many link from experiments to
-- it. Deleting an organisation removes it from every experiment (ON DELETE
-- CASCADE). Names are unique case-insensitively.
--
-- No row-level security: the list is the same for every caller, and who may
-- change an experiment's organisations is decided by the experiment update
-- itself (ExperimentWriteAuthorizationManager, experiments_update policy).
-- -----------------------------------------------------------------------------

CREATE TABLE organisations (
    id   UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(100) NOT NULL
);

CREATE UNIQUE INDEX organisations_name_lower_idx ON organisations (lower(name));

CREATE TABLE experiment_organisation (
    experiment_id   UUID NOT NULL REFERENCES experiments (id) ON DELETE CASCADE,
    organisation_id UUID NOT NULL REFERENCES organisations (id) ON DELETE CASCADE,
    PRIMARY KEY (experiment_id, organisation_id)
);

CREATE INDEX experiment_organisation_organisation_id_idx
    ON experiment_organisation (organisation_id);
