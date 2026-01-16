package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.dto.ExistingSleepInfo;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

interface EventRepository {

    void saveAll(List<Event> events);

    Map<String, Event> findExistingEventsByIdempotencyKeys(List<IdempotencyKey> idempotencyKeys);

    Optional<ExistingSleepInfo> findExistingSleepInfo(DeviceId deviceId, Instant sleepStart);

    Optional<ExistingSleepInfo> findOverlappingSleepInfo(DeviceId deviceId, Instant sleepStart, Instant sleepEnd);

    Optional<CompensationTargetInfo> markAsDeleted(String targetEventId, String deletedByEventId, Instant deletedAt, String requestingDeviceId);

    Optional<CompensationTargetInfo> markAsSuperseded(String targetEventId, String supersededByEventId, String requestingDeviceId);

    Optional<Event> findByEventIdAndDeviceId(String eventId, String deviceId);
}
