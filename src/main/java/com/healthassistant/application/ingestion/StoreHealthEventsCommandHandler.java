package com.healthassistant.application.ingestion;

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

import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
class StoreHealthEventsCommandHandler {

    private final EventRepository eventRepository;
    private final EventIdGenerator eventIdGenerator;
    private final EventValidator eventValidator;

    @Transactional
    public StoreHealthEventsResult handle(StoreHealthEventsCommand command) {
        List<StoreHealthEventsResult.EventResult> results = IntStream.range(0, command.events().size())
                .mapToObj(index -> processEvent(command.events().get(index), index, command.deviceId()))
                .toList();

        return new StoreHealthEventsResult(results);
    }

    private StoreHealthEventsResult.EventResult processEvent(
            StoreHealthEventsCommand.EventEnvelope envelope,
            int index,
            DeviceId deviceId
    ) {
        try {
            if ("ExerciseSessionRecorded.v1".equals(envelope.eventType())) {
                log.warn("=== ExerciseSessionRecorded.v1 RECEIVED ===");
                log.warn("Event type: {}", envelope.eventType());
                log.warn("Idempotency key: {}", envelope.idempotencyKey().value());
                log.warn("Occurred at: {}", envelope.occurredAt());
                log.warn("Payload: {}", envelope.payload());
                log.warn("Payload class: {}", envelope.payload().getClass().getName());
                log.warn("===========================================");
            }
            
            String eventTypeStr = envelope.eventType();
            if (eventTypeStr == null || eventTypeStr.isBlank()) {
                return new StoreHealthEventsResult.EventResult(
                        index,
                        StoreHealthEventsResult.EventStatus.invalid,
                        null,
                        new StoreHealthEventsResult.EventError("type", "Missing required field: type")
                );
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
                return new StoreHealthEventsResult.EventResult(
                        index,
                        StoreHealthEventsResult.EventStatus.invalid,
                        null,
                        new StoreHealthEventsResult.EventError("idempotencyKey", "Missing required field: idempotencyKey")
                );
            }
            
            if (eventRepository.existsByIdempotencyKey(idempotencyKey)) {
                log.debug("Duplicate event detected at index {}: {}", index, idempotencyKey.value());
                return new StoreHealthEventsResult.EventResult(
                        index,
                        StoreHealthEventsResult.EventStatus.duplicate,
                        null,
                        null
                );
            }

            EventType eventType = EventType.from(envelope.eventType());
            EventId eventId = eventIdGenerator.generate();
            Event event = Event.create(
                    idempotencyKey,
                    eventType,
                    envelope.occurredAt(),
                    envelope.payload(),
                    deviceId,
                    eventId
            );

            eventRepository.save(event);

            log.info("Stored event: {} (type: {}, key: {})",
                    eventId.value(),
                    eventType.value(),
                    idempotencyKey.value());

            return new StoreHealthEventsResult.EventResult(
                    index,
                    StoreHealthEventsResult.EventStatus.stored,
                    eventId,
                    null
            );

        } catch (Exception e) {
            log.error("Failed to process event at index " + index, e);
            return new StoreHealthEventsResult.EventResult(
                    index,
                    StoreHealthEventsResult.EventStatus.invalid,
                    null,
                    new StoreHealthEventsResult.EventError("general", "Internal error: " + e.getMessage())
            );
        }
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
