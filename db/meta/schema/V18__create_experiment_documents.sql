-- -----------------------------------------------------------------------------
-- Experiment documents (MON-171)
--
-- Files uploaded to an experiment's master data. The file content lives in S3
-- under a key derived from experiment_id and id; this table holds the metadata
-- the document list shows.
--
-- Whoever may read an experiment may read its documents; whoever may update an
-- experiment may add documents to it. Deleting and updating documents is out of
-- scope, so there is no UPDATE or DELETE policy and RLS rejects both. The
-- application layer enforces the same rules (ExperimentWriteAuthorizationManager).
-- -----------------------------------------------------------------------------

CREATE TABLE experiment_documents (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    experiment_id UUID NOT NULL REFERENCES experiments (id) ON DELETE CASCADE,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(255) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by   VARCHAR(255) NOT NULL
);

CREATE INDEX experiment_documents_experiment_id_uploaded_at_idx
    ON experiment_documents (experiment_id, uploaded_at DESC);

ALTER TABLE experiment_documents ENABLE ROW LEVEL SECURITY;

CREATE POLICY experiment_documents_read ON experiment_documents FOR SELECT
    USING (can_access_experiment(experiment_documents.experiment_id));

-- WITH CHECK raises SQLSTATE 42501 for a forbidden insert, which the
-- application maps to 403 (see V16).
CREATE POLICY experiment_documents_insert ON experiment_documents FOR INSERT
    WITH CHECK (can_write_experiment(experiment_documents.experiment_id));
