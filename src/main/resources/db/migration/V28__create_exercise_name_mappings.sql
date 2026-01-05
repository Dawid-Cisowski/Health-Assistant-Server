-- Exercise name mappings table
-- Maps free-text exercise names (from GymRun) to catalog exercise IDs
CREATE TABLE exercise_name_mappings (
    exercise_name VARCHAR(255) PRIMARY KEY,
    catalog_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_exercise_name_mappings_catalog FOREIGN KEY (catalog_id) REFERENCES exercises(id)
);

-- Index for efficient catalog_id lookups (find all names for a given exercise)
CREATE INDEX idx_exercise_name_mappings_catalog_id ON exercise_name_mappings(catalog_id);

-- Initial data population: map catalog exercise names to themselves
-- This covers cases where GymRun uses the exact catalog name
INSERT INTO exercise_name_mappings (exercise_name, catalog_id)
SELECT name, id FROM exercises;

-- Common name variations that map to catalog exercises
-- These are typical shortened or alternative names users might use in GymRun

-- Chest variations
INSERT INTO exercise_name_mappings (exercise_name, catalog_id) VALUES
('Wyciskanie sztangi leżąc', 'chest_1'),
('Wyciskanie leżąc', 'chest_1'),
('Bench press', 'chest_1'),
('Flat bench press', 'chest_1'),
('Wyciskanie hantli', 'chest_2'),
('Dumbbell press', 'chest_2'),
('Wyciskanie skos dodatni', 'chest_3'),
('Incline dumbbell press', 'chest_3'),
('Rozpiętki', 'chest_6'),
('Flyes', 'chest_6'),
('Rozpiętki na maszynie', 'chest_7'),
('Pec deck', 'chest_7'),
('Brama', 'chest_8'),
('Cable crossover', 'chest_8'),
('Pompki', 'chest_9'),
('Push ups', 'chest_9'),
('Dipy klatka', 'chest_10'),
('Chest dips', 'chest_10'),
('Pullover', 'chest_11')
ON CONFLICT (exercise_name) DO NOTHING;

-- Back variations
INSERT INTO exercise_name_mappings (exercise_name, catalog_id) VALUES
('Martwy ciąg', 'back_1'),
('Deadlift', 'back_1'),
('Podciąganie', 'back_2'),
('Pull ups', 'back_2'),
('Podciąganie podchwyt', 'back_3'),
('Chin ups', 'back_3'),
('Wiosłowanie sztangą', 'back_4'),
('Barbell row', 'back_4'),
('Wiosłowanie hantlem', 'back_5'),
('Dumbbell row', 'back_5'),
('Ściąganie drążka', 'back_6'),
('Lat pulldown', 'back_6'),
('Wyciąg dolny siedząc', 'back_7'),
('Seated row', 'back_7'),
('Cable row', 'back_7'),
('T-Bar', 'back_8'),
('Szrugsy', 'back_9'),
('Shrugs', 'back_9'),
('Hyperextension', 'back_10'),
('Ławeczka rzymska', 'back_10')
ON CONFLICT (exercise_name) DO NOTHING;

-- Legs variations
INSERT INTO exercise_name_mappings (exercise_name, catalog_id) VALUES
('Przysiady', 'legs_1'),
('Squat', 'legs_1'),
('Back squat', 'legs_1'),
('Front squat', 'legs_2'),
('Przysiady przednie', 'legs_2'),
('Prasa do nóg', 'legs_3'),
('Leg press', 'legs_3'),
('Wykroki', 'legs_4'),
('Lunges', 'legs_4'),
('Przysiady bułgarskie', 'legs_5'),
('Bulgarian split squat', 'legs_5'),
('Prostowanie nóg', 'legs_6'),
('Leg extension', 'legs_6'),
('Uginanie nóg', 'legs_7'),
('Leg curl', 'legs_7'),
('RDL', 'legs_8'),
('Rumuński martwy ciąg', 'legs_8'),
('Hip thrust', 'legs_9'),
('Łydki stojąc', 'legs_10'),
('Calf raise', 'legs_10'),
('Łydki siedząc', 'legs_11')
ON CONFLICT (exercise_name) DO NOTHING;

-- Shoulders variations
INSERT INTO exercise_name_mappings (exercise_name, catalog_id) VALUES
('OHP', 'shoulders_1'),
('Overhead press', 'shoulders_1'),
('Wyciskanie nad głowę', 'shoulders_1'),
('Military press', 'shoulders_1'),
('Wyciskanie hantli nad głowę', 'shoulders_2'),
('Shoulder press', 'shoulders_2'),
('Wznosy bokiem', 'shoulders_3'),
('Lateral raise', 'shoulders_3'),
('Wznosy w opadzie', 'shoulders_4'),
('Rear delt fly', 'shoulders_4'),
('Wznosy w przód', 'shoulders_5'),
('Front raise', 'shoulders_5'),
('Face pulls', 'shoulders_6'),
('Upright row', 'shoulders_7'),
('Odwrotne rozpiętki', 'shoulders_8'),
('Reverse fly', 'shoulders_8')
ON CONFLICT (exercise_name) DO NOTHING;

-- Biceps variations
INSERT INTO exercise_name_mappings (exercise_name, catalog_id) VALUES
('Uginanie sztangą', 'biceps_1'),
('Barbell curl', 'biceps_1'),
('Biceps curl', 'biceps_1'),
('Uginanie hantlami', 'biceps_2'),
('Dumbbell curl', 'biceps_2'),
('Hammer curl', 'biceps_3'),
('Młotki', 'biceps_3'),
('Preacher curl', 'biceps_4'),
('Modlitewnik', 'biceps_4'),
('Uginanie koncentryczne', 'biceps_5'),
('Concentration curl', 'biceps_5'),
('Uginanie na ławce skośnej', 'biceps_6'),
('Incline curl', 'biceps_6')
ON CONFLICT (exercise_name) DO NOTHING;

-- Triceps variations
INSERT INTO exercise_name_mappings (exercise_name, catalog_id) VALUES
('Wyciskanie wąskim chwytem', 'triceps_1'),
('Close grip bench press', 'triceps_1'),
('French press', 'triceps_2'),
('Skull crusher', 'triceps_2'),
('Prostowanie na wyciągu', 'triceps_3'),
('Triceps pushdown', 'triceps_3'),
('Rope pushdown', 'triceps_3'),
('Dipy triceps', 'triceps_4'),
('Triceps dips', 'triceps_4'),
('Wyciskanie hantla zza głowy', 'triceps_5'),
('Overhead triceps extension', 'triceps_5'),
('Kickbacks', 'triceps_6'),
('Odrzuty', 'triceps_6')
ON CONFLICT (exercise_name) DO NOTHING;

-- Abs variations
INSERT INTO exercise_name_mappings (exercise_name, catalog_id) VALUES
('Plank', 'abs_1'),
('Deska', 'abs_1'),
('Allachy', 'abs_2'),
('Cable crunch', 'abs_2'),
('Unoszenie nóg', 'abs_3'),
('Hanging leg raise', 'abs_3'),
('Russian twist', 'abs_4'),
('Skręty rosyjskie', 'abs_4'),
('Crunches', 'abs_5'),
('Spięcia', 'abs_5'),
('Ab wheel', 'abs_6'),
('Kółko do brzucha', 'abs_6')
ON CONFLICT (exercise_name) DO NOTHING;
