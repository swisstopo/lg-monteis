-- -----------------------------------------------------------------------------
-- MON-143: repoint sensor_reading_secured's join from sensor_parameter.das_parameter_alias (TEXT,
-- only unique per sensor - see V9's comment) to sensor_parameter.id / sensor_reading.sensor_parameter_id
-- (UUID, globally unique on both sides). Fixes a latent bug where two different sensors reusing the
-- same das_parameter_alias could collide in this join, and matches the DAS-remap-safe persistence
-- key now carried through the Kafka config object.
--
-- Same shape as before (V6/V9): no security_barrier, plain join against a natively-RLS'd local table
-- (sensor_parameter, see V10), so the caller's ORDER BY still becomes pathkeys on the foreign scan -
-- see V6's comment for why security_barrier or a function-based predicate would break that pushdown.
--
-- A reading whose sensor_parameter_id hasn't been backfilled yet (NULL) will not match any row here
-- until the pipeline's reprocessing flow backfills it - see the pipeline-side migration/reprocessing
-- changes for the backfill mechanism, and re-publish every active sensor parameter's config once after
-- this deploys to force that backfill before relying on this view for reads.
--
-- CREATE OR REPLACE cannot be used here: V13 renamed the underlying foreign table's column from
-- sensor_id to das_key, but a view created via `SELECT r.*` freezes its own output column names at
-- creation time - it does not follow a later rename of the source column. Postgres refuses to
-- rename an existing view output column via CREATE OR REPLACE ("cannot change name of view column
-- ... Use ALTER VIEW ... RENAME COLUMN instead"), so the view must be dropped and recreated instead.
-- -----------------------------------------------------------------------------
DROP VIEW sensor_reading_secured;

CREATE VIEW sensor_reading_secured WITH (security_invoker = true) AS
SELECT r.*
FROM raw_sensor_reading r
JOIN sensor_parameter s ON s.id = r.sensor_parameter_id;

GRANT SELECT ON sensor_reading_secured TO core_app;
