-- Heart Rate Projections (all data points, each 15-min measurement)
CREATE TABLE heart_rate_projections (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    avg_bpm INTEGER NOT NULL,
    min_bpm INTEGER,
    max_bpm INTEGER,
    samples INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_hr_event UNIQUE (event_id)
);

CREATE INDEX idx_hr_device_measured ON heart_rate_projections(device_id, measured_at DESC);

-- Resting Heart Rate Projections (one value per day)
CREATE TABLE resting_heart_rate_projections (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    resting_bpm INTEGER NOT NULL,
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_rhr_event UNIQUE (event_id),
    CONSTRAINT uq_rhr_device_date UNIQUE (device_id, date)
);

CREATE INDEX idx_rhr_device_date ON resting_heart_rate_projections(device_id, date DESC);
