-- MON-143: add the stable sensor_parameter_id (UUID) as a separate, nullable, non-PK column.
--
-- The primary key stays (timestamp, sensor_id): sensor_id is the composite DAS ingest key
-- (<DAS>__<das_sensor_alias>__<das_parameter_alias>), always derivable from the raw payload the
-- moment it arrives. sensor_parameter_id cannot be part of the primary key because the pipeline may
-- not yet know it for a given key (Core hasn't published that parameter's config yet, or hasn't
-- republished since a restart) - it's populated once resolved via the SensorConfigCache and
-- backfilled retroactively for older rows by the reprocessing flow (see HistoricalReadingChunkProcessor),
-- never required at write time.
ALTER TABLE sensor_reading ADD COLUMN sensor_parameter_id UUID;

-- Mirrors sensor_reading_sensor_time_idx: serves reprocessing's id-matched backlog scan
-- (sensor_parameter_id = ? ORDER BY timestamp DESC) without a BitmapOr/local Sort, exactly like the
-- existing index does for the DAS-key-matched backlog scan - see that index's comment for why a
-- second, narrower index on top of this shape would be actively harmful instead of redundant.
CREATE INDEX sensor_reading_sensor_parameter_time_idx
    ON sensor_reading (sensor_parameter_id, timestamp DESC);
