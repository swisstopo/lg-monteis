-- MON-143: sensor_id was always the composite DAS ingest key
-- (<DAS>__<das_sensor_alias>__<das_parameter_alias>), never a "sensor code" - the name was just
-- never updated. Rename it to das_key so it's no longer confusable with sensor_parameter_id (the
-- actual, unrelated notion of "sensor id" used elsewhere in the schema).
ALTER TABLE sensor_reading RENAME COLUMN sensor_id TO das_key;
ALTER INDEX sensor_reading_sensor_time_idx RENAME TO sensor_reading_das_key_time_idx;
