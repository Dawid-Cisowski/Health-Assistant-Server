package com.healthassistant.mealimport;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealRecordedPayload;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
class MealEventMapper {

    private static final String MEAL_EVENT_TYPE = "MealRecorded.v1";

    StoreHealthEventsCommand.EventEnvelope mapToEventEnvelope(
        ExtractedMealData data, String mealId, DeviceId deviceId, Instant occurredAt
    ) {
        MealRecordedPayload payload = new MealRecordedPayload(
            data.title(),
            MealType.valueOf(data.mealType()),
            data.caloriesKcal(),
            data.proteinGrams(),
            data.fatGrams(),
            data.carbohydratesGrams(),
            HealthRating.valueOf(data.healthRating())
        );

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
