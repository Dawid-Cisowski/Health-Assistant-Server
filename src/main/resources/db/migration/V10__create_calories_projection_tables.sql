-- Calories hourly projections (per device, per date, per hour)
CREATE TABLE calories_hourly_projections (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(128) NOT NULL,
    date DATE NOT NULL,
    hour INTEGER NOT NULL CHECK (hour >= 0 AND hour <= 23),
    calories_kcal DOUBLE PRECISION NOT NULL DEFAULT 0,
    bucket_count INTEGER NOT NULL DEFAULT 0,
    first_bucket_time TIMESTAMP WITH TIME ZONE,
    last_bucket_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_calories_hourly_device_date_hour UNIQUE(device_id, date, hour)
);

-- Calories daily projections (aggregated from hourly, per device)
CREATE TABLE calories_daily_projections (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(128) NOT NULL,
    date DATE NOT NULL,
    total_calories_kcal DOUBLE PRECISION NOT NULL DEFAULT 0,
    first_calorie_time TIMESTAMP WITH TIME ZONE,
    last_calorie_time TIMESTAMP WITH TIME ZONE,
    most_active_hour INTEGER,
    most_active_hour_calories DOUBLE PRECISION,
    active_hours_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_calories_daily_device_date UNIQUE(device_id, date)
);

-- Indexes for common query patterns
CREATE INDEX idx_calories_daily_device_date ON calories_daily_projections(device_id, date DESC);
CREATE INDEX idx_calories_hourly_device_date ON calories_hourly_projections(device_id, date DESC);
CREATE INDEX idx_calories_hourly_device_date_hour ON calories_hourly_projections(device_id, date, hour);
