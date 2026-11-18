-- MON-173
ALTER TABLE sensors
    ADD COLUMN last_fulcrum_refresh_at TIMESTAMPTZ;
