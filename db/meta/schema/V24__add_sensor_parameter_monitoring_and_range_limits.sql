ALTER TABLE sensor_parameter
    -- Measuring range of the sensor, from the manufacturer's config file
    ADD COLUMN lower_range_limit         DOUBLE PRECISION,
    ADD COLUMN upper_range_limit         DOUBLE PRECISION,

    -- Switches notifications on the alarm limits on or off
    ADD COLUMN notification_active       BOOLEAN NOT NULL DEFAULT false,

    -- Switches the separate "closely monitored" alarm limits on or off
    ADD COLUMN closely_monitored         BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN lower_special_alarm_limit DOUBLE PRECISION,
    ADD COLUMN upper_special_alarm_limit DOUBLE PRECISION,

    ADD CONSTRAINT chk_sensor_parameter_closely_monitored_needs_special_alarm_limits
        CHECK (NOT closely_monitored
            OR (lower_special_alarm_limit IS NOT NULL AND upper_special_alarm_limit IS NOT NULL));

-- Existing parameters have always notified on their alarm limits: keep that for them.
UPDATE sensor_parameter SET notification_active = true;
