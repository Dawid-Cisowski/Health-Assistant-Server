-- Add index for frequently queried (device_id, measurement_id) combination
-- This supports the findByDeviceIdAndMeasurementId repository method
CREATE INDEX IF NOT EXISTS idx_body_measurement_device_measurement
    ON body_measurement_projections(device_id, measurement_id);
