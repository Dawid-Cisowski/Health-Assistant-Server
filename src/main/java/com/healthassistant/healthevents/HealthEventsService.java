package com.healthassistant.healthevents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.EventData;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.ActivityEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.AllEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.CaloriesEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.MealsEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.SleepEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.StepsEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.WorkoutEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class HealthEventsService implements HealthEventsFacade {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private static final String STEPS_BUCKETED_V1 = "StepsBucketedRecorded.v1";
    private static final String WORKOUT_V1 = "WorkoutRecorded.v1";
    private static final String SLEEP_SESSION_V1 = "SleepSessionRecorded.v1";
    private static final String ACTIVE_MINUTES_V1 = "ActiveMinutesRecorded.v1";
    private static final String WALKING_SESSION_V1 = "WalkingSessionRecorded.v1";
    private static final String HEART_RATE_V1 = "HeartRateSummaryRecorded.v1";
    private static final String ACTIVE_CALORIES_V1 = "ActiveCaloriesBurnedRecorded.v1";
    private static final String MEAL_V1 = "MealRecorded.v1";

    private final StoreHealthEventsCommandHandler commandHandler;
    private final ApplicationEventPublisher eventPublisher;
    private final HealthEventJpaRepository healthEventJpaRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public StoreHealthEventsResult storeHealthEvents(StoreHealthEventsCommand command) {
        log.debug("Storing {} health events", command.events().size());

        StoreHealthEventsResult result = commandHandler.handle(command);

        if (!result.affectedDates().isEmpty()) {
            publishTypedEvents(result);
        }

        return result;
    }

    private void publishTypedEvents(StoreHealthEventsResult result) {
        Map<String, List<StoredEventData>> eventsByType = result.storedEvents().stream()
                .collect(Collectors.groupingBy(e -> e.eventType().value()));

        publishStepsEvents(eventsByType);
        publishWorkoutEvents(eventsByType);
        publishSleepEvents(eventsByType);
        publishActivityEvents(eventsByType);
        publishCaloriesEvents(eventsByType);
        publishMealsEvents(eventsByType);

        publishAllEventsStoredEvent(result);
    }

    private void publishStepsEvents(Map<String, List<StoredEventData>> eventsByType) {
        List<StoredEventData> events = eventsByType.getOrDefault(STEPS_BUCKETED_V1, List.of());
        if (!events.isEmpty()) {
            Set<LocalDate> dates = extractAffectedDates(events);
            log.info("Publishing StepsEventsStoredEvent with {} events for {} dates", events.size(), dates.size());
            eventPublisher.publishEvent(new StepsEventsStoredEvent(events, dates));
        }
    }

    private void publishWorkoutEvents(Map<String, List<StoredEventData>> eventsByType) {
        List<StoredEventData> events = eventsByType.getOrDefault(WORKOUT_V1, List.of());
        if (!events.isEmpty()) {
            Set<LocalDate> dates = extractAffectedDates(events);
            log.info("Publishing WorkoutEventsStoredEvent with {} events for {} dates", events.size(), dates.size());
            eventPublisher.publishEvent(new WorkoutEventsStoredEvent(events, dates));
        }
    }

    private void publishSleepEvents(Map<String, List<StoredEventData>> eventsByType) {
        List<StoredEventData> events = eventsByType.getOrDefault(SLEEP_SESSION_V1, List.of());
        if (!events.isEmpty()) {
            Set<LocalDate> dates = extractAffectedDates(events);
            log.info("Publishing SleepEventsStoredEvent with {} events for {} dates", events.size(), dates.size());
            eventPublisher.publishEvent(new SleepEventsStoredEvent(events, dates));
        }
    }

    private void publishActivityEvents(Map<String, List<StoredEventData>> eventsByType) {
        List<StoredEventData> activityEvents = new java.util.ArrayList<>(eventsByType.getOrDefault(ACTIVE_MINUTES_V1, List.of()));
        if (!activityEvents.isEmpty()) {
            Set<LocalDate> dates = extractAffectedDates(activityEvents);
            log.info("Publishing ActivityEventsStoredEvent with {} events for {} dates", activityEvents.size(), dates.size());
            eventPublisher.publishEvent(new ActivityEventsStoredEvent(activityEvents, dates));
        }
    }

    private void publishCaloriesEvents(Map<String, List<StoredEventData>> eventsByType) {
        List<StoredEventData> events = eventsByType.getOrDefault(ACTIVE_CALORIES_V1, List.of());
        if (!events.isEmpty()) {
            Set<LocalDate> dates = extractAffectedDates(events);
            log.info("Publishing CaloriesEventsStoredEvent with {} events for {} dates", events.size(), dates.size());
            eventPublisher.publishEvent(new CaloriesEventsStoredEvent(events, dates));
        }
    }

    private void publishMealsEvents(Map<String, List<StoredEventData>> eventsByType) {
        List<StoredEventData> events = eventsByType.getOrDefault(MEAL_V1, List.of());
        if (!events.isEmpty()) {
            Set<LocalDate> dates = extractAffectedDates(events);
            log.info("Publishing MealsEventsStoredEvent with {} events for {} dates", events.size(), dates.size());
            eventPublisher.publishEvent(new MealsEventsStoredEvent(events, dates));
        }
    }

    private void publishAllEventsStoredEvent(StoreHealthEventsResult result) {
        if (result.storedEvents().isEmpty()) {
            return;
        }

        String deviceId = result.storedEvents().getFirst().deviceId().value();
        Set<String> eventTypeStrings = result.eventTypes().stream()
                .map(EventType::value)
                .collect(Collectors.toSet());

        log.info("Publishing AllEventsStoredEvent for device {} with {} affected dates, {} event types, {} stored events",
                deviceId, result.affectedDates().size(), eventTypeStrings.size(), result.storedEvents().size());

        eventPublisher.publishEvent(
                new AllEventsStoredEvent(deviceId, result.storedEvents(), result.affectedDates(), eventTypeStrings)
        );
    }

    private Set<LocalDate> extractAffectedDates(List<StoredEventData> events) {
        return events.stream()
                .map(StoredEventData::occurredAt)
                .filter(Objects::nonNull)
                .map(this::toLocalDate)
                .collect(Collectors.toSet());
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant.atZone(POLAND_ZONE).toLocalDate();
    }

    @Override
    public List<EventData> findEventsByOccurredAtBetween(Instant start, Instant end) {
        return healthEventJpaRepository.findByOccurredAtBetween(start, end)
                .stream()
                .map(entity -> new EventData(
                        entity.getEventType(),
                        entity.getOccurredAt(),
                        toPayload(entity.getEventType(), entity.getPayload()),
                        entity.getDeviceId(),
                        entity.getIdempotencyKey()
                ))
                .toList();
    }

    private EventPayload toPayload(String eventTypeStr, Map<String, Object> map) {
        EventType eventType = EventType.from(eventTypeStr);
        Class<? extends EventPayload> clazz = EventPayload.payloadClassFor(eventType);
        return objectMapper.convertValue(map, clazz);
    }

    @Override
    @Transactional
    public void deleteAllEvents() {
        log.warn("Deleting all health events");
        healthEventJpaRepository.deleteAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> findExistingSleepIdempotencyKey(DeviceId deviceId, Instant sleepStart) {
        return eventRepository.findSleepIdempotencyKeyByDeviceIdAndSleepStart(deviceId, sleepStart);
    }
}
