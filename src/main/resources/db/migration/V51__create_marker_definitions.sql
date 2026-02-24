CREATE TABLE marker_definitions (
    code                   VARCHAR(100) PRIMARY KEY,
    name_pl                VARCHAR(255) NOT NULL,
    name_en                VARCHAR(255),
    category               VARCHAR(100) NOT NULL,
    specialty              VARCHAR(100),
    standard_unit          VARCHAR(50),
    unit_conversions       JSONB,
    ref_range_low_default  DECIMAL(12, 4),
    ref_range_high_default DECIMAL(12, 4),
    description            TEXT,
    sort_order             INTEGER NOT NULL DEFAULT 0
);

-- Morphology markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('WBC',   'Leukocyty',           'White Blood Cells',     'MORPHOLOGY', 'HEMATOLOGY', 'tys/ul',   4.0,   10.0,  10),
('RBC',   'Erytrocyty',          'Red Blood Cells',       'MORPHOLOGY', 'HEMATOLOGY', 'mln/ul',   4.2,    5.4,  20),
('HGB',   'Hemoglobina',         'Hemoglobin',            'MORPHOLOGY', 'HEMATOLOGY', 'g/dl',    12.0,   18.0,  30),
('HCT',   'Hematokryt',          'Hematocrit',            'MORPHOLOGY', 'HEMATOLOGY', '%',       37.0,   52.0,  40),
('MCV',   'MCV',                 'Mean Corpuscular Volume','MORPHOLOGY', 'HEMATOLOGY', 'fl',      80.0,  100.0,  50),
('MCH',   'MCH',                 'Mean Corpuscular Hemoglobin','MORPHOLOGY','HEMATOLOGY','pg',    27.0,   33.0,  60),
('MCHC',  'MCHC',                'MCHC',                  'MORPHOLOGY', 'HEMATOLOGY', 'g/dl',   32.0,   36.0,  70),
('PLT',   'Płytki krwi',         'Platelets',             'MORPHOLOGY', 'HEMATOLOGY', 'tys/ul', 150.0,  400.0,  80),
('NEUT',  'Neutrofile',          'Neutrophils',           'MORPHOLOGY', 'HEMATOLOGY', '%',       45.0,   70.0,  90),
('LYMPH', 'Limfocyty',           'Lymphocytes',           'MORPHOLOGY', 'HEMATOLOGY', '%',       20.0,   45.0, 100),
('MONO',  'Monocyty',            'Monocytes',             'MORPHOLOGY', 'HEMATOLOGY', '%',        2.0,   10.0, 110),
('EOS',   'Eozynofile',          'Eosinophils',           'MORPHOLOGY', 'HEMATOLOGY', '%',        1.0,    5.0, 120),
('BASO',  'Bazofile',            'Basophils',             'MORPHOLOGY', 'HEMATOLOGY', '%',        0.0,    1.0, 130);

-- Lipid panel markers (standard: mg/dL, conversion from mmol/L)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, unit_conversions, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('CHOL',  'Cholesterol całkowity','Total Cholesterol',    'LIPID_PANEL','CARDIOLOGY', 'mg/dL', '{"mmol/L": 38.67}',  null, 200.0, 200),
('LDL',   'Cholesterol LDL',     'LDL Cholesterol',      'LIPID_PANEL','CARDIOLOGY', 'mg/dL', '{"mmol/L": 38.67}',  null, 130.0, 210),
('HDL',   'Cholesterol HDL',     'HDL Cholesterol',      'LIPID_PANEL','CARDIOLOGY', 'mg/dL', '{"mmol/L": 38.67}', 40.0,  null, 220),
('TG',    'Trójglicerydy',        'Triglycerides',        'LIPID_PANEL','CARDIOLOGY', 'mg/dL', '{"mmol/L": 88.57}',  null, 150.0, 230);

-- Thyroid markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('TSH',   'TSH',                 'Thyroid Stimulating Hormone','THYROID','ENDOCRINOLOGY','uIU/mL', 0.27, 4.20, 300),
('FT3',   'Wolna trijodotyronina','Free T3',              'THYROID','ENDOCRINOLOGY', 'pmol/L',  3.10,  6.80, 310),
('FT4',   'Wolna tyroksyna',     'Free T4',              'THYROID','ENDOCRINOLOGY', 'pmol/L', 12.00, 22.00, 320);

