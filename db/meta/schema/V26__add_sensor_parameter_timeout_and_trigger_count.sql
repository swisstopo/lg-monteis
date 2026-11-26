-- IF (NOT) EXISTS: local DBs may have run an earlier draft of V25 that already had some of this.
ALTER TABLE sensor_parameter
    -- Seconds without a new reading before alerting, e.g. 9000 (2.5 hours)
    ADD COLUMN IF NOT EXISTS timeout                    INTEGER CHECK (timeout > 0),
    -- Consecutive readings outside the alarm limits before alerting
    ADD COLUMN IF NOT EXISTS notification_trigger_count INTEGER CHECK (notification_trigger_count > 0),

    DROP COLUMN IF EXISTS notification_timeout_hours,
    DROP COLUMN IF EXISTS comment;
