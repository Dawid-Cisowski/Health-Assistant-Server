package com.healthassistant.weightimport;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.payload.WeightMeasurementPayload;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import org.springframework.stereotype.Component;

@Component
class WeightEventMapper {

    private static final String WEIGHT_MEASUREMENT_EVENT_TYPE = "WeightMeasurementRecorded.v1";
    private static final String IMPORT_SOURCE = "SCALE_SCREENSHOT";

    StoreHealthEventsCommand.EventEnvelope mapToEventEnvelope(
            ExtractedWeightData data,
            String measurementId,
            IdempotencyKey idempotencyKey
    ) {
        WeightMeasurementPayload payload = new WeightMeasurementPayload(
                measurementId,
                data.measuredAt(),
                data.score(),
                data.weightKg(),
                data.bmi(),
                data.bodyFatPercent(),
                data.musclePercent(),
                data.hydrationPercent(),
                data.boneMassKg(),
                data.bmrKcal(),
                data.visceralFatLevel(),
                data.subcutaneousFatPercent(),
                data.proteinPercent(),
                data.metabolicAge(),
                data.idealWeightKg(),
                data.weightControlKg(),
                data.fatMassKg(),
                data.leanBodyMassKg(),
                data.muscleMassKg(),
                data.proteinMassKg(),
                data.bodyType(),
                IMPORT_SOURCE
        );

        return new StoreHealthEventsCommand.EventEnvelope(
                idempotencyKey,
                WEIGHT_MEASUREMENT_EVENT_TYPE,
                data.measuredAt(),
                payload
        );
    }
}
