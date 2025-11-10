package com.healthassistant.service;

import com.healthassistant.domain.HealthEvent;
import com.healthassistant.dto.EventEnvelope;
import com.healthassistant.dto.IngestRequest;
import com.healthassistant.dto.IngestResponse;
import com.healthassistant.repository.HealthEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for ingesting health events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventIngestionService {

    private final HealthEventRepository eventRepository;
    private final EventIdGenerator eventIdGenerator;
    private final EventValidator eventValidator;

    /**
     * Process a batch of events and return per-item results
     */
    @Transactional
    public IngestResponse ingestEvents(IngestRequest request, String deviceId) {
        List<IngestResponse.EventResult> results = new ArrayList<>();

        for (int i = 0; i < request.getEvents().size(); i++) {
            EventEnvelope envelope = request.getEvents().get(i);
            IngestResponse.EventResult result = processEvent(envelope, i, deviceId);
            results.add(result);
        }

        return IngestResponse.builder()
            .results(results)
            .build();
    }

    private IngestResponse.EventResult processEvent(EventEnvelope envelope, int index, String deviceId) {
        try {
            // Validate event payload
            List<String> validationErrors = eventValidator.validate(envelope);
            if (!validationErrors.isEmpty()) {
                log.debug("Validation failed for event at index {}: {}", index, validationErrors);
                return IngestResponse.EventResult.builder()
                    .index(index)
                    .status(IngestResponse.EventStatus.invalid)
                    .error(IngestResponse.ItemError.builder()
                        .field("payload")
                        .message(String.join("; ", validationErrors))
                        .build())
                    .build();
            }

            // Check for duplicate
            if (eventRepository.existsByIdempotencyKey(envelope.getIdempotencyKey())) {
                log.debug("Duplicate event detected at index {}: {}", index, envelope.getIdempotencyKey());
                return IngestResponse.EventResult.builder()
                    .index(index)
                    .status(IngestResponse.EventStatus.duplicate)
                    .build();
            }

            // Create and save event
            String eventId = eventIdGenerator.generate();
            HealthEvent event = HealthEvent.builder()
                .eventId(eventId)
                .idempotencyKey(envelope.getIdempotencyKey())
                .eventType(envelope.getType())
                .occurredAt(envelope.getOccurredAt())
                .payload(envelope.getPayload())
                .deviceId(deviceId)
                .build();

            eventRepository.save(event);
            
            log.info("Stored event: {} (type: {}, key: {})", eventId, envelope.getType(), envelope.getIdempotencyKey());

            return IngestResponse.EventResult.builder()
                .index(index)
                .status(IngestResponse.EventStatus.stored)
                .eventId(eventId)
                .build();

        } catch (Exception e) {
            log.error("Failed to process event at index " + index, e);
            return IngestResponse.EventResult.builder()
                .index(index)
                .status(IngestResponse.EventStatus.invalid)
                .error(IngestResponse.ItemError.builder()
                    .field("general")
                    .message("Internal error: " + e.getMessage())
                    .build())
                .build();
        }
    }
}

