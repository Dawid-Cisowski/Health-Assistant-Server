package com.healthassistant.mealimport;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import com.healthassistant.mealimport.dto.ExtractedMealData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
class MealEventMapper {

    private static final String MEAL_EVENT_TYPE = "MealRecorded.v1";

    StoreHealthEventsCommand.EventEnvelope mapToEventEnvelope(
        ExtractedMealData data, String mealId, DeviceId deviceId, Instant occurredAt
    ) {
        Map<String, Object> payload = new HashMap<>();

        payload.put("title", data.title());
        payload.put("mealType", data.mealType());
        payload.put("caloriesKcal", data.caloriesKcal());
        payload.put("proteinGrams", data.proteinGrams());
        payload.put("fatGrams", data.fatGrams());
        payload.put("carbohydratesGrams", data.carbohydratesGrams());
        payload.put("healthRating", data.healthRating());

        // Generate unique idempotency key using mealId (UUID-based, so always unique)
        String idempotencyKeyValue = deviceId.value() + "|meal|" + mealId;
        IdempotencyKey idempotencyKey = IdempotencyKey.of(idempotencyKeyValue);

        log.debug("Created meal event envelope: mealId={}, title={}, mealType={}, calories={}, idempotencyKey={}",
            mealId, data.title(), data.mealType(), data.caloriesKcal(), idempotencyKeyValue);

        return new StoreHealthEventsCommand.EventEnvelope(
            idempotencyKey,
            MEAL_EVENT_TYPE,
            occurredAt,
            payload
        );
    }
}
