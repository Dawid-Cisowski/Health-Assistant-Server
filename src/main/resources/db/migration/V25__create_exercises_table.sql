-- Exercise catalog table
CREATE TABLE exercises (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    primary_muscle VARCHAR(50) NOT NULL,
    muscles TEXT[] NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for filtering by muscle
CREATE INDEX idx_exercises_primary_muscle ON exercises(primary_muscle);
CREATE INDEX idx_exercises_muscles ON exercises USING GIN(muscles);

-- Seed data: 59 exercises with muscle mappings

-- KLATKA (11)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('chest_1', 'Wyciskanie sztangi leżąc (płasko)', 'Klasyczne ćwiczenie budujące ogólną masę i siłę klatki piersiowej.', 'CHEST', ARRAY['CHEST', 'SHOULDERS_FRONT', 'TRICEPS']),
('chest_2', 'Wyciskanie hantli leżąc (poziomo)', 'Wersja z hantlami pozwalająca na większy zakres ruchu.', 'CHEST', ARRAY['CHEST', 'SHOULDERS_FRONT', 'TRICEPS']),
('chest_3', 'Wyciskanie hantli na ławce skośnej (głową do góry)', 'Akcentuje górną część klatki piersiowej.', 'CHEST', ARRAY['CHEST', 'SHOULDERS_FRONT', 'TRICEPS']),
('chest_4', 'Wyciskanie sztangi na ławce skośnej (głową do góry)', 'Buduje górną część klatki piersiowej ze sztangą.', 'CHEST', ARRAY['CHEST', 'SHOULDERS_FRONT', 'TRICEPS']),
('chest_5', 'Wyciskanie hantli na ławce skośnej (głową w dół)', 'Akcentuje dolną część klatki piersiowej.', 'CHEST', ARRAY['CHEST', 'TRICEPS']),
('chest_6', 'Rozpiętki z hantlami', 'Izolowane ćwiczenie rozciągające klatę piersiową.', 'CHEST', ARRAY['CHEST']),
('chest_7', 'Rozpiętki na maszynie (pec deck)', 'Izolowane ćwiczenie na maszynie dla klatki piersiowej.', 'CHEST', ARRAY['CHEST']),
('chest_8', 'Brama na linkach (cable crossover)', 'Ćwiczenie na linkach angażujące całą klatkę piersiową.', 'CHEST', ARRAY['CHEST', 'SHOULDERS_FRONT']),
('chest_9', 'Pompki klasyczne', 'Podstawowe ćwiczenie z masą ciała dla klatki piersiowej.', 'CHEST', ARRAY['CHEST', 'SHOULDERS_FRONT', 'TRICEPS', 'ABS']),
('chest_10', 'Dipy na poręczach (klatka)', 'Dipy z pochyleniem tułowia do przodu akcentujące klatkę.', 'CHEST', ARRAY['CHEST', 'SHOULDERS_FRONT', 'TRICEPS']),
('chest_11', 'Przenoszenie hantla (pullover)', 'Ćwiczenie rozciągające klatkę i angażujące plecy.', 'CHEST', ARRAY['CHEST', 'UPPER_BACK']);

-- PLECY (11)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('back_1', 'Martwy ciąg klasyczny', 'Podstawowe ćwiczenie wielostawowe budujące siłę całego ciała.', 'LOWER_BACK', ARRAY['LOWER_BACK', 'UPPER_BACK', 'GLUTES', 'HAMSTRINGS', 'TRAPS', 'FOREARMS']),
('back_2', 'Podciąganie na drążku (szeroki chwyt)', 'Buduje szerokość pleców, akcent na najszersze.', 'UPPER_BACK', ARRAY['UPPER_BACK', 'BICEPS', 'FOREARMS']),
('back_3', 'Podciąganie na drążku (podchwyt)', 'Większe zaangażowanie bicepsów przy budowaniu pleców.', 'UPPER_BACK', ARRAY['UPPER_BACK', 'BICEPS', 'FOREARMS']),
('back_4', 'Wiosłowanie sztangą w opadzie', 'Buduje grubość i siłę środkowej części pleców.', 'UPPER_BACK', ARRAY['UPPER_BACK', 'LOWER_BACK', 'BICEPS']),
('back_5', 'Wiosłowanie hantlem jednorącz', 'Jednostronne ćwiczenie na plecy z pełnym zakresem ruchu.', 'UPPER_BACK', ARRAY['UPPER_BACK', 'BICEPS']),
('back_6', 'Ściąganie drążka wyciągu górnego', 'Alternatywa dla podciągania, buduje szerokość pleców.', 'UPPER_BACK', ARRAY['UPPER_BACK', 'BICEPS']),
('back_7', 'Przyciąganie wyciągu dolnego siedząc', 'Buduje grubość środkowej części pleców.', 'UPPER_BACK', ARRAY['UPPER_BACK', 'BICEPS']),
('back_8', 'Wiosłowanie T-Bar', 'Ciężkie ćwiczenie budujące grubość pleców.', 'UPPER_BACK', ARRAY['UPPER_BACK', 'LOWER_BACK', 'BICEPS']),
('back_9', 'Szrugsy ze sztangą/hantlami', 'Izolowane ćwiczenie na mięśnie czworoboczne (kaptur).', 'TRAPS', ARRAY['TRAPS']),
('back_10', 'Ławeczka rzymska (hyperextension)', 'Wzmacnia prostowniki grzbietu i pośladki.', 'LOWER_BACK', ARRAY['LOWER_BACK', 'GLUTES']),
('back_11', 'Narciarz na wyciągu (straight arm pulldown)', 'Izolowane ćwiczenie na najszersze mięśnie grzbietu.', 'UPPER_BACK', ARRAY['UPPER_BACK']);

-- NOGI (11)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('legs_1', 'Przysiady ze sztangą (high bar)', 'Król ćwiczeń na nogi, buduje masę i siłę.', 'QUADS', ARRAY['QUADS', 'GLUTES', 'HAMSTRINGS', 'LOWER_BACK', 'ABS']),
('legs_2', 'Przysiady przednie (front squat)', 'Większy akcent na mięśnie czworogłowe.', 'QUADS', ARRAY['QUADS', 'GLUTES', 'ABS']),
('legs_3', 'Prasa do nóg (leg press)', 'Bezpieczna alternatywa dla przysiadów na maszynie.', 'QUADS', ARRAY['QUADS', 'GLUTES', 'HAMSTRINGS']),
('legs_4', 'Wykroki ze sztangą/hantlami', 'Ćwiczenie jednostronne angażujące całe nogi.', 'QUADS', ARRAY['QUADS', 'GLUTES', 'HAMSTRINGS']),
('legs_5', 'Przysiady bułgarskie', 'Zaawansowane ćwiczenie jednostronne na nogi.', 'QUADS', ARRAY['QUADS', 'GLUTES', 'HAMSTRINGS']),
('legs_6', 'Prostowanie nóg na maszynie', 'Izolowane ćwiczenie na mięśnie czworogłowe.', 'QUADS', ARRAY['QUADS']),
('legs_7', 'Uginanie nóg na maszynie', 'Izolowane ćwiczenie na mięśnie dwugłowe uda.', 'HAMSTRINGS', ARRAY['HAMSTRINGS']),
('legs_8', 'Martwy ciąg rumuński (RDL)', 'Akcentuje mięśnie dwugłowe uda i pośladki.', 'HAMSTRINGS', ARRAY['HAMSTRINGS', 'GLUTES', 'LOWER_BACK']),
('legs_9', 'Hip thrust', 'Najlepsze ćwiczenie izolujące pośladki.', 'GLUTES', ARRAY['GLUTES', 'HAMSTRINGS']),
('legs_10', 'Wspięcia na palce stojąc', 'Buduje mięśnie łydek (brzuchate).', 'CALVES', ARRAY['CALVES']),
('legs_11', 'Wspięcia na palce siedząc', 'Akcentuje mięsień płaszczkowaty łydki.', 'CALVES', ARRAY['CALVES']);

-- BARKI (8)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('shoulders_1', 'Wyciskanie sztangi nad głowę (OHP)', 'Podstawowe ćwiczenie budujące siłę i masę barków.', 'SHOULDERS_FRONT', ARRAY['SHOULDERS_FRONT', 'SHOULDERS_SIDE', 'TRICEPS', 'ABS']),
('shoulders_2', 'Wyciskanie hantli nad głowę', 'Wersja z hantlami pozwalająca na naturalny ruch.', 'SHOULDERS_FRONT', ARRAY['SHOULDERS_FRONT', 'SHOULDERS_SIDE', 'TRICEPS']),
('shoulders_3', 'Wznosy hantli bokiem', 'Izolowane ćwiczenie na boczne partie barków.', 'SHOULDERS_SIDE', ARRAY['SHOULDERS_SIDE']),
('shoulders_4', 'Wznosy hantli w opadzie tułowia', 'Izolowane ćwiczenie na tylne partie barków.', 'SHOULDERS_REAR', ARRAY['SHOULDERS_REAR']),
('shoulders_5', 'Wznosy hantli w przód', 'Izolowane ćwiczenie na przednie partie barków.', 'SHOULDERS_FRONT', ARRAY['SHOULDERS_FRONT']),
('shoulders_6', 'Face pulls na wyciągu', 'Buduje tylne barki i poprawia postawę.', 'SHOULDERS_REAR', ARRAY['SHOULDERS_REAR', 'TRAPS']),
('shoulders_7', 'Podciąganie sztangi wzdłuż tułowia (upright row)', 'Ćwiczenie na boczne barki i kaptur.', 'SHOULDERS_SIDE', ARRAY['SHOULDERS_SIDE', 'TRAPS']),
('shoulders_8', 'Odwrotne rozpiętki na maszynie', 'Izolowane ćwiczenie na tylne partie barków.', 'SHOULDERS_REAR', ARRAY['SHOULDERS_REAR']);

-- BICEPS (6)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('biceps_1', 'Uginanie ramion ze sztangą stojąc', 'Podstawowe ćwiczenie budujące masę bicepsów.', 'BICEPS', ARRAY['BICEPS', 'FOREARMS']),
('biceps_2', 'Uginanie ramion z hantlami z supinacją', 'Pełna aktywacja bicepsa przez supinację nadgarstka.', 'BICEPS', ARRAY['BICEPS', 'FOREARMS']),
('biceps_3', 'Uginanie młotkowe (hammer curl)', 'Angażuje biceps i mięsień ramienno-promieniowy.', 'BICEPS', ARRAY['BICEPS', 'FOREARMS']),
('biceps_4', 'Uginanie na modlitewniku (preacher curl)', 'Izoluje biceps eliminując momentum.', 'BICEPS', ARRAY['BICEPS']),
('biceps_5', 'Uginanie koncentryczne', 'Maksymalna izolacja bicepsa.', 'BICEPS', ARRAY['BICEPS']),
('biceps_6', 'Uginanie na ławce skośnej', 'Rozciąga biceps w pozycji wyjściowej.', 'BICEPS', ARRAY['BICEPS']);

-- TRICEPS (6)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('triceps_1', 'Wyciskanie sztangi wąskim chwytem', 'Ciężkie ćwiczenie budujące masę tricepsów.', 'TRICEPS', ARRAY['TRICEPS', 'CHEST', 'SHOULDERS_FRONT']),
('triceps_2', 'Wyciskanie francuskie (french press)', 'Izolowane ćwiczenie na wszystkie głowy tricepsa.', 'TRICEPS', ARRAY['TRICEPS']),
('triceps_3', 'Prostowanie ramion na wyciągu górnym', 'Izolowane ćwiczenie na triceps.', 'TRICEPS', ARRAY['TRICEPS']),
('triceps_4', 'Dipy na poręczach (triceps)', 'Dipy z wyprostowanym tułowiem akcentujące triceps.', 'TRICEPS', ARRAY['TRICEPS', 'SHOULDERS_FRONT']),
('triceps_5', 'Wyciskanie hantla zza głowy', 'Ćwiczenie rozciągające długą głowę tricepsa.', 'TRICEPS', ARRAY['TRICEPS']),
('triceps_6', 'Odrzuty (kickbacks)', 'Izolowane ćwiczenie wykańczające triceps.', 'TRICEPS', ARRAY['TRICEPS']);

-- BRZUCH (6)
INSERT INTO exercises (id, name, description, primary_muscle, muscles) VALUES
('abs_1', 'Plank (deska)', 'Ćwiczenie izometryczne wzmacniające całą część rdzeniową.', 'ABS', ARRAY['ABS', 'OBLIQUES']),
('abs_2', 'Allachy (cable crunch)', 'Ćwiczenie na wyciągu górnym na mięsień prosty brzucha.', 'ABS', ARRAY['ABS']),
('abs_3', 'Unoszenie nóg w zwisie', 'Zaawansowane ćwiczenie na dolną część brzucha.', 'ABS', ARRAY['ABS', 'OBLIQUES']),
('abs_4', 'Skręty rosyjskie (russian twist)', 'Ćwiczenie na mięśnie skośne brzucha.', 'OBLIQUES', ARRAY['OBLIQUES', 'ABS']),
('abs_5', 'Spięcia brzucha (crunches)', 'Podstawowe ćwiczenie na mięsień prosty brzucha.', 'ABS', ARRAY['ABS']),
('abs_6', 'Kółko do brzucha (ab wheel rollout)', 'Zaawansowane ćwiczenie angażujące cały korpus.', 'ABS', ARRAY['ABS', 'OBLIQUES', 'SHOULDERS_FRONT']);
