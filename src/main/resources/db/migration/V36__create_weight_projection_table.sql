-- Create weight measurement projection table for body composition tracking
CREATE TABLE weight_measurement_projections (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    measurement_id VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Core metrics
    score INTEGER,
    weight_kg DECIMAL(6,2) NOT NULL,
    bmi DECIMAL(5,2),
    body_fat_percent DECIMAL(5,2),
    muscle_percent DECIMAL(5,2),
    hydration_percent DECIMAL(5,2),
    bone_mass_kg DECIMAL(5,2),
    bmr_kcal INTEGER,
    visceral_fat_level INTEGER,
    subcutaneous_fat_percent DECIMAL(5,2),
    protein_percent DECIMAL(5,2),
    metabolic_age INTEGER,
    ideal_weight_kg DECIMAL(6,2),
    weight_control_kg DECIMAL(6,2),
    fat_mass_kg DECIMAL(6,2),
    lean_body_mass_kg DECIMAL(6,2),
    muscle_mass_kg DECIMAL(6,2),
    protein_mass_kg DECIMAL(6,2),
    body_type VARCHAR(50),
    source VARCHAR(100),

    -- Timestamps and versioning
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_weight_kg CHECK (weight_kg > 0 AND weight_kg <= 500),
    CONSTRAINT chk_score CHECK (score IS NULL OR (score >= 0 AND score <= 100)),
    CONSTRAINT chk_bmi CHECK (bmi IS NULL OR (bmi > 0 AND bmi <= 100)),
    CONSTRAINT chk_body_fat_percent CHECK (body_fat_percent IS NULL OR (body_fat_percent >= 0 AND body_fat_percent <= 100)),
    CONSTRAINT chk_muscle_percent CHECK (muscle_percent IS NULL OR (muscle_percent >= 0 AND muscle_percent <= 100)),
    CONSTRAINT chk_hydration_percent CHECK (hydration_percent IS NULL OR (hydration_percent >= 0 AND hydration_percent <= 100)),
    CONSTRAINT chk_visceral_fat_level CHECK (visceral_fat_level IS NULL OR (visceral_fat_level >= 1 AND visceral_fat_level <= 59)),
    CONSTRAINT chk_protein_percent CHECK (protein_percent IS NULL OR (protein_percent >= 0 AND protein_percent <= 100)),
    CONSTRAINT chk_subcutaneous_fat_percent CHECK (subcutaneous_fat_percent IS NULL OR (subcutaneous_fat_percent >= 0 AND subcutaneous_fat_percent <= 100))
);

-- Indexes for efficient queries
CREATE INDEX idx_weight_device_id ON weight_measurement_projections(device_id);
CREATE INDEX idx_weight_device_date ON weight_measurement_projections(device_id, date DESC);
CREATE INDEX idx_weight_measured_at ON weight_measurement_projections(device_id, measured_at DESC);

-- Comments
COMMENT ON TABLE weight_measurement_projections IS 'Body composition measurements from smart scales';
COMMENT ON COLUMN weight_measurement_projections.device_id IS 'Device identifier for multi-device support';
COMMENT ON COLUMN weight_measurement_projections.score IS 'Overall health score (Wynik) 0-100';
COMMENT ON COLUMN weight_measurement_projections.weight_kg IS 'Body weight in kilograms (Waga)';
COMMENT ON COLUMN weight_measurement_projections.bmi IS 'Body Mass Index';
COMMENT ON COLUMN weight_measurement_projections.body_fat_percent IS 'Body Fat Rate percentage (BFR)';
COMMENT ON COLUMN weight_measurement_projections.muscle_percent IS 'Muscle percentage (Miesnie)';
COMMENT ON COLUMN weight_measurement_projections.hydration_percent IS 'Hydration percentage (Nawodnienie)';
COMMENT ON COLUMN weight_measurement_projections.bone_mass_kg IS 'Bone mass in kg (Masa kostna)';
COMMENT ON COLUMN weight_measurement_projections.bmr_kcal IS 'Basal Metabolic Rate in kcal';
COMMENT ON COLUMN weight_measurement_projections.visceral_fat_level IS 'Visceral fat level 1-59 (Tluszcz trzewny)';
COMMENT ON COLUMN weight_measurement_projections.subcutaneous_fat_percent IS 'Subcutaneous fat percentage (Tluszcz podskórny)';
COMMENT ON COLUMN weight_measurement_projections.protein_percent IS 'Protein level percentage (Poziom bialka)';
COMMENT ON COLUMN weight_measurement_projections.metabolic_age IS 'Metabolic/body age in years (Wiek ciala)';
COMMENT ON COLUMN weight_measurement_projections.ideal_weight_kg IS 'Ideal weight in kg (Standardowa waga)';
COMMENT ON COLUMN weight_measurement_projections.weight_control_kg IS 'Weight control difference (Kontrola wagi)';
COMMENT ON COLUMN weight_measurement_projections.fat_mass_kg IS 'Fat mass in kg (Tluszcz masa)';
COMMENT ON COLUMN weight_measurement_projections.lean_body_mass_kg IS 'Lean body mass in kg (Waga bez tluszczu)';
COMMENT ON COLUMN weight_measurement_projections.muscle_mass_kg IS 'Muscle mass in kg (Masa miesni)';
COMMENT ON COLUMN weight_measurement_projections.protein_mass_kg IS 'Protein mass in kg (Masa bialkowa)';
COMMENT ON COLUMN weight_measurement_projections.body_type IS 'Body type category (Typ ciala)';
COMMENT ON COLUMN weight_measurement_projections.version IS 'Optimistic locking version';
