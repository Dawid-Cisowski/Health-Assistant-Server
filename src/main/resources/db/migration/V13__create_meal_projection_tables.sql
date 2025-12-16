-- Create meal projection tables for meal tracking and analytics

-- Individual meal projections table (per meal event)
CREATE TABLE meal_projections (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    date DATE NOT NULL,
    meal_number INTEGER NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    title VARCHAR(255) NOT NULL,
    meal_type VARCHAR(20) NOT NULL,
    calories_kcal INTEGER NOT NULL DEFAULT 0,
    protein_grams INTEGER NOT NULL DEFAULT 0,
    fat_grams INTEGER NOT NULL DEFAULT 0,
    carbohydrates_grams INTEGER NOT NULL DEFAULT 0,
    health_rating VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_meal_calories CHECK (calories_kcal >= 0),
    CONSTRAINT chk_meal_protein CHECK (protein_grams >= 0),
    CONSTRAINT chk_meal_fat CHECK (fat_grams >= 0),
    CONSTRAINT chk_meal_carbs CHECK (carbohydrates_grams >= 0),
    CONSTRAINT chk_meal_type CHECK (meal_type IN ('BREAKFAST', 'BRUNCH', 'LUNCH', 'DINNER', 'SNACK', 'DESSERT', 'DRINK')),
    CONSTRAINT chk_health_rating CHECK (health_rating IN ('VERY_HEALTHY', 'HEALTHY', 'NEUTRAL', 'UNHEALTHY', 'VERY_UNHEALTHY'))
);

-- Daily meal projection table (aggregated daily totals)
CREATE TABLE meal_daily_projections (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL UNIQUE,
    total_meal_count INTEGER NOT NULL DEFAULT 0,
    breakfast_count INTEGER NOT NULL DEFAULT 0,
    brunch_count INTEGER NOT NULL DEFAULT 0,
    lunch_count INTEGER NOT NULL DEFAULT 0,
    dinner_count INTEGER NOT NULL DEFAULT 0,
    snack_count INTEGER NOT NULL DEFAULT 0,
    dessert_count INTEGER NOT NULL DEFAULT 0,
    drink_count INTEGER NOT NULL DEFAULT 0,
    total_calories_kcal INTEGER NOT NULL DEFAULT 0,
    total_protein_grams INTEGER NOT NULL DEFAULT 0,
    total_fat_grams INTEGER NOT NULL DEFAULT 0,
    total_carbohydrates_grams INTEGER NOT NULL DEFAULT 0,
    average_calories_per_meal INTEGER DEFAULT 0,
    very_healthy_count INTEGER NOT NULL DEFAULT 0,
    healthy_count INTEGER NOT NULL DEFAULT 0,
    neutral_count INTEGER NOT NULL DEFAULT 0,
    unhealthy_count INTEGER NOT NULL DEFAULT 0,
    very_unhealthy_count INTEGER NOT NULL DEFAULT 0,
    first_meal_time TIMESTAMP WITH TIME ZONE,
    last_meal_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_daily_meal_count CHECK (total_meal_count >= 0),
    CONSTRAINT chk_daily_calories CHECK (total_calories_kcal >= 0),
    CONSTRAINT chk_daily_protein CHECK (total_protein_grams >= 0),
    CONSTRAINT chk_daily_fat CHECK (total_fat_grams >= 0),
    CONSTRAINT chk_daily_carbs CHECK (total_carbohydrates_grams >= 0)
);

-- Indexes for meal_projections
CREATE INDEX idx_meal_projections_event_id ON meal_projections(event_id);
CREATE INDEX idx_meal_projections_date ON meal_projections(date DESC);
CREATE INDEX idx_meal_projections_date_meal_number ON meal_projections(date, meal_number);
CREATE INDEX idx_meal_projections_updated_at ON meal_projections(updated_at);

