package com.healthassistant.weight;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.WeightMeasurementPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
class WeightMeasurementFactory {

    Optional<WeightMeasurement> createFromEvent(StoredEventData eventData) {
        if (!(eventData.payload() instanceof WeightMeasurementPayload payload)) {
            log.warn("Expected WeightMeasurementPayload but got {}, skipping",
                    eventData.payload().getClass().getSimpleName());
            return Optional.empty();
        }

        if (payload.weightKg() == null || payload.measuredAt() == null) {
            log.warn("WeightMeasurement event missing required fields (weightKg or measuredAt), skipping");
            return Optional.empty();
        }

        return Optional.of(WeightMeasurement.create(
                eventData.deviceId().value(),
                eventData.eventId().value(),
                payload.measurementId(),
                payload.measuredAt(),
                payload.score(),
                payload.weightKg(),
                payload.bmi(),
                payload.bodyFatPercent(),
                payload.musclePercent(),
                payload.hydrationPercent(),
                payload.boneMassKg(),
                payload.bmrKcal(),
                payload.visceralFatLevel(),
                payload.subcutaneousFatPercent(),
                payload.proteinPercent(),
                payload.metabolicAge(),
                payload.idealWeightKg(),
                payload.weightControlKg(),
                payload.fatMassKg(),
                payload.leanBodyMassKg(),
                payload.muscleMassKg(),
                payload.proteinMassKg(),
                payload.bodyType(),
                payload.source()
        ));
    }
}
