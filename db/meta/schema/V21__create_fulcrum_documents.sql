-- -----------------------------------------------------------------------------
-- Fulcrum documents (MON-228)
--
-- A mirror of the Mont Terri literature entries maintained in Fulcrum, filled by
-- the nightly sync in core (ADR "Fulcrum Documents spiegeln statt On-Demand
-- abrufen"). The ids are Fulcrum's: the record id for a document, the
-- attachment id for a file, which is also the S3 key below fulcrum-documents/.
--
-- Nothing is ever deleted, an entry or file Fulcrum no longer returns keeps its
-- row and gets removed_at (backup, PO decision). A file without stored_at is
-- known but its content is not in S3 yet, the next sync retries it.
--
-- No row-level security: the documents belong to no experiment and every caller
-- sees the same list. Opening a file is guarded by documents:read in the filter
-- chain.
-- -----------------------------------------------------------------------------

CREATE TABLE fulcrum_documents (
    id           UUID PRIMARY KEY,
    number       VARCHAR(255),
    title        TEXT,
    authors      TEXT,
    year         INTEGER,
    category     VARCHAR(255),
    keywords     TEXT[] NOT NULL DEFAULT '{}',
    last_seen_at TIMESTAMPTZ NOT NULL,
    removed_at   TIMESTAMPTZ
);

CREATE TABLE fulcrum_document_files (
    id           UUID PRIMARY KEY,
    document_id  UUID NOT NULL REFERENCES fulcrum_documents (id),
    position     INTEGER NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes   BIGINT,
    stored_at    TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ NOT NULL,
    removed_at   TIMESTAMPTZ
);

CREATE INDEX fulcrum_document_files_document_id_idx ON fulcrum_document_files (document_id);

CREATE TABLE fulcrum_sync_runs (
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    status          VARCHAR(20) NOT NULL,
    triggered_by    VARCHAR(255) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    documents_seen  INTEGER,
    files_stored    INTEGER,
    files_failed    INTEGER
);

CREATE INDEX fulcrum_sync_runs_started_at_idx ON fulcrum_sync_runs (started_at DESC);

-- ShedLock (JdbcTemplateLockProvider), keeps the sync to one core pod at a time.
-- the schema ShedLock documents for PostgreSQL, it writes utc into plain TIMESTAMP
CREATE TABLE shedlock (
    name       VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at  TIMESTAMP NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
