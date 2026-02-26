-- Coagulation markers (exam type added in V59, markers missing from V51)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('INR',         'INR',                          'INR',                          'COAGULATION', 'HEMATOLOGY',  null,          0.90,  1.20, 1000),
('PT',          'Czas protrombinowy',           'Prothrombin Time (PT)',         'COAGULATION', 'HEMATOLOGY',  's',           9.80, 12.10, 1010),
('PT_PERCENT',  'Wskaźnik protrombiny (Quicka)','Prothrombin Index (Quick)',     'COAGULATION', 'HEMATOLOGY',  '%',          80.00,120.00, 1020),
('APTT',        'Czas kaolinowo-kefalinowy (APTT)','APTT',                       'COAGULATION', 'HEMATOLOGY',  's',          26.00, 36.00, 1030),
('FIBRINOGEN',  'Fibrynogen',                   'Fibrinogen',                    'COAGULATION', 'HEMATOLOGY',  'g/L',         2.00,  4.00, 1040),
('D_DIMER',     'D-dimer',                      'D-Dimer',                       'COAGULATION', 'HEMATOLOGY',  'ng/mL',       null, 500.00, 1050);

-- Electrolyte markers (CA, MG, NA, K, P missing)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('CA',   'Wapń całkowity',   'Total Calcium',   'ELECTROLYTES', 'GENERAL_MEDICINE', 'mmol/L', 2.10, 2.55, 1100),
('MG',   'Magnez',           'Magnesium',        'ELECTROLYTES', 'GENERAL_MEDICINE', 'mmol/L', 0.66, 1.07, 1110),
('NA',   'Sód',              'Sodium',           'ELECTROLYTES', 'GENERAL_MEDICINE', 'mmol/L', 136.0, 145.0, 1120),
('K',    'Potas',            'Potassium',        'ELECTROLYTES', 'GENERAL_MEDICINE', 'mmol/L', 3.50,  5.10, 1130),
('P',    'Fosfor nieorganiczny','Phosphorus',    'ELECTROLYTES', 'GENERAL_MEDICINE', 'mmol/L', 0.81,  1.45, 1140),
('CL',   'Chlorki',          'Chloride',         'ELECTROLYTES', 'GENERAL_MEDICINE', 'mmol/L', 98.0, 107.0, 1150);

-- Extended morphology markers missing from V51
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('RDW_SD',  'RDW-SD',                           'RDW-SD',                   'MORPHOLOGY', 'HEMATOLOGY', 'fl',      36.0,  47.0, 140),
('RDW_CV',  'RDW-CV',                           'RDW-CV',                   'MORPHOLOGY', 'HEMATOLOGY', '%',       11.5,  14.5, 145),
('PDW',     'PDW',                              'Platelet Distribution Width','MORPHOLOGY','HEMATOLOGY', 'fl',      10.0,  17.4, 150),
('MPV',     'MPV',                              'Mean Platelet Volume',      'MORPHOLOGY', 'HEMATOLOGY', 'fl',       7.0,  12.0, 155),
('PLCR',    'P-LCR',                            'Platelet-Large Cell Ratio', 'MORPHOLOGY', 'HEMATOLOGY', '%',       19.3,  47.1, 160),
('PCT',     'PCT',                              'Plateletcrit',              'MORPHOLOGY', 'HEMATOLOGY', '%',        0.1,   0.4, 165),
('IG_PERC', 'Niedojrzałe granulocyty IG %',    'Immature Granulocytes %',   'MORPHOLOGY', 'HEMATOLOGY', '%',       null,   1.0, 170),
('IG_ABS',  'Niedojrzałe granulocyty IG il.',  'Immature Granulocytes Abs.','MORPHOLOGY', 'HEMATOLOGY', 'tys/ul',  null,  0.07, 175),
('NRBC',    'NRBC#',                            'Nucleated Red Blood Cells', 'MORPHOLOGY', 'HEMATOLOGY', 'tys/ul',  null,  0.01, 180),
('NRBC_PERC','NRBC%',                           'Nucleated RBC %',           'MORPHOLOGY', 'HEMATOLOGY', '%',       null,  0.20, 185),
('NEUT_ABS','Neutrofile bezwzględne',           'Neutrophils Absolute',      'MORPHOLOGY', 'HEMATOLOGY', 'tys/ul',  1.90,  7.00,  95),
('LYMPH_ABS','Limfocyty bezwzględne',          'Lymphocytes Absolute',      'MORPHOLOGY', 'HEMATOLOGY', 'tys/ul',  1.50,  4.50, 105),
('MONO_ABS','Monocyty bezwzględne',            'Monocytes Absolute',        'MORPHOLOGY', 'HEMATOLOGY', 'tys/ul',  0.10,  0.90, 115),
('EOS_ABS', 'Eozynofile bezwzględne',          'Eosinophils Absolute',      'MORPHOLOGY', 'HEMATOLOGY', 'tys/ul',  0.05,  0.50, 125),
('BASO_ABS','Bazofile bezwzględne',            'Basophils Absolute',        'MORPHOLOGY', 'HEMATOLOGY', 'tys/ul',  0.00,  0.10, 135);

