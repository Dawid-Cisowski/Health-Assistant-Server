package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.payload.EventCorrectedPayload;
import com.healthassistant.healthevents.api.dto.payload.EventDeletedPayload;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
class StoreHealthEventsCommandHandler {

    private static final String SLEEP_SESSION_TYPE_VALUE = "SleepSessionRecorded.v1";

    private final EventRepository eventRepository;
    private final HealthEventFactory eventFactory;
    private final EventValidator eventValidator;

    @Transactional
    public StoreHealthEventsResult handle(StoreHealthEventsCommand command) {
        List<StoreHealthEventsCommand.EventEnvelope> events = command.events();
        String deviceId = command.deviceId().value();

        List<StoreHealthEventsResult.EventResult> validationResults = IntStream.range(0, events.size())
                .mapToObj(index -> {
                    var envelope = events.get(index);
                    return validateEvent(envelope, index);
                })
                .toList();

        List<Integer> validIndices = IntStream.range(0, validationResults.size())
                .filter(index -> validationResults.get(index) == null)
                .boxed()
                .toList();

        if (validIndices.isEmpty()) {
            return new StoreHealthEventsResult(validationResults, Set.of(), Set.of(), List.of());
        }

        List<IdempotencyKey> idempotencyKeys = validIndices.stream()
                .map(index -> events.get(index).idempotencyKey())
                .toList();

        Map<String, Event> existingEvents = eventRepository.findExistingEventsByIdempotencyKeys(idempotencyKeys);

        Map<Boolean, List<Integer>> partitioned = validIndices.stream()
                .collect(Collectors.partitioningBy(index ->
                        existingEvents.containsKey(events.get(index).idempotencyKey().value())));

        List<StoreHealthEventsResult.EventResult> duplicateResults = partitioned.get(true).stream()
                .map(index -> createDuplicateError(index,
                        "Event with this idempotency key already exists. Use EventCorrected.v1 to modify events."))
                .toList();

        List<Event> eventsToSave = partitioned.get(false).stream()
                .map(index -> {
                    var envelope = events.get(index);
                    EventType eventType = EventType.from(envelope.eventType());
                    return eventFactory.createNew(envelope.idempotencyKey(), eventType, envelope.occurredAt(), envelope.payload(), command.deviceId());
                })
                .toList();

        List<StoreHealthEventsResult.CompensationTarget> compensationTargets;

        if (!eventsToSave.isEmpty()) {
            eventRepository.saveAll(eventsToSave);
            log.info("Stored {} new events in batch", eventsToSave.size());

            compensationTargets = handleCompensationEvents(eventsToSave, deviceId);
        } else {
            compensationTargets = List.of();
        }

        Map<String, Event> allProcessedEvents = new java.util.HashMap<>();
        eventsToSave.forEach(e -> allProcessedEvents.put(e.idempotencyKey().value(), e));

        Set<String> duplicateKeys = duplicateResults.stream()
                .map(r -> events.get(r.index()).idempotencyKey().value())
                .collect(Collectors.toSet());

        Map<Integer, Event> savedEventsByIndex = validIndices.stream()
                .filter(index -> !duplicateKeys.contains(events.get(index).idempotencyKey().value()))
                .collect(Collectors.toMap(
                        index -> index,
                        index -> allProcessedEvents.get(events.get(index).idempotencyKey().value())
                ));

        Map<Integer, StoreHealthEventsResult.EventResult> duplicateResultsByIndex = duplicateResults.stream()
                .collect(Collectors.toMap(StoreHealthEventsResult.EventResult::index, r -> r));

        List<StoreHealthEventsResult.EventResult> results = IntStream.range(0, events.size())
                .mapToObj(index -> {
                    StoreHealthEventsResult.EventResult validationResult = validationResults.get(index);
                    if (validationResult != null) {
                        return validationResult;
                    }

                    StoreHealthEventsResult.EventResult duplicateResult = duplicateResultsByIndex.get(index);
                    if (duplicateResult != null) {
                        return duplicateResult;
                    }

                    Event savedEvent = savedEventsByIndex.get(index);
                    if (savedEvent != null) {
                        if (SLEEP_SESSION_TYPE_VALUE.equals(savedEvent.eventType().value())) {
                            log.info("Sleep event index={} status=stored idempotencyKey={}",
                                    index, LogMasker.maskSensitive(savedEvent.idempotencyKey().value()));
                        }

                        return new StoreHealthEventsResult.EventResult(
                                index,
                                StoreHealthEventsResult.EventStatus.stored,
                                savedEvent.eventId(),
                                null
                        );
                    }

                    return createErrorResult(index, "Event not found after save");
                })
                .toList();

        Set<LocalDate> affectedDates = extractAffectedDates(command, results);
        Set<EventType> eventTypes = extractEventTypes(command, results);
        List<com.healthassistant.healthevents.api.dto.StoredEventData> storedEventData = extractStoredEvents(results, savedEventsByIndex);

        return new StoreHealthEventsResult(results, affectedDates, eventTypes, storedEventData, compensationTargets);
    }

