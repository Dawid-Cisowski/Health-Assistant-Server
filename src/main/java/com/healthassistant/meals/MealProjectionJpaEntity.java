package com.healthassistant.meals;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "meal_projections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class MealProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "meal_number", nullable = false)
    private Integer mealNumber;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "meal_type", nullable = false)
    private String mealType;

    @Column(name = "calories_kcal", nullable = false)
    @Builder.Default
    private Integer caloriesKcal = 0;

    @Column(name = "protein_grams", nullable = false)
    @Builder.Default
    private Integer proteinGrams = 0;

    @Column(name = "fat_grams", nullable = false)
    @Builder.Default
    private Integer fatGrams = 0;

    @Column(name = "carbohydrates_grams", nullable = false)
    @Builder.Default
    private Integer carbohydratesGrams = 0;

    @Column(name = "health_rating", nullable = false)
    private String healthRating;

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

    static MealProjectionJpaEntity from(Meal meal, int mealNumber) {
        return MealProjectionJpaEntity.builder()
                .deviceId(meal.deviceId())
                .eventId(meal.eventId())
                .date(meal.date())
                .mealNumber(mealNumber)
                .occurredAt(meal.occurredAt())
                .title(meal.title())
                .mealType(meal.mealTypeName())
                .caloriesKcal(meal.caloriesKcal())
                .proteinGrams(meal.proteinGrams())
                .fatGrams(meal.fatGrams())
                .carbohydratesGrams(meal.carbohydratesGrams())
                .healthRating(meal.healthRatingName())
                .build();
    }

    void updateFrom(Meal meal) {
        this.title = meal.title();
        this.mealType = meal.mealTypeName();
        this.caloriesKcal = meal.caloriesKcal();
        this.proteinGrams = meal.proteinGrams();
        this.fatGrams = meal.fatGrams();
        this.carbohydratesGrams = meal.carbohydratesGrams();
        this.healthRating = meal.healthRatingName();
    }
}
