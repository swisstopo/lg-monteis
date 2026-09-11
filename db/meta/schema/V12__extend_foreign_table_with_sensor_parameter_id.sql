-- MON-143: TimescaleDB's sensor_reading gets a new nullable sensor_parameter_id column (see the
-- companion db/timescale migration). Expose it through the FDW mapping so sensor_reading_secured
-- (V13) can join on it.
ALTER FOREIGN TABLE raw_sensor_reading ADD COLUMN sensor_parameter_id uuid;
