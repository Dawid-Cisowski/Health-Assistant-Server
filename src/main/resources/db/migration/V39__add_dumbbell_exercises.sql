-- Add dumbbell exercises

-- Dumbbell Squats (legs)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('legs_12', 'Przysiady ze sztangielkami', 'Przysiady z hantlami trzymanymi po bokach lub przy ramionach, bezpieczna alternatywa dla sztangi.', 'QUADS', ARRAY['QUADS', 'GLUTES', 'HAMSTRINGS', 'ABS']);

-- Dumbbell Deadlift (back/legs)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('back_12', 'Martwy ciąg ze sztangielkami', 'Martwy ciąg z hantlami, pozwala na naturalny tor ruchu i większy zakres ruchu.', 'LOWER_BACK', ARRAY['LOWER_BACK', 'UPPER_BACK', 'GLUTES', 'HAMSTRINGS', 'FOREARMS']);

-- Seated Dumbbell Shoulder Press (shoulders)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('shoulders_9', 'Wyciskanie sztangielek sprzed głowy siedząc', 'Wyciskanie hantli nad głowę w pozycji siedzącej, większa izolacja barków i stabilność.', 'SHOULDERS_FRONT', ARRAY['SHOULDERS_FRONT', 'SHOULDERS_SIDE', 'TRICEPS']);
