-- =============================================================================
-- V68: Add missing marker definitions required for Health Pillars feature
-- =============================================================================

INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty, standard_unit,
                                ref_range_low_default, ref_range_high_default, sort_order, description)
VALUES
('HOMOCYSTEINE', 'Homocysteina', 'Homocysteine',
 'CARDIAC_BIOMARKERS', 'CARDIOLOGY', 'µmol/L', null, 15.0, 1250,
 'Aminokwas pośredni; podwyższone stężenie zwiększa ryzyko chorób sercowo-naczyniowych i zakrzepicy.'),

('AMYLASE', 'Amylaza', 'Amylase',
 'LIVER_PANEL', 'GASTROENTEROLOGY', 'U/L', 25.0, 125.0, 620,
 'Enzym trawiący skrobię; wzrost wskazuje na zapalenie trzustki lub ślinianek.'),

('LIPASE', 'Lipaza', 'Lipase',
 'LIVER_PANEL', 'GASTROENTEROLOGY', 'U/L', 13.0, 60.0, 625,
 'Enzym trzustkowy trawiący tłuszcze; czuły marker ostrego zapalenia trzustki.'),

('ZINC', 'Cynk', 'Zinc',
 'ELECTROLYTES', 'GENERAL_MEDICINE', 'µmol/L', 10.7, 22.9, 1160,
 'Pierwiastek śladowy niezbędny dla odporności, gojenia ran i prawidłowego wzrostu.'),

('COPPER', 'Miedź', 'Copper',
 'ELECTROLYTES', 'GENERAL_MEDICINE', 'µmol/L', 11.0, 22.0, 1170,
 'Pierwiastek śladowy uczestniczący w syntezie hemoglobiny i funkcjonowaniu układu nerwowego.');
