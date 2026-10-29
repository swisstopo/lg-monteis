-- -----------------------------------------------------------------------------
-- Sensor documents (MON-182)
--
-- Files uploaded to a sensor's master data, built like experiment_documents
-- (V18). The file content lives in S3 under a key derived from sensor_id and
-- id; this table holds the metadata the document list shows.
--
-- Whoever may read a sensor may read its documents. Whoever may update one of
-- the sensor's experiments may add documents to it, an admin or global editor
-- any. Deleting and updating documents is out of scope, so there is no UPDATE or
-- DELETE policy and RLS rejects both. The application checks the same function
-- before it uploads to S3 (SensorDocumentService).
-- -----------------------------------------------------------------------------

CREATE FUNCTION can_write_an_experiment_of_sensor(target_sensor_id uuid) RETURNS boolean AS $$
    SELECT can_write_all_experiments() OR EXISTS (
        SELECT 1 FROM experiment_sensor es
        WHERE es.sensor_id = target_sensor_id
          AND es.experiment_id = ANY (current_write_experiment_ids())
    )
$$ LANGUAGE sql STABLE;

CREATE TABLE sensor_documents (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    sensor_id    UUID NOT NULL REFERENCES sensors (id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by  VARCHAR(255) NOT NULL
);

CREATE INDEX sensor_documents_sensor_id_uploaded_at_idx
    ON sensor_documents (sensor_id, uploaded_at DESC);

ALTER TABLE sensor_documents ENABLE ROW LEVEL SECURITY;

CREATE POLICY sensor_documents_read ON sensor_documents FOR SELECT
    USING (can_access_sensor(sensor_documents.sensor_id));

-- WITH CHECK raises SQLSTATE 42501 for a forbidden insert, which the
-- application maps to 403 (see V16).
CREATE POLICY sensor_documents_insert ON sensor_documents FOR INSERT
    WITH CHECK (can_write_an_experiment_of_sensor(sensor_documents.sensor_id));
