-- -----------------------------------------------------------------------------
-- Fulcrum entries (MON-228)
--
-- A mirror of the Mont Terri literature maintained in Fulcrum, filled by the
-- nightly sync in core (ADR "Fulcrum Documents spiegeln statt On-Demand
-- abrufen"). Each Fulcrum entry has exactly one Fulcrum Document, whose content
-- is in S3 under fulcrum-documents/<attachment_id>. The id is Fulcrum's record
-- id, attachment_id Fulcrum's id of the file.
--
-- Like an experiment document upload, the content goes to S3 first and the row
-- after, so an entry only exists once its document does. A replaced file is
-- downloaded under its new attachment id before the row switches to it, the old
-- object stays in S3 as a backup.
--
-- Nothing is ever deleted, an entry Fulcrum no longer returns keeps its row and
-- gets removed_at (backup, PO decision).
--
-- No row-level security: the entries belong to no experiment and every caller
-- sees the same list. Opening a document is guarded by documents:read in the
-- filter chain.
-- -----------------------------------------------------------------------------

CREATE TABLE fulcrum_entries (
    id            UUID PRIMARY KEY,
    number        VARCHAR(255),
    title         TEXT,
    authors       TEXT,
    year          INTEGER,
    category      VARCHAR(255),
    keywords      TEXT[] NOT NULL DEFAULT '{}',
    attachment_id UUID NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(255) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    stored_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL,
    removed_at    TIMESTAMPTZ
);

-- ShedLock (JdbcTemplateLockProvider), keeps the sync to one core pod at a time
-- and tells whether a sync is running. the schema ShedLock documents for
-- PostgreSQL, it writes utc into plain TIMESTAMP
CREATE TABLE shedlock (
    name       VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at  TIMESTAMP NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
