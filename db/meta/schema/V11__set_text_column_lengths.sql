-- -----------------------------------------------------------------------------
-- Bound the free-text columns that have been unconstrained TEXT since they were
-- introduced. Lengths mirror the @Size limits already enforced on the matching
-- core DTOs (WriteSensorDto, WriteSensorParameterDto, WriteExperimentDto), or
-- establish a new limit where the DTO didn't have one yet (sensor_types.name,
-- formulas.expression, experiments.comment - see the accompanying core changes).
--
-- Safe as a plain ALTER COLUMN ... TYPE: none of the current data (including
-- the dev seed) exceeds these limits, so no USING/truncation is needed.
--
-- Left out of scope: jv_snapshot.state (Javers-owned schema, already excluded
-- from structural changes by V8) and raw_sensor_reading.status (declared TEXT
-- here for the FDW mapping but backed by the range_category enum on the
-- TimescaleDB side - not a text-length concern).
--
-- sensor_reading_secured (V9) depends on sensor_parameter.das_parameter_alias
-- (its join predicate) and, via its `r.*` projection, every column of
-- raw_sensor_reading including sensor_id - Postgres refuses to ALTER COLUMN
-- TYPE on a column a view depends on. Drop it up front and recreate it
-- identically (same definition and GRANT as V9) once the alters are done.
-- -----------------------------------------------------------------------------

DROP VIEW sensor_reading_secured;

ALTER TABLE experiments ALTER COLUMN name TYPE VARCHAR(100);
ALTER TABLE experiments ALTER COLUMN "owner" TYPE VARCHAR(255);
ALTER TABLE experiments ALTER COLUMN "comment" TYPE VARCHAR(4096);

ALTER TABLE sensors ALTER COLUMN name TYPE VARCHAR(100);
ALTER TABLE sensors ALTER COLUMN das_sensor_alias TYPE VARCHAR(255);
ALTER TABLE sensors ALTER COLUMN comment TYPE VARCHAR(4096);
ALTER TABLE sensors ALTER COLUMN das TYPE VARCHAR(100);

ALTER TABLE sensor_parameter ALTER COLUMN name TYPE VARCHAR(255);
ALTER TABLE sensor_parameter ALTER COLUMN das_parameter_alias TYPE VARCHAR(255);
ALTER TABLE sensor_parameter ALTER COLUMN comment TYPE VARCHAR(4096);

ALTER TABLE sensor_types ALTER COLUMN name TYPE VARCHAR(100);

ALTER TABLE formulas ALTER COLUMN expression TYPE VARCHAR(1024);

CREATE VIEW sensor_reading_secured WITH (security_invoker = true) AS
SELECT r.*
FROM raw_sensor_reading r
         JOIN sensor_parameter s ON s.das_parameter_alias = r.sensor_id;

GRANT SELECT ON sensor_reading_secured TO core_app;
