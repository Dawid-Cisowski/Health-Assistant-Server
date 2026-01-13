-- Add index for frequently queried (device_id, measurement_id) combination
-- This supports the findByDeviceIdAndMeasurementId repository method
CREATE INDEX IF NOT EXISTS idx_weight_device_measurement
    ON weight_measurement_projections(device_id, measurement_id);
