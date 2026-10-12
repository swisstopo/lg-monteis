-- -----------------------------------------------------------------------------
-- Organisations
--
-- A pick list the admin maintains, and the many-to-many link from experiments to
-- it. Deleting an organisation removes it from every experiment (ON DELETE
-- CASCADE). Names are unique case-insensitively.
--
-- The organisations themselves have no row-level security, the list is the same
-- for every caller. The links follow their experiment like experiment_documents
-- (V18): whoever may read the experiment reads its links, whoever may update it
-- adds and removes them. The application layer enforces the same rules
-- (ExperimentWriteAuthorizationManager).
-- -----------------------------------------------------------------------------

CREATE TABLE organisations (
    id   UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(100) NOT NULL,
    "comment" VARCHAR(4096)
);

CREATE UNIQUE INDEX organisations_name_lower_idx ON organisations (lower(name));

CREATE TABLE experiment_organisation (
    experiment_id   UUID NOT NULL REFERENCES experiments (id) ON DELETE CASCADE,
    organisation_id UUID NOT NULL REFERENCES organisations (id) ON DELETE CASCADE,
    PRIMARY KEY (experiment_id, organisation_id)
);

CREATE INDEX experiment_organisation_organisation_id_idx
    ON experiment_organisation (organisation_id);

ALTER TABLE experiment_organisation ENABLE ROW LEVEL SECURITY;

CREATE POLICY experiment_organisation_read ON experiment_organisation FOR SELECT
    USING (can_access_experiment(experiment_organisation.experiment_id));

-- WITH CHECK raises SQLSTATE 42501 for a forbidden insert, which the
-- application maps to 403 (see V16).
CREATE POLICY experiment_organisation_insert ON experiment_organisation FOR INSERT
    WITH CHECK (can_write_experiment(experiment_organisation.experiment_id));

CREATE POLICY experiment_organisation_delete ON experiment_organisation FOR DELETE
    USING (can_write_experiment(experiment_organisation.experiment_id));
