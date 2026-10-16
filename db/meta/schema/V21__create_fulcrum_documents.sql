-- -----------------------------------------------------------------------------
-- Fulcrum entries and Fulcrum Documents (MON-228)
--
-- A mirror of the Mont Terri literature maintained in Fulcrum, filled by the
-- nightly sync in core (ADR "Fulcrum Documents spiegeln statt On-Demand
-- abrufen"). A Fulcrum entry is one literature entry with the attachments
-- Fulcrum lists for it, a Fulcrum Document is one of those attachments whose
-- content is stored in S3 under fulcrum-documents/<id>. The ids are Fulcrum's:
-- the record id for an entry, the attachment id for a document.
--
-- Like an experiment document upload, the content goes to S3 first and the
-- fulcrum_documents row after, so every row has its content. An attachment an
-- entry lists without a row is still to be downloaded, the next sync retries it.
--
-- Nothing is ever deleted, an entry Fulcrum no longer returns and a document no
-- entry lists anymore keep their row and get removed_at (backup, PO decision).
--
-- No row-level security: both belong to no experiment and every caller sees
-- the same list. Opening a document is guarded by documents:read in the filter
-- chain.
-- -----------------------------------------------------------------------------

CREATE TABLE fulcrum_entries (
    id               UUID PRIMARY KEY,
    number           VARCHAR(255),
    title            TEXT,
    authors          TEXT,
    year             INTEGER,
    category         VARCHAR(255),
    keywords         TEXT[] NOT NULL DEFAULT '{}',
    -- parallel arrays, in Fulcrum's order
    attachment_ids   UUID[] NOT NULL DEFAULT '{}',
    attachment_names TEXT[] NOT NULL DEFAULT '{}',
    last_seen_at     TIMESTAMPTZ NOT NULL,
    removed_at       TIMESTAMPTZ
);

CREATE TABLE fulcrum_documents (
    id           UUID PRIMARY KEY,
    entry_id     UUID NOT NULL REFERENCES fulcrum_entries (id),
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    stored_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at   TIMESTAMPTZ
);

CREATE INDEX fulcrum_documents_entry_id_idx ON fulcrum_documents (entry_id);

CREATE TABLE fulcrum_sync_runs (
    id               UUID PRIMARY KEY DEFAULT uuidv7(),
    status           VARCHAR(20) NOT NULL,
    triggered_by     VARCHAR(255) NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    entries_seen     INTEGER,
    documents_stored INTEGER,
    documents_failed INTEGER
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
