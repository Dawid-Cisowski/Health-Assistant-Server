package com.healthassistant.healthevents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.EventData;
import com.healthassistant.healthevents.api.dto.ExistingSleepInfo;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.ActivityEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.AllEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.CaloriesEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.CompensationEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.MealsEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.SleepEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.StepsEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.HeartRateEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.RestingHeartRateEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.events.WeightEventsStoredEvent;
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

    private static final String STEPS_BUCKETED_V1 = "StepsBucketedRecorded.v1";
    private static final String WORKOUT_V1 = "WorkoutRecorded.v1";
    private static final String SLEEP_SESSION_V1 = "SleepSessionRecorded.v1";
    private static final String ACTIVE_MINUTES_V1 = "ActiveMinutesRecorded.v1";
    private static final String WALKING_SESSION_V1 = "WalkingSessionRecorded.v1";
    private static final String HEART_RATE_V1 = "HeartRateSummaryRecorded.v1";
    private static final String RESTING_HEART_RATE_V1 = "RestingHeartRateRecorded.v1";
    private static final String ACTIVE_CALORIES_V1 = "ActiveCaloriesBurnedRecorded.v1";
    private static final String MEAL_V1 = "MealRecorded.v1";
    private static final String WEIGHT_V1 = "WeightMeasurementRecorded.v1";

    private final StoreHealthEventsCommandHandler commandHandler;
    private final ApplicationEventPublisher eventPublisher;
    private final HealthEventJpaRepository healthEventJpaRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Stores health events and publishes domain events for downstream projectors.
     *
     * <p>IMPORTANT: Event listeners for the published events (StepsEventsStoredEvent, WorkoutEventsStoredEvent, etc.)
     * should use {@code @TransactionalEventListener(phase = AFTER_COMMIT)} to ensure they only process events
     * after the transaction successfully commits. Using regular {@code @EventListener} or
     * {@code @TransactionalEventListener(phase = BEFORE_COMMIT)} could lead to:
     * <ul>
     *   <li>Processing events for data that gets rolled back</li>
     *   <li>Inconsistent projections if the listener fails and causes rollback</li>
     * </ul>
     *
     * <p>TODO: Consider using Spring's ApplicationEventMulticaster with async execution for better resilience,
     * or migrate to a message broker (e.g., Kafka) for guaranteed delivery and replay capabilities.
     */
    @Override
    @Transactional
    public StoreHealthEventsResult storeHealthEvents(StoreHealthEventsCommand command) {
        log.debug("Storing {} health events", command.events().size());

        StoreHealthEventsResult result = commandHandler.handle(command);

        if (!result.affectedDates().isEmpty()) {
            publishTypedEvents(result);
        }

        if (!result.compensationTargets().isEmpty()) {
            publishCompensationEvents(result, command.deviceId().value());
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
        publishWeightEvents(eventsByType);
        publishHeartRateEvents(eventsByType);
        publishRestingHeartRateEvents(eventsByType);

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
        int totalStoredEvents = eventsByType.values().stream().mapToInt(List::size).sum();
        log.debug("publishSleepEvents: totalStoredEvents={} sleepEvents={}", totalStoredEvents, events.size());

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

    private void publishWeightEvents(Map<String, List<StoredEventData>> eventsByType) {
        List<StoredEventData> events = eventsByType.getOrDefault(WEIGHT_V1, List.of());
        if (!events.isEmpty()) {
            Set<LocalDate> dates = extractAffectedDates(events);
            log.info("Publishing WeightEventsStoredEvent with {} events for {} dates", events.size(), dates.size());
            eventPublisher.publishEvent(new WeightEventsStoredEvent(events, dates));
        }
    }

    private void publishHeartRateEvents(Map<String, List<StoredEventData>> eventsByType) {
        List<StoredEventData> events = eventsByType.getOrDefault(HEART_RATE_V1, List.of());
        if (!events.isEmpty()) {
            Set<LocalDate> dates = extractAffectedDates(events);
            log.info("Publishing HeartRateEventsStoredEvent with {} events for {} dates", events.size(), dates.size());
            eventPublisher.publishEvent(new HeartRateEventsStoredEvent(events, dates));
        }
    }

    private void publishRestingHeartRateEvents(Map<String, List<StoredEventData>> eventsByType) {
        List<StoredEventData> events = eventsByType.getOrDefault(RESTING_HEART_RATE_V1, List.of());
        if (!events.isEmpty()) {
            Set<LocalDate> dates = extractAffectedDates(events);
            log.info("Publishing RestingHeartRateEventsStoredEvent with {} events for {} dates", events.size(), dates.size());
            eventPublisher.publishEvent(new RestingHeartRateEventsStoredEvent(events, dates));
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
                new AllEventsStoredEvent(deviceId, result.affectedDates(), eventTypeStrings)
        );
    }

    private void publishCompensationEvents(StoreHealthEventsResult result, String deviceId) {
        List<CompensationEventsStoredEvent.CompensationEventData> deletions = result.compensationTargets().stream()
                .filter(target -> target.compensationType() == StoreHealthEventsResult.CompensationType.DELETED)
                .map(target -> new CompensationEventsStoredEvent.CompensationEventData(
                        null,
                        target.targetEventId(),
                        target.targetEventType(),
                        extractDateSet(target.targetOccurredAt())
                ))
                .toList();

        List<CompensationEventsStoredEvent.CorrectionEventData> corrections = result.compensationTargets().stream()
                .filter(target -> target.compensationType() == StoreHealthEventsResult.CompensationType.CORRECTED)
                .map(target -> {
                    Set<LocalDate> affectedDates = extractDateSet(target.targetOccurredAt());
                    Set<LocalDate> correctedDates = target.correctedOccurredAt() != null
                            ? extractDateSet(target.correctedOccurredAt())
                            : affectedDates;
                    return new CompensationEventsStoredEvent.CorrectionEventData(
                            null,
                            target.targetEventId(),
                            target.targetEventType(),
                            correctedDates,
                            target.correctedEventType(),
                            target.correctedPayload(),
                            target.correctedOccurredAt()
                    );
                })
                .toList();

        CompensationEventsStoredEvent event = new CompensationEventsStoredEvent(deviceId, deletions, corrections);

        log.info("Publishing CompensationEventsStoredEvent for device {} with {} deletions, {} corrections, affected types: {}",
                deviceId, deletions.size(), corrections.size(), event.affectedEventTypes());

        eventPublisher.publishEvent(event);
    }

    private Set<LocalDate> extractDateSet(Instant instant) {
        return Optional.ofNullable(instant)
                .map(DateTimeUtils::toPolandDate)
                .map(Set::of)
                .orElse(Set.of());
    }

    private Set<LocalDate> extractAffectedDates(List<StoredEventData> events) {
        return events.stream()
                .map(StoredEventData::occurredAt)
                .filter(Objects::nonNull)
                .map(DateTimeUtils::toPolandDate)
                .collect(Collectors.toSet());
    }

    @Override
    public List<EventData> findEventsByOccurredAtBetween(Instant start, Instant end) {
        return healthEventJpaRepository.findByOccurredAtBetween(start, end)
                .stream()
                .map(this::toEventData)
                .toList();
    }

    @Override
    public List<EventData> findEventsByDeviceId(String deviceId) {
        return healthEventJpaRepository.findActiveByDeviceId(deviceId)
                .stream()
                .map(this::toEventData)
                .toList();
    }

    private EventData toEventData(HealthEventJpaEntity entity) {
        return new EventData(
                entity.getEventType(),
                entity.getOccurredAt(),
                toPayload(entity.getEventType(), entity.getPayload()),
                entity.getDeviceId(),
                entity.getIdempotencyKey()
        );
    }

    private StoredEventData toStoredEventData(HealthEventJpaEntity entity) {
        return new StoredEventData(
                new IdempotencyKey(entity.getIdempotencyKey()),
                EventType.from(entity.getEventType()),
                entity.getOccurredAt(),
                toPayload(entity.getEventType(), entity.getPayload()),
                new DeviceId(entity.getDeviceId()),
                new com.healthassistant.healthevents.api.model.EventId(entity.getEventId())
        );
    }

    private EventPayload toPayload(String eventTypeStr, Map<String, Object> map) {
        EventType eventType = EventType.from(eventTypeStr);
        Class<? extends EventPayload> clazz = EventPayload.payloadClassFor(eventType);
        return objectMapper.convertValue(map, clazz);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredEventData> findEventsForReprojection(int page, int size) {
        return healthEventJpaRepository.findAllActiveOrderByIdAsc(
                org.springframework.data.domain.PageRequest.of(page, size)
        )
                .getContent()
                .stream()
                .map(this::toStoredEventData)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredEventData> findEventsForDateRange(String deviceId, Instant start, Instant end) {
        return healthEventJpaRepository.findActiveByDeviceIdAndOccurredAtBetween(deviceId, start, end)
                .stream()
                .map(this::toStoredEventData)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAllEvents() {
        return healthEventJpaRepository.count();
    }

    @Override
    @Transactional
    public void deleteAllEvents() {
        log.warn("Deleting all health events");
        healthEventJpaRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteEventsByDeviceId(String deviceId) {
        log.warn("Deleting health events for device: {}", deviceId);
        healthEventJpaRepository.deleteByDeviceId(deviceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExistingSleepInfo> findExistingSleepInfo(DeviceId deviceId, Instant sleepStart) {
        return eventRepository.findExistingSleepInfo(deviceId, sleepStart);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredEventData> findEventById(String deviceId, String eventId) {
        return healthEventJpaRepository.findByEventIdAndDeviceId(eventId, deviceId)
                .filter(entity -> entity.getDeletedAt() == null && entity.getSupersededByEventId() == null)
                .map(this::toStoredEventData);
    }
}
