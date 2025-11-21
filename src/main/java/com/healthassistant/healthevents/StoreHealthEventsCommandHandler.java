package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
class StoreHealthEventsCommandHandler {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final EventRepository eventRepository;
    private final EventIdGenerator eventIdGenerator;
    private final EventValidator eventValidator;

    @Transactional
    public StoreHealthEventsResult handle(StoreHealthEventsCommand command) {
        List<StoreHealthEventsCommand.EventEnvelope> events = command.events();

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

        List<Event> eventsToSave = new java.util.ArrayList<>();
        List<Event> eventsToUpdate = new java.util.ArrayList<>();

        for (Integer index : validIndices) {
            var envelope = events.get(index);
            String idempotencyKeyValue = envelope.idempotencyKey().value();

            Event existingEvent = existingEvents.get(idempotencyKeyValue);

            EventType eventType = EventType.from(envelope.eventType());
            com.healthassistant.healthevents.api.model.EventId eventId = existingEvent != null ? existingEvent.eventId() : eventIdGenerator.generate();
            Instant createdAt = existingEvent != null ? existingEvent.createdAt() : Instant.now();

            Event event = new Event(
                    envelope.idempotencyKey(),
                    eventType,
                    envelope.occurredAt(),
                    envelope.payload(),
                    command.deviceId(),
                    eventId,
                    createdAt
            );

            if (existingEvent != null) {
                eventsToUpdate.add(event);
            } else {
                eventsToSave.add(event);
            }
        }

        if (!eventsToSave.isEmpty()) {
            eventRepository.saveAll(eventsToSave);
            log.info("Stored {} new events in batch", eventsToSave.size());
        }

        if (!eventsToUpdate.isEmpty()) {
            eventRepository.updateAll(eventsToUpdate);
            log.info("Updated {} existing events in batch", eventsToUpdate.size());
        }

        Map<String, Event> allProcessedEvents = new java.util.HashMap<>();
        eventsToSave.forEach(e -> allProcessedEvents.put(e.idempotencyKey().value(), e));
        eventsToUpdate.forEach(e -> allProcessedEvents.put(e.idempotencyKey().value(), e));

        Set<String> updatedEventKeys = eventsToUpdate.stream()
                .map(e -> e.idempotencyKey().value())
                .collect(Collectors.toSet());

        Map<Integer, Event> savedEventsByIndex = validIndices.stream()
                .collect(Collectors.toMap(
                        index -> index,
                        index -> allProcessedEvents.get(events.get(index).idempotencyKey().value())
                ));

        List<StoreHealthEventsResult.EventResult> results = IntStream.range(0, events.size())
                .mapToObj(index -> {
                    StoreHealthEventsResult.EventResult validationResult = validationResults.get(index);
                    if (validationResult != null) {
                        return validationResult;
                    }

                    Event savedEvent = savedEventsByIndex.get(index);
                    if (savedEvent != null) {
                        boolean isDuplicate = updatedEventKeys.contains(savedEvent.idempotencyKey().value());
                        return new StoreHealthEventsResult.EventResult(
                                index,
                                isDuplicate ? StoreHealthEventsResult.EventStatus.duplicate : StoreHealthEventsResult.EventStatus.stored,
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

        return new StoreHealthEventsResult(results, affectedDates, eventTypes, storedEventData);
    }

    private List<com.healthassistant.healthevents.api.dto.StoredEventData> extractStoredEvents(
            List<StoreHealthEventsResult.EventResult> results,
            Map<Integer, Event> savedEventsByIndex
    ) {
        return IntStream.range(0, results.size())
                .filter(index -> results.get(index).status() == StoreHealthEventsResult.EventStatus.stored)
                .mapToObj(savedEventsByIndex::get)
                .filter(event -> event != null)
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
                .filter(index -> {
                    var status = results.get(index).status();
                    return status == StoreHealthEventsResult.EventStatus.stored ||
                           status == StoreHealthEventsResult.EventStatus.duplicate;
                })
                .mapToObj(index -> {
                    var envelope = command.events().get(index);
                    Instant occurredAt = envelope.occurredAt();
                    return occurredAt != null ? occurredAt.atZone(POLAND_ZONE).toLocalDate() : null;
                })
                .filter(date -> date != null)
                .collect(Collectors.toSet());
    }

    private Set<EventType> extractEventTypes(StoreHealthEventsCommand command, List<StoreHealthEventsResult.EventResult> results) {
        return IntStream.range(0, results.size())
                .filter(index -> {
                    var status = results.get(index).status();
                    return status == StoreHealthEventsResult.EventStatus.stored ||
                           status == StoreHealthEventsResult.EventStatus.duplicate;
                })
                .mapToObj(index -> {
                    var envelope = command.events().get(index);
                    try {
                        return EventType.from(envelope.eventType());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private StoreHealthEventsResult.EventResult validateEvent(
            StoreHealthEventsCommand.EventEnvelope envelope,
            int index
    ) {
        String eventTypeStr = envelope.eventType();
        if (eventTypeStr == null || eventTypeStr.isBlank()) {
            return createInvalidResult(index, "type", "Missing required field: type");
        }

        List<EventValidationError> validationErrors = eventValidator.validate(
                eventTypeStr,
                envelope.payload() != null ? envelope.payload() : java.util.Map.of()
        );

        if (!validationErrors.isEmpty()) {
            log.debug("Validation failed for event at index {}: {}", index, validationErrors);
            return createInvalidResult(index, validationErrors);
        }

        IdempotencyKey idempotencyKey = envelope.idempotencyKey();
        if (idempotencyKey.value().equals("temp-key-for-validation")) {
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
}
