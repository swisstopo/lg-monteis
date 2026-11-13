ALTER TABLE sensor_parameter
    -- Physical / plausibility sensor range limits
    ADD COLUMN lower_range_limit          DOUBLE PRECISION,
    ADD COLUMN upper_range_limit          DOUBLE PRECISION,

    -- Timeout & trigger threshold
    ADD COLUMN timeout                    INTERVAL,
    ADD COLUMN frequency_trigger          INTEGER,

    -- Standard alarm notification toggle
    ADD COLUMN notification_active        BOOLEAN NOT NULL DEFAULT false,

    -- Close monitoring & secondary alarm thresholds
    ADD COLUMN closely_monitored          BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN lower_special_alarm_limit  DOUBLE PRECISION,
    ADD COLUMN upper_special_alarm_limit  DOUBLE PRECISION;

-- Optional sanity constraints to ensure low <= high when both bounds are set
ALTER TABLE sensor_parameter
    ADD CONSTRAINT chk_sensor_parameter_range_limits
        CHECK (lower_range_limit IS NULL OR upper_range_limit IS NULL OR lower_range_limit <= upper_range_limit),
    ADD CONSTRAINT chk_sensor_parameter_alarm_limits
        CHECK (lower_alarm_limit IS NULL OR upper_alarm_limit IS NULL OR lower_alarm_limit <= upper_alarm_limit),
    ADD CONSTRAINT chk_sensor_parameter_special_alarm_limits
        CHECK (lower_special_alarm_limit IS NULL OR upper_special_alarm_limit IS NULL OR lower_special_alarm_limit <= upper_special_alarm_limit),
    ADD CONSTRAINT chk_sensor_parameter_frequency_trigger_positive
        CHECK (frequency_trigger IS NULL OR frequency_trigger > 0);
