package com.healthassistant.mealimport;

import com.healthassistant.mealimport.api.dto.ClarifyingQuestion;
import com.healthassistant.mealimport.api.dto.MealDraftUpdateRequest;
import com.healthassistant.mealimport.api.dto.QuestionAnswer;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meal_import_drafts", indexes = {
    @Index(name = "idx_meal_import_drafts_device", columnList = "device_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MealImportDraft {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @Column(name = "calories_kcal", nullable = false)
    private Integer caloriesKcal;

    @Column(name = "protein_grams", nullable = false)
    private Integer proteinGrams;

    @Column(name = "fat_grams", nullable = false)
    private Integer fatGrams;

    @Column(name = "carbohydrates_grams", nullable = false)
    private Integer carbohydratesGrams;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_rating", nullable = false, length = 20)
    private HealthRating healthRating;

    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "suggested_occurred_at", nullable = false)
    private Instant suggestedOccurredAt;

    @Column(name = "final_occurred_at")
    private Instant finalOccurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions", columnDefinition = "jsonb")
    private List<ClarifyingQuestion> questions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", columnDefinition = "jsonb")
    private List<QuestionAnswer> answers;

    @Column(name = "original_description", columnDefinition = "TEXT")
    private String originalDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DraftStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    enum DraftStatus {
        PENDING, CONFIRMED, EXPIRED
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        var now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = DraftStatus.PENDING;
        }
        if (expiresAt == null) {
            expiresAt = now.plus(24, ChronoUnit.HOURS);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    Instant getEffectiveOccurredAt() {
        return finalOccurredAt != null ? finalOccurredAt : suggestedOccurredAt;
    }

    void applyUpdate(MealDraftUpdateRequest request) {
        if (request.meal() != null) {
            var m = request.meal();
            if (m.title() != null) {
                this.title = m.title();
            }
            if (m.mealType() != null) {
                this.mealType = m.mealType();
            }
            if (m.caloriesKcal() != null) {
                this.caloriesKcal = m.caloriesKcal();
            }
            if (m.proteinGrams() != null) {
                this.proteinGrams = m.proteinGrams();
            }
            if (m.fatGrams() != null) {
                this.fatGrams = m.fatGrams();
            }
            if (m.carbohydratesGrams() != null) {
                this.carbohydratesGrams = m.carbohydratesGrams();
            }
            if (m.healthRating() != null) {
                this.healthRating = m.healthRating();
            }
        }
        if (request.occurredAt() != null) {
            this.finalOccurredAt = request.occurredAt();
        }
        if (request.answers() != null && !request.answers().isEmpty()) {
            this.answers = request.answers();
        }
    }

    void markConfirmed() {
        this.status = DraftStatus.CONFIRMED;
    }

    boolean isPending() {
        return this.status == DraftStatus.PENDING;
    }

    boolean isExpired() {
        return this.expiresAt.isBefore(Instant.now());
    }
}
