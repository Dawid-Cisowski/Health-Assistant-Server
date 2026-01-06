package com.healthassistant.meals;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "meal_daily_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class MealDailyProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_meal_count", nullable = false)
    @Builder.Default
    private Integer totalMealCount = 0;

    @Column(name = "breakfast_count", nullable = false)
    @Builder.Default
    private Integer breakfastCount = 0;

    @Column(name = "brunch_count", nullable = false)
    @Builder.Default
    private Integer brunchCount = 0;

    @Column(name = "lunch_count", nullable = false)
    @Builder.Default
    private Integer lunchCount = 0;

    @Column(name = "dinner_count", nullable = false)
    @Builder.Default
    private Integer dinnerCount = 0;

    @Column(name = "snack_count", nullable = false)
    @Builder.Default
    private Integer snackCount = 0;

    @Column(name = "dessert_count", nullable = false)
    @Builder.Default
    private Integer dessertCount = 0;

    @Column(name = "drink_count", nullable = false)
    @Builder.Default
    private Integer drinkCount = 0;

    @Column(name = "total_calories_kcal", nullable = false)
    @Builder.Default
    private Integer totalCaloriesKcal = 0;

    @Column(name = "total_protein_grams", nullable = false)
    @Builder.Default
    private Integer totalProteinGrams = 0;

    @Column(name = "total_fat_grams", nullable = false)
    @Builder.Default
    private Integer totalFatGrams = 0;

    @Column(name = "total_carbohydrates_grams", nullable = false)
    @Builder.Default
    private Integer totalCarbohydratesGrams = 0;

    @Column(name = "average_calories_per_meal")
    @Builder.Default
    private Integer averageCaloriesPerMeal = 0;

    @Column(name = "very_healthy_count", nullable = false)
    @Builder.Default
    private Integer veryHealthyCount = 0;

    @Column(name = "healthy_count", nullable = false)
    @Builder.Default
    private Integer healthyCount = 0;

    @Column(name = "neutral_count", nullable = false)
    @Builder.Default
    private Integer neutralCount = 0;

    @Column(name = "unhealthy_count", nullable = false)
    @Builder.Default
    private Integer unhealthyCount = 0;

    @Column(name = "very_unhealthy_count", nullable = false)
    @Builder.Default
    private Integer veryUnhealthyCount = 0;

    @Column(name = "first_meal_time")
    private Instant firstMealTime;

    @Column(name = "last_meal_time")
    private Instant lastMealTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    void updateTotals(int totalMealCount, int totalCalories, int totalProtein, int totalFat, int totalCarbs, int avgCalories) {
        this.totalMealCount = totalMealCount;
        this.totalCaloriesKcal = totalCalories;
        this.totalProteinGrams = totalProtein;
        this.totalFatGrams = totalFat;
        this.totalCarbohydratesGrams = totalCarbs;
        this.averageCaloriesPerMeal = avgCalories;
    }

    void updateMealTypeCounts(Map<String, Long> mealTypeCounts) {
        this.breakfastCount = mealTypeCounts.getOrDefault("BREAKFAST", 0L).intValue();
        this.brunchCount = mealTypeCounts.getOrDefault("BRUNCH", 0L).intValue();
        this.lunchCount = mealTypeCounts.getOrDefault("LUNCH", 0L).intValue();
        this.dinnerCount = mealTypeCounts.getOrDefault("DINNER", 0L).intValue();
        this.snackCount = mealTypeCounts.getOrDefault("SNACK", 0L).intValue();
        this.dessertCount = mealTypeCounts.getOrDefault("DESSERT", 0L).intValue();
        this.drinkCount = mealTypeCounts.getOrDefault("DRINK", 0L).intValue();
    }

    void updateHealthRatingCounts(Map<String, Long> healthRatingCounts) {
        this.veryHealthyCount = healthRatingCounts.getOrDefault("VERY_HEALTHY", 0L).intValue();
        this.healthyCount = healthRatingCounts.getOrDefault("HEALTHY", 0L).intValue();
        this.neutralCount = healthRatingCounts.getOrDefault("NEUTRAL", 0L).intValue();
        this.unhealthyCount = healthRatingCounts.getOrDefault("UNHEALTHY", 0L).intValue();
        this.veryUnhealthyCount = healthRatingCounts.getOrDefault("VERY_UNHEALTHY", 0L).intValue();
    }

    void updateMealTimes(Instant firstMealTime, Instant lastMealTime) {
        this.firstMealTime = firstMealTime;
        this.lastMealTime = lastMealTime;
    }
}
