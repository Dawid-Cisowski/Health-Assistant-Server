-- =============================================================================
-- Align standard units with what Polish laboratories actually report.
-- For each marker: change standard_unit to match real lab output, update
-- default ref ranges to the new unit, and add unit_conversions for the rare
-- case where a lab uses the legacy unit.
-- Existing lab_results records are already in the new units — no data migration needed.
-- =============================================================================

-- CREATININE: Polish labs report in µmol/L. Old standard was mg/dL (wrong for PL).
--   Conversions kept for labs that do report mg/dL: 1 mg/dL × 88.4 = µmol/L
UPDATE marker_definitions
SET standard_unit          = 'µmol/L',
    ref_range_low_default  = 62.0,
    ref_range_high_default = 110.0,
    unit_conversions       = '{"mg/dl": 88.4, "mg/dL": 88.4}'
WHERE code = 'CREAT';

-- BILIRUBIN (total): Polish labs report in µmol/L. Old standard was mg/dL.
--   1 mg/dL × 17.1 = µmol/L
UPDATE marker_definitions
SET standard_unit          = 'µmol/L',
    ref_range_low_default  = 3.4,
    ref_range_high_default = 20.5,
    unit_conversions       = '{"mg/dl": 17.1, "mg/dL": 17.1}'
WHERE code = 'BILIR';

-- URIC ACID: Polish labs report in µmol/L. Old standard was mg/dL.
--   1 mg/dL × 59.48 = µmol/L
UPDATE marker_definitions
SET standard_unit          = 'µmol/L',
    ref_range_low_default  = 202.0,
    ref_range_high_default = 416.0,
    unit_conversions       = '{"mg/dl": 59.48, "mg/dL": 59.48}'
WHERE code = 'UA';

-- FT3: Polish labs report in pg/mL. Old standard was pmol/L.
--   1 pmol/L × 0.6513 = pg/mL  (and reverse: 1 pg/mL × 1.5361 = pmol/L)
UPDATE marker_definitions
SET standard_unit          = 'pg/mL',
    ref_range_low_default  = 2.04,
    ref_range_high_default = 4.40,
    unit_conversions       = '{"pmol/L": 0.6513, "pmol/l": 0.6513}'
WHERE code = 'FT3';

-- FT4: Polish labs report in ng/dL. Old standard was pmol/L.
--   1 pmol/L × 0.07773 = ng/dL
UPDATE marker_definitions
SET standard_unit          = 'ng/dL',
    ref_range_low_default  = 0.70,
    ref_range_high_default = 1.80,
    unit_conversions       = '{"pmol/L": 0.07773, "pmol/l": 0.07773}'
WHERE code = 'FT4';

-- =============================================================================
-- Fix ref range defaults that were missing or incorrect
-- =============================================================================

-- HbA1c: was null/null which caused UNKNOWN flag for every result
--   < 5.7% = normal, 5.7-6.4% = prediabetes, >= 6.5% = diabetes
UPDATE marker_definitions
SET ref_range_high_default = 5.7
WHERE code = 'HBA1C';

-- URINE_GLUCOSE default was 0.0 (not null). This is intentional (any glucose
-- in urine is abnormal) but ref_range_low 0.0 caused LOW flag on value 0.
-- Set low to null so 0 = NORMAL and > 0 = HIGH.
UPDATE marker_definitions
SET ref_range_low_default = null
WHERE code IN ('URINE_GLUCOSE', 'URINE_KETONES', 'URINE_BILIRUBIN');