    private List<com.healthassistant.healthevents.api.dto.StoredEventData> extractStoredEvents(
            List<StoreHealthEventsResult.EventResult> results,
            Map<Integer, Event> savedEventsByIndex
    ) {
        return IntStream.range(0, results.size())
                .filter(index -> results.get(index).status() == StoreHealthEventsResult.EventStatus.stored)
                .mapToObj(savedEventsByIndex::get)
                .filter(Objects::nonNull)
                .map(event -> new com.healthassistant.healthevents.api.dto.StoredEventData(
                        event.idempotencyKey(),
                        event.eventType(),
                        event.occurredAt(),
                        event.payload(),
                        event.deviceId(),
                        event.eventId()
                ))
                .toList();
    }

    private Set<LocalDate> extractAffectedDates(StoreHealthEventsCommand command, List<StoreHealthEventsResult.EventResult> results) {
        return IntStream.range(0, results.size())
                .filter(index -> results.get(index).status() == StoreHealthEventsResult.EventStatus.stored)
                .mapToObj(command.events()::get)
                .map(StoreHealthEventsCommand.EventEnvelope::occurredAt)
                .filter(Objects::nonNull)
                .map(DateTimeUtils::toPolandDate)
                .collect(Collectors.toSet());
    }

    private Set<EventType> extractEventTypes(StoreHealthEventsCommand command, List<StoreHealthEventsResult.EventResult> results) {
        return IntStream.range(0, results.size())
                .filter(index -> results.get(index).status() == StoreHealthEventsResult.EventStatus.stored)
                .mapToObj(command.events()::get)
                .map(StoreHealthEventsCommand.EventEnvelope::eventType)
                .map(this::parseEventTypeSafely)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    private Optional<EventType> parseEventTypeSafely(String eventTypeStr) {
        try {
            return Optional.of(EventType.from(eventTypeStr));
        } catch (IllegalArgumentException e) {
            log.error("Stored event has invalid event type '{}'. This should never happen - indicates data corruption or validation bypass.",
                    eventTypeStr, e);
            throw new IllegalStateException("Stored event has invalid event type: " + eventTypeStr, e);
        }
    }

    private StoreHealthEventsResult.EventResult validateEvent(
            StoreHealthEventsCommand.EventEnvelope envelope,
            int index
    ) {
        String eventTypeStr = envelope.eventType();
        if (eventTypeStr == null || eventTypeStr.isBlank()) {
            return createInvalidResult(index, "type", "Missing required field: type");
        }

        if (envelope.payload() == null) {
            return createInvalidResult(index, "payload", "Missing required field: payload");
        }

        List<EventValidationError> validationErrors = eventValidator.validate(envelope.payload());

        if (!validationErrors.isEmpty()) {
            log.debug("Validation failed for event at index {}: {}", index, validationErrors);
            return createInvalidResult(index, validationErrors);
        }

        IdempotencyKey idempotencyKey = envelope.idempotencyKey();
        if (idempotencyKey.isTemporary()) {
            return createInvalidResult(index, "idempotencyKey", "Missing required field: idempotencyKey");
        }

        return null;
    }

    private StoreHealthEventsResult.EventResult createInvalidResult(int index, String field, String message) {
        return new StoreHealthEventsResult.EventResult(
                index,
                StoreHealthEventsResult.EventStatus.invalid,
                null,
                new StoreHealthEventsResult.EventError(field, message)
        );
    }

    private StoreHealthEventsResult.EventResult createErrorResult(int index, String message) {
        return new StoreHealthEventsResult.EventResult(
                index,
                StoreHealthEventsResult.EventStatus.invalid,
                null,
                new StoreHealthEventsResult.EventError("general", "Internal error: " + message)
        );
    }

    private StoreHealthEventsResult.EventResult createInvalidResult(
            int index,
            List<EventValidationError> errors
    ) {
        String errorMessage = errors.stream()
                .map(EventValidationError::message)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        return new StoreHealthEventsResult.EventResult(
                index,
                StoreHealthEventsResult.EventStatus.invalid,
                null,
                new StoreHealthEventsResult.EventError("payload", errorMessage)
        );
    }

    private StoreHealthEventsResult.EventResult createDuplicateError(int index, String message) {
        return new StoreHealthEventsResult.EventResult(
                index,
                StoreHealthEventsResult.EventStatus.duplicate,
                null,
                new StoreHealthEventsResult.EventError("idempotencyKey", message)
        );
    }

    private List<StoreHealthEventsResult.CompensationTarget> handleCompensationEvents(List<Event> savedEvents, String requestingDeviceId) {
        return savedEvents.stream()
                .filter(event -> event.payload() instanceof EventDeletedPayload || event.payload() instanceof EventCorrectedPayload)
                .flatMap(event -> processCompensationEvent(event, requestingDeviceId).stream())
                .toList();
    }

    private Optional<StoreHealthEventsResult.CompensationTarget> processCompensationEvent(Event event, String requestingDeviceId) {
        return switch (event.payload()) {
            case EventDeletedPayload deletedPayload -> processDeletedEvent(event, deletedPayload, requestingDeviceId);
            case EventCorrectedPayload correctedPayload -> processCorrectedEvent(event, correctedPayload, requestingDeviceId);
            default -> Optional.empty();
        };
    }

    private Optional<StoreHealthEventsResult.CompensationTarget> processDeletedEvent(
            Event event,
            EventDeletedPayload deletedPayload,
            String requestingDeviceId
    ) {
        return eventRepository.markAsDeleted(
                deletedPayload.targetEventId(),
                event.eventId().value(),
                Instant.now(),
                requestingDeviceId
        ).map(info -> {
            log.info("Processed EventDeleted.v1: marked event {} as deleted", deletedPayload.targetEventId());
            return new StoreHealthEventsResult.CompensationTarget(
                    info.targetEventId(),
                    info.targetEventType(),
                    info.targetOccurredAt(),
                    info.deviceId(),
                    StoreHealthEventsResult.CompensationType.DELETED
            );
        }).or(() -> {
            log.warn("EventDeleted.v1: target event {} not found or not owned by device {}",
                    deletedPayload.targetEventId(), requestingDeviceId);
            return Optional.empty();
        });
    }

    private Optional<StoreHealthEventsResult.CompensationTarget> processCorrectedEvent(
            Event event,
            EventCorrectedPayload correctedPayload,
            String requestingDeviceId
    ) {
        return eventRepository.markAsSuperseded(
                correctedPayload.targetEventId(),
                event.eventId().value(),
                requestingDeviceId
        ).map(info -> {
            log.info("Processed EventCorrected.v1: marked event {} as superseded", correctedPayload.targetEventId());
            return new StoreHealthEventsResult.CompensationTarget(
                    info.targetEventId(),
                    info.targetEventType(),
                    info.targetOccurredAt(),
                    info.deviceId(),
                    StoreHealthEventsResult.CompensationType.CORRECTED,
                    correctedPayload.correctedEventType(),
                    correctedPayload.correctedPayload(),
                    correctedPayload.correctedOccurredAt()
            );
        }).or(() -> {
            log.warn("EventCorrected.v1: target event {} not found or not owned by device {}",
                    correctedPayload.targetEventId(), requestingDeviceId);
            return Optional.empty();
        });
    }
}
