INSERT INTO marker_definitions (code, name_pl, name_en, category, specialty,
    standard_unit, ref_range_low_default, ref_range_high_default, sort_order, description)
VALUES
    ('ECG_RHYTHM', 'Rytm EKG', 'ECG Rhythm',
     'ECG', 'CARDIOLOGY', null, null, null, 1300,
     'Klasyfikacja rytmu serca z zapisu EKG.'),

    ('ECG_AVG_HR', 'Średnie tętno (EKG)', 'Average Heart Rate (ECG)',
     'ECG', 'CARDIOLOGY', 'bpm', 60.0, 100.0, 1310,
     'Średnie tętno rejestrowane podczas pomiaru EKG.'),

    ('ECG_DURATION_SEC', 'Czas trwania (EKG)', 'ECG Recording Duration',
     'ECG', 'CARDIOLOGY', 's', null, null, 1320,
     'Czas trwania zapisu EKG w sekundach.'),

    ('ECG_GAIN', 'Wzmocnienie (EKG)', 'ECG Gain',
     'ECG', 'CARDIOLOGY', null, null, null, 1330,
     'Wzmocnienie sygnału EKG.'),

    ('ECG_PAPER_SPEED', 'Prędkość papieru (EKG)', 'ECG Paper Speed',
     'ECG', 'CARDIOLOGY', null, null, null, 1340,
     'Prędkość papieru (mm/s).');
