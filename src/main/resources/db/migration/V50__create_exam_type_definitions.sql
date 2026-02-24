CREATE TABLE exam_type_definitions (
    code         VARCHAR(100) PRIMARY KEY,
    name_pl      VARCHAR(255) NOT NULL,
    name_en      VARCHAR(255),
    display_type VARCHAR(30)  NOT NULL,
    specialties  JSONB        NOT NULL DEFAULT '[]',
    category     VARCHAR(50)  NOT NULL,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    description  TEXT
);

INSERT INTO exam_type_definitions (code, name_pl, name_en, display_type, specialties, category, sort_order) VALUES
('MORPHOLOGY',     'Morfologia krwi',            'Complete Blood Count',      'LAB_RESULTS_TABLE',       '["HEMATOLOGY","GENERAL_MEDICINE"]',            'LAB_TEST',       10),
('LIPID_PANEL',    'Lipidogram',                 'Lipid Panel',               'LAB_RESULTS_TABLE',       '["CARDIOLOGY","GENERAL_MEDICINE"]',             'LAB_TEST',       20),
('THYROID',        'Hormony tarczycy',           'Thyroid Hormones',          'LAB_RESULTS_TABLE',       '["ENDOCRINOLOGY"]',                             'LAB_TEST',       30),
('GLUCOSE',        'Glukoza / cukrzyca',         'Glucose / Diabetes',        'LAB_RESULTS_TABLE',       '["DIABETOLOGY","GENERAL_MEDICINE"]',            'LAB_TEST',       40),
('URINE',          'Badanie ogólne moczu',       'Urinalysis',                'LAB_RESULTS_TABLE',       '["NEPHROLOGY","GENERAL_MEDICINE"]',             'LAB_TEST',       50),
('ALLERGY_PANEL',  'Panel alergiczny',           'Allergy Panel',             'LAB_RESULTS_TABLE',       '["ALLERGOLOGY"]',                               'LAB_TEST',       60),
('HISTOPATHOLOGY', 'Badanie histopatologiczne',  'Histopathology',            'LAB_RESULTS_TABLE',       '["GASTROENTEROLOGY","ONCOLOGY","PATHOLOGY"]',   'LAB_TEST',       70),
('LIVER_PANEL',    'Próby wątrobowe',            'Liver Function Tests',      'LAB_RESULTS_TABLE',       '["GASTROENTEROLOGY","HEPATOLOGY"]',             'LAB_TEST',       80),
('KIDNEY_PANEL',   'Funkcja nerek',              'Kidney Function Panel',     'LAB_RESULTS_TABLE',       '["NEPHROLOGY","GENERAL_MEDICINE"]',             'LAB_TEST',       90),
('ELECTROLYTES',   'Elektrolity',                'Electrolytes',              'LAB_RESULTS_TABLE',       '["GENERAL_MEDICINE","CARDIOLOGY"]',             'LAB_TEST',      100),
('INFLAMMATION',   'Markery stanu zapalnego',    'Inflammation Markers',      'LAB_RESULTS_TABLE',       '["RHEUMATOLOGY","GENERAL_MEDICINE"]',           'LAB_TEST',      110),
('VITAMINS',       'Witaminy i minerały',        'Vitamins and Minerals',     'LAB_RESULTS_TABLE',       '["GENERAL_MEDICINE","DIETETICS"]',              'LAB_TEST',      120),
('HORMONES',       'Hormony (inne)',              'Hormones (Other)',          'LAB_RESULTS_TABLE',       '["ENDOCRINOLOGY","GYNECOLOGY"]',                'LAB_TEST',      130),
('ABDOMINAL_USG',  'USG jamy brzusznej',         'Abdominal Ultrasound',      'DESCRIPTIVE_WITH_IMAGES', '["GASTROENTEROLOGY","RADIOLOGY"]',              'IMAGING',       200),
('THYROID_USG',    'USG tarczycy',               'Thyroid Ultrasound',        'DESCRIPTIVE_WITH_IMAGES', '["ENDOCRINOLOGY","RADIOLOGY"]',                 'IMAGING',       210),
('CHEST_XRAY',     'RTG klatki piersiowej',      'Chest X-Ray',               'DESCRIPTIVE_WITH_IMAGES', '["PULMONOLOGY","RADIOLOGY"]',                   'IMAGING',       220),
('ECHO',           'Echokardiogram',             'Echocardiogram',            'DESCRIPTIVE_WITH_IMAGES', '["CARDIOLOGY"]',                                'IMAGING',       230),
('ECG',            'EKG',                        'ECG',                       'DESCRIPTIVE_WITH_IMAGES', '["CARDIOLOGY"]',                                'CARDIOLOGY_EXAM',300),
('GASTROSCOPY',    'Gastroskopia',               'Gastroscopy',               'ENDOSCOPY',               '["GASTROENTEROLOGY"]',                          'ENDOSCOPY',     400),
('COLONOSCOPY',    'Kolonoskopia',               'Colonoscopy',               'ENDOSCOPY',               '["GASTROENTEROLOGY"]',                          'ENDOSCOPY',     410),
('OTHER',          'Inne badanie',               'Other Examination',         'DESCRIPTIVE_WITH_IMAGES', '["GENERAL_MEDICINE"]',                          'OTHER',         999);
