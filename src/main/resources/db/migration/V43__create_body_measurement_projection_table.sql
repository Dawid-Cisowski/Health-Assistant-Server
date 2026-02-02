-- Create body measurement projection table for tracking body dimensions
CREATE TABLE body_measurement_projections (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    measurement_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Arms
    biceps_left_cm DECIMAL(5,2),
    biceps_right_cm DECIMAL(5,2),
    forearm_left_cm DECIMAL(5,2),
    forearm_right_cm DECIMAL(5,2),

    -- Torso
    chest_cm DECIMAL(5,2),
    waist_cm DECIMAL(5,2),
    abdomen_cm DECIMAL(5,2),
    hips_cm DECIMAL(5,2),
    neck_cm DECIMAL(5,2),
    shoulders_cm DECIMAL(5,2),

    -- Legs
    thigh_left_cm DECIMAL(5,2),
    thigh_right_cm DECIMAL(5,2),
    calf_left_cm DECIMAL(5,2),
    calf_right_cm DECIMAL(5,2),

    -- Optional notes
    notes VARCHAR(500),

    -- Timestamps and versioning
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    -- Constraints for valid measurement ranges (in cm)
    CONSTRAINT chk_biceps_left CHECK (biceps_left_cm IS NULL OR (biceps_left_cm >= 10 AND biceps_left_cm <= 100)),
    CONSTRAINT chk_biceps_right CHECK (biceps_right_cm IS NULL OR (biceps_right_cm >= 10 AND biceps_right_cm <= 100)),
    CONSTRAINT chk_forearm_left CHECK (forearm_left_cm IS NULL OR (forearm_left_cm >= 10 AND forearm_left_cm <= 80)),
    CONSTRAINT chk_forearm_right CHECK (forearm_right_cm IS NULL OR (forearm_right_cm >= 10 AND forearm_right_cm <= 80)),
    CONSTRAINT chk_chest CHECK (chest_cm IS NULL OR (chest_cm >= 40 AND chest_cm <= 300)),
    CONSTRAINT chk_waist CHECK (waist_cm IS NULL OR (waist_cm >= 40 AND waist_cm <= 300)),
    CONSTRAINT chk_abdomen CHECK (abdomen_cm IS NULL OR (abdomen_cm >= 40 AND abdomen_cm <= 300)),
    CONSTRAINT chk_hips CHECK (hips_cm IS NULL OR (hips_cm >= 40 AND hips_cm <= 300)),
    CONSTRAINT chk_neck CHECK (neck_cm IS NULL OR (neck_cm >= 20 AND neck_cm <= 80)),
    CONSTRAINT chk_shoulders CHECK (shoulders_cm IS NULL OR (shoulders_cm >= 70 AND shoulders_cm <= 200)),
    CONSTRAINT chk_thigh_left CHECK (thigh_left_cm IS NULL OR (thigh_left_cm >= 20 AND thigh_left_cm <= 150)),
    CONSTRAINT chk_thigh_right CHECK (thigh_right_cm IS NULL OR (thigh_right_cm >= 20 AND thigh_right_cm <= 150)),
    CONSTRAINT chk_calf_left CHECK (calf_left_cm IS NULL OR (calf_left_cm >= 15 AND calf_left_cm <= 80)),
    CONSTRAINT chk_calf_right CHECK (calf_right_cm IS NULL OR (calf_right_cm >= 15 AND calf_right_cm <= 80))
);

-- Indexes for efficient queries
CREATE INDEX idx_body_measurement_device_id ON body_measurement_projections(device_id);
CREATE INDEX idx_body_measurement_device_date ON body_measurement_projections(device_id, date DESC);
CREATE INDEX idx_body_measurement_measured_at ON body_measurement_projections(device_id, measured_at DESC);

-- Comments
COMMENT ON TABLE body_measurement_projections IS 'Body dimension measurements tracking (biceps, waist, chest, etc.)';
COMMENT ON COLUMN body_measurement_projections.device_id IS 'Device identifier for multi-device support';
COMMENT ON COLUMN body_measurement_projections.biceps_left_cm IS 'Left bicep circumference in cm';
COMMENT ON COLUMN body_measurement_projections.biceps_right_cm IS 'Right bicep circumference in cm';
COMMENT ON COLUMN body_measurement_projections.forearm_left_cm IS 'Left forearm circumference in cm';
COMMENT ON COLUMN body_measurement_projections.forearm_right_cm IS 'Right forearm circumference in cm';
COMMENT ON COLUMN body_measurement_projections.chest_cm IS 'Chest circumference in cm';
COMMENT ON COLUMN body_measurement_projections.waist_cm IS 'Waist circumference in cm';
COMMENT ON COLUMN body_measurement_projections.abdomen_cm IS 'Abdomen circumference in cm';
COMMENT ON COLUMN body_measurement_projections.hips_cm IS 'Hips circumference in cm';
COMMENT ON COLUMN body_measurement_projections.neck_cm IS 'Neck circumference in cm';
COMMENT ON COLUMN body_measurement_projections.shoulders_cm IS 'Shoulder width in cm';
COMMENT ON COLUMN body_measurement_projections.thigh_left_cm IS 'Left thigh circumference in cm';
COMMENT ON COLUMN body_measurement_projections.thigh_right_cm IS 'Right thigh circumference in cm';
COMMENT ON COLUMN body_measurement_projections.calf_left_cm IS 'Left calf circumference in cm';
COMMENT ON COLUMN body_measurement_projections.calf_right_cm IS 'Right calf circumference in cm';
COMMENT ON COLUMN body_measurement_projections.version IS 'Optimistic locking version';
