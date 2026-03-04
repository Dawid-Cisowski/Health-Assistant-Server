-- Nowa kolumna progowa dla strefy WARNING
ALTER TABLE marker_definitions
    ADD COLUMN ref_range_warning_high_default NUMERIC(12, 4);

ALTER TABLE lab_results
    ADD COLUMN default_ref_range_warning_high NUMERIC(12, 4);

-- 7 markerów statusowych dla badań obrazowych
INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty,
    standard_unit, ref_range_low_default, ref_range_warning_high_default, ref_range_high_default, sort_order, description)
VALUES
    ('GASTROSCOPY_OVERALL',   'Wynik gastroskopii',          'Gastroscopy Result',
     'GASTROSCOPY',   'GASTROENTEROLOGY', null, 0.5, 1.5, null, 1400, 'Ocena ogólna gastroskopii: 0=nieprawidłowe, 1=wymaga uwagi, 2=prawidłowe.'),
    ('COLONOSCOPY_OVERALL',   'Wynik kolonoskopii',          'Colonoscopy Result',
     'COLONOSCOPY',   'GASTROENTEROLOGY', null, 0.5, 1.5, null, 1410, 'Ocena ogólna kolonoskopii: 0=nieprawidłowe, 1=wymaga uwagi, 2=prawidłowe.'),
    ('USG_ABDOMINAL_OVERALL', 'Wynik USG jamy brzusznej',    'Abdominal USG Result',
     'ABDOMINAL_USG', 'RADIOLOGY',        null, 0.5, 1.5, null, 1420, 'Ocena ogólna USG jamy brzusznej: 0=nieprawidłowe, 1=wymaga uwagi, 2=prawidłowe.'),
    ('HISTOPATH_OVERALL',     'Wynik histopatologiczny',     'Histopathology Result',
     'HISTOPATHOLOGY','PATHOLOGY',         null, 0.5, 1.5, null, 1430, 'Ocena ogólna histopatologii: 0=nieprawidłowe, 1=wymaga uwagi, 2=prawidłowe.'),
    ('USG_THYROID_OVERALL',   'Wynik USG tarczycy',          'Thyroid USG Result',
     'THYROID_USG',  'ENDOCRINOLOGY',     null, 0.5, 1.5, null, 1440, 'Ocena ogólna USG tarczycy: 0=nieprawidłowe, 1=wymaga uwagi, 2=prawidłowe.'),
    ('ECHO_OVERALL',          'Wynik echa serca',            'Echocardiography Result',
     'ECHO',         'CARDIOLOGY',        null, 0.5, 1.5, null, 1450, 'Ocena ogólna echokardiografii: 0=nieprawidłowe, 1=wymaga uwagi, 2=prawidłowe.'),
    ('CHEST_XRAY_OVERALL',    'Wynik RTG klatki piersiowej', 'Chest X-Ray Result',
     'CHEST_XRAY',   'RADIOLOGY',         null, 0.5, 1.5, null, 1460, 'Ocena ogólna RTG klatki piersiowej: 0=nieprawidłowe, 1=wymaga uwagi, 2=prawidłowe.');