-- Advanced lipid markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, unit_conversions, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('NON_HDL', 'Cholesterol nie-HDL', 'Non-HDL Cholesterol', 'LIPID_PANEL', 'CARDIOLOGY', 'mg/dL', '{"mmol/L": 38.67}', null, 130.0, 240),
('LPA',     'Lipoproteina Lp(a)', 'Lipoprotein(a)',       'LIPID_PANEL', 'CARDIOLOGY', 'mg/dL', null,               null,  30.0, 250),
('APO_B',   'Apolipoproteina B',  'Apolipoprotein B',     'LIPID_PANEL', 'CARDIOLOGY', 'g/L',   null,               null,   1.0, 260),
('APO_A1',  'Apolipoproteina A1', 'Apolipoprotein A1',    'LIPID_PANEL', 'CARDIOLOGY', 'g/L',   null,              1.10, null,   265);

-- Cardiac biomarkers (exam type added in V60)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('NT_PRO_BNP',  'NT pro-BNP',   'NT pro-BNP',           'CARDIAC_BIOMARKERS', 'CARDIOLOGY', 'pg/mL', null, 125.0, 1200),
('BNP',         'BNP',          'BNP',                   'CARDIAC_BIOMARKERS', 'CARDIOLOGY', 'pg/mL', null, 100.0, 1210),
('TROPONIN_I',  'Troponina I',  'Troponin I',            'CARDIAC_BIOMARKERS', 'CARDIOLOGY', 'ng/L',  null,  19.0, 1220),
('TROPONIN_T',  'Troponina T',  'Troponin T (hs)',        'CARDIAC_BIOMARKERS', 'CARDIOLOGY', 'ng/L',  null,  14.0, 1230),
('CK_MB',       'CK-MB',        'CK-MB',                 'CARDIAC_BIOMARKERS', 'CARDIOLOGY', 'U/L',   null,  25.0, 1240);

-- Thyroid autoimmune markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('ANTI_TPO', 'Przeciwciała anty-TPO', 'Anti-TPO Antibodies', 'THYROID', 'ENDOCRINOLOGY', 'IU/mL', null,  5.61, 330),
('ANTI_TG',  'Przeciwciała anty-TG',  'Anti-TG Antibodies',  'THYROID', 'ENDOCRINOLOGY', 'IU/mL', null, 40.00, 340);

