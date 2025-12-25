package com.healthassistant.sleepimport;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.payload.SleepSessionPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import org.springframework.stereotype.Component;

@Component
class SleepEventMapper {

    private static final String SLEEP_SOURCE = "OHEALTH_SCREENSHOT";
    private static final String SLEEP_EVENT_TYPE = "SleepSessionRecorded.v1";

    StoreHealthEventsCommand.EventEnvelope mapToEventEnvelope(
            ExtractedSleepData data,
            String sleepId,
            IdempotencyKey idempotencyKey,
            DeviceId deviceId
    ) {
        SleepSessionPayload payload = new SleepSessionPayload(
                sleepId,
                data.sleepStart(),
                data.sleepEnd(),
                data.totalSleepMinutes(),
                SLEEP_SOURCE,
                data.phases().lightSleepMinutes(),
                data.phases().deepSleepMinutes(),
                data.phases().remSleepMinutes(),
                data.phases().awakeMinutes(),
                data.sleepScore(),
                SLEEP_SOURCE
        );

        return new StoreHealthEventsCommand.EventEnvelope(
                idempotencyKey,
                SLEEP_EVENT_TYPE,
                data.sleepEnd(),
                payload
        );
    }
}
