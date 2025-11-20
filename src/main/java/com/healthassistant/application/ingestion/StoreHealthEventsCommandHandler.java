package com.healthassistant.application.ingestion;

import com.healthassistant.application.steps.projection.StepsProjector;
import com.healthassistant.application.summary.DailySummaryFacade;
import com.healthassistant.application.workout.projection.WorkoutProjector;
import com.healthassistant.domain.event.DeviceId;
import com.healthassistant.domain.event.Event;
import com.healthassistant.domain.event.EventId;
import com.healthassistant.domain.event.EventType;
import com.healthassistant.domain.event.IdempotencyKey;
import com.healthassistant.domain.event.EventValidationError;
import com.healthassistant.domain.event.EventValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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
    private final DailySummaryFacade dailySummaryFacade;
    private final WorkoutProjector workoutProjector;
    private final StepsProjector stepsProjector;

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
            updateDailySummaries(command, validationResults);
            return new StoreHealthEventsResult(validationResults);
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
            EventId eventId = existingEvent != null ? existingEvent.eventId() : eventIdGenerator.generate();
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

        // Project workout events
        List<Event> allEvents = new java.util.ArrayList<>();
        allEvents.addAll(eventsToSave);
        allEvents.addAll(eventsToUpdate);

        allEvents.forEach(event -> {
            try {
                workoutProjector.projectWorkout(event);
                stepsProjector.projectSteps(event);
            } catch (Exception e) {
                log.error("Failed to project event {}", event.eventId().value(), e);
            }
        });

        Map<String, Event> allProcessedEvents = new java.util.HashMap<>();
        eventsToSave.forEach(e -> allProcessedEvents.put(e.idempotencyKey().value(), e));
        eventsToUpdate.forEach(e -> allProcessedEvents.put(e.idempotencyKey().value(), e));

        // Track which events were updated (duplicates)
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
                        // Check if this was an update (duplicate) or new insert
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

        updateDailySummaries(command, results);

        return new StoreHealthEventsResult(results);
    }

    private void updateDailySummaries(StoreHealthEventsCommand command, List<StoreHealthEventsResult.EventResult> results) {
        Set<LocalDate> datesToUpdate = IntStream.range(0, results.size())
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

        for (LocalDate date : datesToUpdate) {
            try {
                log.debug("Updating daily summary for date: {}", date);
                dailySummaryFacade.generateDailySummary(date);
                log.debug("Successfully updated daily summary for date: {}", date);
            } catch (Exception e) {
                log.error("Failed to update daily summary for date: {}", date, e);
            }
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