-- Hormone markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('TESTOSTERONE', 'Testosteron',     'Testosterone',    'HORMONES', 'ENDOCRINOLOGY', 'ng/dL',  240.0,  870.0, 1300),
('ESTRADIOL',    'Estradiol',       'Estradiol',       'HORMONES', 'GYNECOLOGY',    'pg/mL',  null,   null,  1310),
('PROGESTERONE', 'Progesteron',     'Progesterone',    'HORMONES', 'GYNECOLOGY',    'ng/mL',  null,   null,  1320),
('FSH',          'FSH',             'FSH',             'HORMONES', 'ENDOCRINOLOGY', 'mIU/mL', null,   null,  1330),
('LH',           'LH',              'LH',              'HORMONES', 'ENDOCRINOLOGY', 'mIU/mL', null,   null,  1340),
('PROLACTIN',    'Prolaktyna',      'Prolactin',       'HORMONES', 'ENDOCRINOLOGY', 'ng/mL',  null,   null,  1350),
('CORTISOL',     'Kortyzol',        'Cortisol',        'HORMONES', 'ENDOCRINOLOGY', 'ug/dL',  null,   null,  1360),
('DHEAS',        'DHEA-S',          'DHEA-S',          'HORMONES', 'ENDOCRINOLOGY', 'ug/dL',  null,   null,  1370),
('SHBG',         'SHBG',            'SHBG',            'HORMONES', 'ENDOCRINOLOGY', 'nmol/L', null,   null,  1380);

-- Albumin (appears as standalone test and in liver panel)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('ALBUMIN', 'Albumina', 'Albumin', 'LIVER_PANEL', 'HEPATOLOGY', 'g/L', 35.0, 50.0, 545);

-- Advanced glucose markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('HBA1C_IFCC', 'Hemoglobina glikowana (IFCC)', 'HbA1c (IFCC)', 'GLUCOSE', 'DIABETOLOGY', 'mmol/mol', 20.0, 42.0, 415),
('INSULIN_FASTING', 'Insulina na czczo', 'Fasting Insulin', 'GLUCOSE', 'DIABETOLOGY', 'uIU/mL', null, 24.9, 425);

-- Urine markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('URINE_PH',           'pH moczu',               'Urine pH',             'URINE', 'NEPHROLOGY', null,  5.0,  7.5, 1400),
('URINE_SG',           'Ciężar właściwy moczu',  'Urine Specific Gravity','URINE','NEPHROLOGY', null, 1.003, 1.030, 1410),
('URINE_PROTEIN',      'Białko w moczu',         'Urine Protein',        'URINE', 'NEPHROLOGY', 'mg/dL', null, 30.0, 1420),
('URINE_GLUCOSE',      'Glukoza w moczu',        'Urine Glucose',        'URINE', 'NEPHROLOGY', 'mg/dL', null,  0.0, 1430),
('URINE_KETONES',      'Ketony w moczu',         'Urine Ketones',        'URINE', 'NEPHROLOGY', 'mg/dL', null,  0.0, 1440),
('URINE_RBC',          'Erytrocyty w moczu',     'Urine RBC',            'URINE', 'NEPHROLOGY', '/ul',   null, 13.6, 1450),
('URINE_WBC',          'Leukocyty w moczu',      'Urine WBC',            'URINE', 'NEPHROLOGY', '/ul',   null, 13.2, 1460),
('URINE_BACTERIA',     'Bakterie w moczu',       'Urine Bacteria',       'URINE', 'NEPHROLOGY', '/ul',   null, 26.4, 1470),
('URINE_BILIRUBIN',    'Bilirubina w moczu',     'Urine Bilirubin',      'URINE', 'NEPHROLOGY', 'mg/dL', null,  0.0, 1480),
('URINE_UROBILINOGEN', 'Urobilinogen w moczu',   'Urine Urobilinogen',   'URINE', 'NEPHROLOGY', 'mg/dL', 0.0,  1.0, 1490),
('URINE_CASTS',        'Wałeczki szkliste',      'Hyaline Casts',        'URINE', 'NEPHROLOGY', '/ul',   null,  2.25, 1495),
('URINE_EPITHELIAL',   'Nabłonki płaskie',       'Squamous Epithelial Cells','URINE','NEPHROLOGY','/ul', null,  5.7, 1497);

-- Stool markers (exam type added in V60)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit, ref_range_low_default, ref_range_high_default, sort_order) VALUES
('OCCULT_BLOOD',    'Krew utajona w kale',  'Fecal Occult Blood',    'STOOL_TEST', 'GASTROENTEROLOGY', null, null, null, 1500),
('CALPROTECTIN',    'Kalprotektyna',        'Calprotectin',          'STOOL_TEST', 'GASTROENTEROLOGY', 'ug/g', null, 50.0, 1510);
