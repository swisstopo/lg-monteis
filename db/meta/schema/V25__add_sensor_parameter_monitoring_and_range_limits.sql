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
            OR (lower_special_alarm_limit IS NOT NULL AND upper_special_alarm_limit IS NOT NULL)),

    -- A limit pair is either fully set or fully absent, so readers only need to null-check one side.
    ADD CONSTRAINT chk_sensor_parameter_range_limits_pair
        CHECK ((lower_range_limit IS NULL) = (upper_range_limit IS NULL)),
    ADD CONSTRAINT chk_sensor_parameter_special_alarm_limits_pair
        CHECK ((lower_special_alarm_limit IS NULL) = (upper_special_alarm_limit IS NULL));

-- Existing parameters have always notified on their alarm limits: keep that for them.
UPDATE sensor_parameter SET notification_active = true;
