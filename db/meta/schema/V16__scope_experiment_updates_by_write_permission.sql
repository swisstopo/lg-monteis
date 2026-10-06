-- -----------------------------------------------------------------------------
-- Scope experiment updates by write permission (MON-196)
--
-- Until now writes were unrestricted at the DB level (see V6): only admins could
-- write, gated in the application layer. Experiment writers may now update the
-- experiments they are granted. Which experiments may be written is enforced
-- both here and in the application layer (ExperimentWriteAuthorizationManager).
--
-- The application propagates two more transaction-local GUCs next to the read
-- ones: app.write_all (api:admin or api:experiment:write-all) and
-- app.user_write_experiment_ids (the caller's writable experiment IDs).
--
-- INSERT/DELETE on experiments and all sensor writes stay admin-only and are
-- still enforced in the application layer only.
-- -----------------------------------------------------------------------------

CREATE FUNCTION can_write_all() RETURNS boolean AS $$
    SELECT current_setting('app.write_all', true) = 'true'
$$ LANGUAGE sql STABLE;

CREATE FUNCTION current_write_experiment_ids() RETURNS uuid[] AS $$
    SELECT COALESCE(
        string_to_array(NULLIF(current_setting('app.user_write_experiment_ids', true), ''), ',')::uuid[],
        ARRAY[]::uuid[]
    )
$$ LANGUAGE sql STABLE;

CREATE FUNCTION can_write_experiment(target_experiment_id uuid) RETURNS boolean AS $$
    SELECT can_write_all() OR target_experiment_id = ANY (current_write_experiment_ids())
$$ LANGUAGE sql STABLE;

-- The write check sits in WITH CHECK, not USING: USING silently filters rows, so
-- a forbidden update would affect 0 rows and surface as an optimistic locking
-- conflict. WITH CHECK raises SQLSTATE 42501 instead, which the application maps
-- to 403. USING keeps the candidate rows to the ones the caller may read.
DROP POLICY experiments_update ON experiments;
CREATE POLICY experiments_update ON experiments FOR UPDATE
    USING (can_access_experiment(experiments.id))
    WITH CHECK (can_write_experiment(experiments.id));