-- Indexes for meal_daily_projections
CREATE INDEX idx_meal_daily_date ON meal_daily_projections(date DESC);
CREATE INDEX idx_meal_daily_updated_at ON meal_daily_projections(updated_at);

-- Comments for meal_projections
COMMENT ON TABLE meal_projections IS 'Individual meal records with nutritional data';
COMMENT ON COLUMN meal_projections.event_id IS 'Reference to the source health event';
COMMENT ON COLUMN meal_projections.date IS 'Date when meal occurred (Europe/Warsaw timezone)';
COMMENT ON COLUMN meal_projections.meal_number IS 'Sequential meal number for the day (1, 2, 3...)';
COMMENT ON COLUMN meal_projections.occurred_at IS 'Timestamp when meal occurred';
COMMENT ON COLUMN meal_projections.title IS 'Name/description of the meal';
COMMENT ON COLUMN meal_projections.meal_type IS 'Type of meal (BREAKFAST, BRUNCH, LUNCH, DINNER, SNACK, DESSERT, DRINK)';
COMMENT ON COLUMN meal_projections.calories_kcal IS 'Total calories in kcal';
COMMENT ON COLUMN meal_projections.protein_grams IS 'Protein content in grams';
COMMENT ON COLUMN meal_projections.fat_grams IS 'Fat content in grams';
COMMENT ON COLUMN meal_projections.carbohydrates_grams IS 'Carbohydrates content in grams';
COMMENT ON COLUMN meal_projections.health_rating IS 'Health rating (VERY_HEALTHY, HEALTHY, NEUTRAL, UNHEALTHY, VERY_UNHEALTHY)';

-- Comments for meal_daily_projections
COMMENT ON TABLE meal_daily_projections IS 'Daily aggregated meal metrics for analytics';
COMMENT ON COLUMN meal_daily_projections.date IS 'Date for which meals are aggregated (Europe/Warsaw timezone)';
COMMENT ON COLUMN meal_daily_projections.total_meal_count IS 'Total number of meals for the day';
COMMENT ON COLUMN meal_daily_projections.breakfast_count IS 'Number of breakfast meals';
COMMENT ON COLUMN meal_daily_projections.brunch_count IS 'Number of brunch meals';
COMMENT ON COLUMN meal_daily_projections.lunch_count IS 'Number of lunch meals';
COMMENT ON COLUMN meal_daily_projections.dinner_count IS 'Number of dinner meals';
COMMENT ON COLUMN meal_daily_projections.snack_count IS 'Number of snack meals';
COMMENT ON COLUMN meal_daily_projections.dessert_count IS 'Number of dessert meals';
COMMENT ON COLUMN meal_daily_projections.drink_count IS 'Number of drink meals';
COMMENT ON COLUMN meal_daily_projections.total_calories_kcal IS 'Total calories for the day';
COMMENT ON COLUMN meal_daily_projections.total_protein_grams IS 'Total protein for the day';
COMMENT ON COLUMN meal_daily_projections.total_fat_grams IS 'Total fat for the day';
COMMENT ON COLUMN meal_daily_projections.total_carbohydrates_grams IS 'Total carbohydrates for the day';
COMMENT ON COLUMN meal_daily_projections.average_calories_per_meal IS 'Average calories per meal';
COMMENT ON COLUMN meal_daily_projections.very_healthy_count IS 'Number of very healthy meals';
COMMENT ON COLUMN meal_daily_projections.healthy_count IS 'Number of healthy meals';
COMMENT ON COLUMN meal_daily_projections.neutral_count IS 'Number of neutral meals';
COMMENT ON COLUMN meal_daily_projections.unhealthy_count IS 'Number of unhealthy meals';
COMMENT ON COLUMN meal_daily_projections.very_unhealthy_count IS 'Number of very unhealthy meals';
COMMENT ON COLUMN meal_daily_projections.first_meal_time IS 'Timestamp of first meal of the day';
COMMENT ON COLUMN meal_daily_projections.last_meal_time IS 'Timestamp of last meal of the day';