-- Glucose / Diabetes markers (standard: mg/dL, conversion from mmol/L)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, unit_conversions, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('GLU',   'Glukoza na czczo',    'Fasting Glucose',      'GLUCOSE','DIABETOLOGY', 'mg/dL', '{"mmol/L": 18.02}', 70.0, 99.0, 400),
('HBA1C', 'Hemoglobina glikowana','HbA1c',               'GLUCOSE','DIABETOLOGY', '%',     null, null, null, 410),
('INSULIN','Insulina',           'Insulin',              'GLUCOSE','DIABETOLOGY', 'uIU/mL', null, 2.6, 24.9, 420);

-- Liver panel markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('ALT',   'Aminotransferaza alaninowa (ALT)','ALT',      'LIVER_PANEL','HEPATOLOGY', 'U/L', 0.0, 40.0, 500),
('AST',   'Aminotransferaza asparaginowa (AST)','AST',   'LIVER_PANEL','HEPATOLOGY', 'U/L', 0.0, 40.0, 510),
('GGT',   'GGT',                 'GGT',                  'LIVER_PANEL','HEPATOLOGY', 'U/L', 0.0, 55.0, 520),
('BILIR', 'Bilirubina całkowita','Total Bilirubin',      'LIVER_PANEL','HEPATOLOGY', 'mg/dL', 0.0, 1.2, 530),
('ALP',   'Fosfataza alkaliczna','ALP',                  'LIVER_PANEL','HEPATOLOGY', 'U/L', 40.0, 150.0, 540);

-- Kidney panel
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('CREAT', 'Kreatynina',          'Creatinine',           'KIDNEY_PANEL','NEPHROLOGY', 'mg/dL', 0.6, 1.2, 600),
('UREA',  'Mocznik',             'Urea / BUN',           'KIDNEY_PANEL','NEPHROLOGY', 'mg/dL', 15.0, 45.0, 610),
('UA',    'Kwas moczowy',        'Uric Acid',            'KIDNEY_PANEL','NEPHROLOGY', 'mg/dL', 2.4, 7.0, 620),
('EGFR',  'eGFR',               'Estimated GFR',        'KIDNEY_PANEL','NEPHROLOGY', 'ml/min/1.73m2', 60.0, null, 630);

-- Inflammation markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('CRP',   'Białko C-reaktywne (CRP)','C-Reactive Protein','INFLAMMATION','RHEUMATOLOGY','mg/L', null, 5.0, 700),
('ESR',   'OB (sedymentacja)',   'ESR',                  'INFLAMMATION','RHEUMATOLOGY', 'mm/h', null, 20.0, 710),
('FERR',  'Ferrytyna',           'Ferritin',             'INFLAMMATION','HEMATOLOGY', 'ng/mL', 12.0, 300.0, 720);

-- Vitamins / Minerals
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('VIT_D', 'Witamina D (25-OH)',  'Vitamin D 25-OH',      'VITAMINS','GENERAL_MEDICINE', 'ng/mL', 30.0, 100.0, 800),
('VIT_B12','Witamina B12',      'Vitamin B12',          'VITAMINS','GENERAL_MEDICINE', 'pg/mL', 200.0, 900.0, 810),
('FE',    'Żelazo',              'Iron',                 'VITAMINS','HEMATOLOGY', 'ug/dL', 60.0, 170.0, 820),
('TIBC',  'TIBC',               'Total Iron Binding Capacity','VITAMINS','HEMATOLOGY', 'ug/dL', 250.0, 370.0, 830);

-- Allergy / IgE markers (text results common)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('IGE_TOTAL','IgE całkowite',   'Total IgE',            'ALLERGY_PANEL','ALLERGOLOGY', 'kU/L', null, 100.0, 900),
('IGE_SPECIFIC','Swoiste IgE', 'Specific IgE',         'ALLERGY_PANEL','ALLERGOLOGY', 'kU/L', null, 0.35, 910);
