package com.healthassistant.mealcatalog;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "meal_catalog_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CatalogProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "normalized_title", nullable = false, length = 500)
    private String normalizedTitle;

    @Column(name = "meal_type", length = 20)
    private String mealType;

    @Column(name = "calories_kcal", nullable = false)
    private Integer caloriesKcal;

    @Column(name = "protein_grams", nullable = false)
    private Integer proteinGrams;

    @Column(name = "fat_grams", nullable = false)
    private Integer fatGrams;

    @Column(name = "carbohydrates_grams", nullable = false)
    private Integer carbohydratesGrams;

    @Column(name = "health_rating", length = 20)
    private String healthRating;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    CatalogProduct(String deviceId, String title, String mealType,
                   Integer caloriesKcal, Integer proteinGrams, Integer fatGrams,
                   Integer carbohydratesGrams, String healthRating) {
        var now = Instant.now();
        this.deviceId = deviceId;
        this.title = title;
        this.normalizedTitle = normalizeTitle(title);
        this.mealType = mealType;
        this.caloriesKcal = caloriesKcal;
        this.proteinGrams = proteinGrams;
        this.fatGrams = fatGrams;
        this.carbohydratesGrams = carbohydratesGrams;
        this.healthRating = healthRating;
        this.usageCount = 1;
        this.lastUsedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void recordUsage(String mealType, Integer caloriesKcal, Integer proteinGrams,
                     Integer fatGrams, Integer carbohydratesGrams, String healthRating) {
        var now = Instant.now();
        this.usageCount = this.usageCount + 1;
        this.lastUsedAt = now;
        this.updatedAt = now;
        if (mealType != null) {
            this.mealType = mealType;
        }
        if (caloriesKcal != null) {
            this.caloriesKcal = caloriesKcal;
        }
        if (proteinGrams != null) {
            this.proteinGrams = proteinGrams;
        }
        if (fatGrams != null) {
            this.fatGrams = fatGrams;
        }
        if (carbohydratesGrams != null) {
            this.carbohydratesGrams = carbohydratesGrams;
        }
        if (healthRating != null) {
            this.healthRating = healthRating;
        }
    }

    static String normalizeTitle(String title) {
        return title.strip().toLowerCase(Locale.ROOT);
    }
}
