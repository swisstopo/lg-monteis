CREATE TABLE experiments (
                             id BIGSERIAL PRIMARY KEY,
                             name TEXT UNIQUE NOT NULL,
                             description TEXT,
                             version INTEGER NOT NULL DEFAULT 1
);

-- Many-to-Many Join Table
CREATE TABLE experiment_sensor (
                                   experiment_id BIGINT NOT NULL REFERENCES experiments (id) ON DELETE CASCADE,
                                   sensor_id BIGINT NOT NULL REFERENCES sensors (id) ON DELETE CASCADE,
                                   PRIMARY KEY (experiment_id, sensor_id)
);