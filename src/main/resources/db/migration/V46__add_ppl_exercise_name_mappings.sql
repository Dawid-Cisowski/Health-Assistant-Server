-- Dodatkowe mapowania polskich nazw ćwiczeń z planu PPL

INSERT INTO exercise_name_mappings (exercise_name, catalog_id) VALUES
-- Klatka
('Wyciskanie sztangi na ławce poziomej', 'chest_1'),
('Wyciskanie sztangi płasko', 'chest_1'),
('Wyciskanie na ławce', 'chest_1'),
('Wyciskanie hantli na skosie dodatnim', 'chest_3'),
('Wyciskanie hantli skos', 'chest_3'),
('Pompki z obciążeniem', 'chest_9'),
('Pompki klasyczne z obciążeniem', 'chest_9'),
('Pompki z obciążeniem na plecach', 'chest_9'),

-- Barki
('Wyciskanie żołnierskie', 'shoulders_1'),
('Wyciskanie żołnierskie sztangi', 'shoulders_1'),
('Wyciskanie żołnierskie sztangi stojąc', 'shoulders_1'),
('Wyciskanie żołnierskie stojąc', 'shoulders_1'),

-- Triceps
('Wyciskanie francuskie leżąc', 'triceps_2'),
('Wyciskanie francuskie sztangi leżąc', 'triceps_2'),

-- Plecy
('Podciąganie na drążku', 'back_2'),
('Podciąganie szeroki chwyt', 'back_2'),
('Podciąganie szerokim nachwytem', 'back_2'),
('Podciąganie nachwyt', 'back_2'),
('Wiosłowanie sztangą w opadzie tułowia', 'back_4'),

-- Biceps
('Uginanie młotkowe', 'biceps_3'),
('Uginanie młotkowe hantlami', 'biceps_3'),
('Młotki hantlami', 'biceps_3'),
('Uginanie ramion ze sztangą', 'biceps_1'),
('Uginanie sztangą stojąc', 'biceps_1'),

-- Nogi
('Przysiady ze sztangą na plecach', 'legs_1'),
('Przysiady na plecach', 'legs_1'),
('Martwy ciąg na prostych nogach', 'legs_8'),
('Wykroki z hantlami', 'legs_4'),
('Wykroki ze sztangą', 'legs_4'),
('Wspięcia na palce', 'legs_10'),
('Wspięcia na palce ze sztangą', 'legs_10'),
('Łydki ze sztangą', 'legs_10')
ON CONFLICT (exercise_name) DO NOTHING;
