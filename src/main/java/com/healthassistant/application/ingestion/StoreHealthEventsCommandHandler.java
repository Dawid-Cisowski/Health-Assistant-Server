package com.healthassistant.application.ingestion;

import com.healthassistant.application.summary.DailySummaryFacade;
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

        Set<String> existingKeys = eventRepository.findExistingIdempotencyKeys(idempotencyKeys);

        List<Event> eventsToSave = validIndices.stream()
                .filter(index -> !existingKeys.contains(events.get(index).idempotencyKey().value()))
                .map(index -> {
                    var envelope = events.get(index);
                    EventType eventType = EventType.from(envelope.eventType());
                    EventId eventId = eventIdGenerator.generate();
                    return Event.create(
                            envelope.idempotencyKey(),
                            eventType,
                            envelope.occurredAt(),
                            envelope.payload(),
                            command.deviceId(),
                            eventId
                    );
                })
                .toList();

        if (!eventsToSave.isEmpty()) {
            eventRepository.saveAll(eventsToSave);
            log.info("Stored {} events in batch", eventsToSave.size());
        }

        java.util.Map<String, Event> savedEventsByKey = eventsToSave.stream()
                .collect(Collectors.toMap(
                        event -> event.idempotencyKey().value(),
                        event -> event
                ));

        List<StoreHealthEventsResult.EventResult> results = IntStream.range(0, events.size())
                .mapToObj(index -> {
                    StoreHealthEventsResult.EventResult validationResult = validationResults.get(index);
                    if (validationResult != null) {
                        return validationResult;
                    }

                    String idempotencyKey = events.get(index).idempotencyKey().value();
                    if (existingKeys.contains(idempotencyKey)) {
                        log.debug("Duplicate event detected at index {}: {}", index, idempotencyKey);
                        return createDuplicateResult(index);
                    }

                    Event savedEvent = savedEventsByKey.get(idempotencyKey);
                    if (savedEvent != null) {
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

        updateDailySummaries(command, results);

        return new StoreHealthEventsResult(results);
    }

    private void updateDailySummaries(StoreHealthEventsCommand command, List<StoreHealthEventsResult.EventResult> results) {
        Set<LocalDate> datesToUpdate = IntStream.range(0, results.size())
                .filter(index -> results.get(index).status() == StoreHealthEventsResult.EventStatus.stored)
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


    private StoreHealthEventsResult.EventResult createDuplicateResult(int index) {
        return new StoreHealthEventsResult.EventResult(
                index,
                StoreHealthEventsResult.EventStatus.duplicate,
                null,
                null
        );
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
