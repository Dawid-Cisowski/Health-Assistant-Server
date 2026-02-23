CREATE TABLE meal_catalog_products (
    id                    BIGSERIAL PRIMARY KEY,
    device_id             VARCHAR(255) NOT NULL,
    title                 VARCHAR(500) NOT NULL,
    normalized_title      VARCHAR(500) NOT NULL,
    meal_type             VARCHAR(20),
    calories_kcal         INTEGER NOT NULL,
    protein_grams         INTEGER NOT NULL,
    fat_grams             INTEGER NOT NULL,
    carbohydrates_grams   INTEGER NOT NULL,
    health_rating         VARCHAR(20),
    usage_count           INTEGER NOT NULL DEFAULT 1,
    last_used_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_meal_catalog_device_normalized_title UNIQUE (device_id, normalized_title)
);

CREATE INDEX idx_meal_catalog_device_id ON meal_catalog_products (device_id);
CREATE INDEX idx_meal_catalog_usage ON meal_catalog_products (device_id, usage_count DESC);
