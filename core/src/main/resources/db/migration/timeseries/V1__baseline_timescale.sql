CREATE TABLE IF NOT EXISTS simple_metrics (
                                time  TIMESTAMPTZ      NOT NULL,
                                value DOUBLE PRECISION
);

SELECT create_hypertable('simple_metrics', 'time');