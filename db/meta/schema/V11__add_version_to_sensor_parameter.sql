-- MON-143: the Kafka sensor-config object moves to publishing per sensor_parameter (formula/bounds now
-- live at this level, see V9), keyed off its stable sensor_parameter_id. Reprocessing decides whether a
-- historical reading is stale by comparing versions, so sensor_parameter needs its own optimistic-locking
-- version column - only the parent `sensors` table had one until now.
--
-- jOOQ auto-detects any column literally named `version` as an optimistic-locking field
-- (see `recordVersionFields` in core/pom.xml), so no codegen config change is needed: SensorParameterRecord
-- behaves exactly like SensorsRecord already does.
ALTER TABLE sensor_parameter ADD COLUMN version INTEGER NOT NULL DEFAULT 1;
