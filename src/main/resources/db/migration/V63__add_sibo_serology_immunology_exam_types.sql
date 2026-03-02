-- Fix HISTOPATHOLOGY: it produces descriptive reports, not tabular lab results
UPDATE exam_type_definitions SET display_type = 'DESCRIPTIVE_WITH_IMAGES' WHERE code = 'HISTOPATHOLOGY';

-- SIBO/IMO hydrogen breath test
INSERT INTO exam_type_definitions (code, name_pl, name_en, display_type, specialties, category, sort_order) VALUES
('SIBO', 'Test oddechowy (SIBO/IMO)', 'Hydrogen Breath Test (SIBO/IMO)', 'LAB_RESULTS_TABLE',
 '["GASTROENTEROLOGY","GENERAL_MEDICINE"]', 'LAB_TEST', 160);

-- Serology: antibody tests (ASO, anti-tTG, etc.)
INSERT INTO exam_type_definitions (code, name_pl, name_en, display_type, specialties, category, sort_order) VALUES
('SEROLOGY', 'Badania serologiczne', 'Serology Panel', 'LAB_RESULTS_TABLE',
 '["RHEUMATOLOGY","GASTROENTEROLOGY","GENERAL_MEDICINE"]', 'LAB_TEST', 170);

-- Immunology: immunoglobulin quantification (IgA, IgG, IgM)
INSERT INTO exam_type_definitions (code, name_pl, name_en, display_type, specialties, category, sort_order) VALUES
('IMMUNOLOGY', 'Immunoglobuliny', 'Immunoglobulins', 'LAB_RESULTS_TABLE',
 '["IMMUNOLOGY","RHEUMATOLOGY","GENERAL_MEDICINE"]', 'LAB_TEST', 175);

-- SIBO breath test markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit,
                                ref_range_low_default, ref_range_high_default, sort_order) VALUES
('H2_START',     'Stężenie H₂ wyjściowe',   'Baseline H2 Concentration', 'SIBO', 'GASTROENTEROLOGY', 'ppm', 0.0,  10.0, 1600),
('H2_DELTA',     'Wzrost stężenia H₂',       'H2 Rise (delta)',           'SIBO', 'GASTROENTEROLOGY', 'ppm', null, 20.0, 1610),
('CH4_MAX',      'Maks. stężenie CH₄',       'Peak CH4 Concentration',    'SIBO', 'GASTROENTEROLOGY', 'ppm', null, 10.0, 1620),
('H2_CH4_DELTA', 'Wzrost sumy H₂+CH₄',      'H2+CH4 Combined Rise',      'SIBO', 'GASTROENTEROLOGY', 'ppm', null, 15.0, 1630);

-- Serology markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit,
                                ref_range_low_default, ref_range_high_default, sort_order) VALUES
('ASO',          'Antystreptolizyna O (ASO)',                      'Antistreptolysin O (ASO)',       'SEROLOGY', 'RHEUMATOLOGY',    'IU/mL', 0.0, 200.0, 1700),
('ANTY_TGT_IGA', 'Przeciwciała anty-tGT w kl. IgA (tTG-IgA)',    'Anti-tTG IgA (Celiac disease)',  'SEROLOGY', 'GASTROENTEROLOGY','RU/mL', null,  20.0, 1710);

-- Immunoglobulin markers
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit,
                                ref_range_low_default, ref_range_high_default, sort_order) VALUES
('IGA', 'Immunoglobuliny kl. A (IgA)', 'Immunoglobulin A (IgA)', 'IMMUNOLOGY', 'IMMUNOLOGY', 'g/L', 0.70,  4.00, 1720),
('IGG', 'Immunoglobuliny kl. G (IgG)', 'Immunoglobulin G (IgG)', 'IMMUNOLOGY', 'IMMUNOLOGY', 'g/L', 7.00, 16.00, 1730),
('IGM', 'Immunoglobuliny kl. M (IgM)', 'Immunoglobulin M (IgM)', 'IMMUNOLOGY', 'IMMUNOLOGY', 'g/L', 0.40,  2.30, 1740);
