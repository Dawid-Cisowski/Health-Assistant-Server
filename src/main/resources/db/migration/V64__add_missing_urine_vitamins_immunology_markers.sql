-- Folic acid (missing from VITAMINS despite being commonly tested in Poland)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit,
                                ref_range_low_default, ref_range_high_default, sort_order) VALUES
('FOLIC_ACID', 'Kwas foliowy (folian)', 'Folic Acid', 'VITAMINS', 'GENERAL_MEDICINE', 'ng/mL', 3.89, 26.8, 835);

-- Urine qualitative / appearance markers (extracted by AI from lab documents but missing from definitions)
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit,
                                ref_range_low_default, ref_range_high_default, sort_order) VALUES
('URINE_COLOR',            'Barwa moczu',                   'Urine Color',              'URINE', 'NEPHROLOGY', null, null, null, 1398),
('URINE_CLARITY',          'Przejrzystość moczu',           'Urine Clarity/Appearance', 'URINE', 'NEPHROLOGY', null, null, null, 1399),
-- URINE_NITRITES: standardized code (URINE_NITRITE was a typo used by the AI in some documents)
('URINE_NITRITES',         'Azotyny w moczu',               'Urine Nitrites',           'URINE', 'NEPHROLOGY', null, null, null, 1465),
-- Sediment-specific markers (from "Mocz - osad" lab section)
('URINE_WBC_SEDIMENT',     'Leukocyty w osadzie moczu',     'WBC in Urine Sediment',    'URINE', 'NEPHROLOGY', '/ul', null, 13.2, 1462),
('URINE_EPITHELIAL_ROUND', 'Nabłonki okrągłe (osad moczu)', 'Round Epithelial Cells',   'URINE', 'NEPHROLOGY', '/ul', null, null, 1498),
('URINE_MUCUS',            'Pasma śluzu w moczu',           'Mucus in Urine',           'URINE', 'NEPHROLOGY', null, null, null, 1499);
