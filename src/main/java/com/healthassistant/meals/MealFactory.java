package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealRecordedPayload;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
class MealFactory {

    Optional<Meal> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof MealRecordedPayload payload)) {
            String payloadType = eventData.payload() != null
                    ? eventData.payload().getClass().getSimpleName()
                    : "null";
            log.warn("Expected MealRecordedPayload but got {}, skipping", payloadType);
            return Optional.empty();
        }

        if (payload.title() == null || payload.mealType() == null || payload.healthRating() == null) {
            log.warn("MealRecorded event missing required fields, skipping");
            return Optional.empty();
        }

        return Optional.of(Meal.create(
                eventData.deviceId().value(),
                eventData.eventId().value(),
                eventData.occurredAt(),
                payload.title(),
                payload.mealType(),
                payload.caloriesKcal(),
                payload.proteinGrams(),
                payload.fatGrams(),
                payload.carbohydratesGrams(),
                payload.healthRating()
        ));
    }

    Optional<Meal> createFromCorrectionPayload(String deviceId, Map<String, Object> payload, Instant occurredAt) {
        String title = payload.get("title") != null ? payload.get("title").toString() : null;
        String mealTypeStr = payload.get("mealType") != null ? payload.get("mealType").toString() : null;
        String healthRatingStr = payload.get("healthRating") != null ? payload.get("healthRating").toString() : null;

        if (title == null || mealTypeStr == null || healthRatingStr == null) {
            log.warn("Corrected MealRecorded payload missing required fields, skipping");
            return Optional.empty();
        }

        MealType mealType;
        HealthRating healthRating;
        try {
            mealType = MealType.valueOf(mealTypeStr);
            healthRating = HealthRating.valueOf(healthRatingStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid mealType or healthRating in correction payload: {}", e.getMessage());
            return Optional.empty();
        }

        String correctionEventId = "corrected-" + UUID.randomUUID().toString().substring(0, 8);

        return Optional.of(Meal.create(
                deviceId,
                correctionEventId,
                occurredAt,
                title,
                mealType,
                parseInteger(payload.get("caloriesKcal")),
                parseInteger(payload.get("proteinGrams")),
                parseInteger(payload.get("fatGrams")),
                parseInteger(payload.get("carbohydratesGrams")),
                healthRating
        ));
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse integer from value: {}", value);
            return null;
        }
    }
}
