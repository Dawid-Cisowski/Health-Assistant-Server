package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.MealRecordedPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
class MealFactory {

    Optional<Meal> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof MealRecordedPayload payload)) {
            log.warn("Expected MealRecordedPayload but got {}, skipping",
                    eventData.payload().getClass().getSimpleName());
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
}
