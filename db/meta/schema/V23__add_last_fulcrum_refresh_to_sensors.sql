-- -----------------------------------------------------------------------------
-- MON-173: when MONTEIS last read a sensor's coordinates from Fulcrum.
--
-- Set on every successful read (Fulcrum refresh, saving a sensor with a fulcrum_id), also when
-- the coordinates did not change. NULL while the sensor has no fulcrum_id.
-- -----------------------------------------------------------------------------
ALTER TABLE sensors
    ADD COLUMN last_fulcrum_refresh_at TIMESTAMPTZ;
