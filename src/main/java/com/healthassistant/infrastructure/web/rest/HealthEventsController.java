package com.healthassistant.infrastructure.web.rest;

import com.healthassistant.application.ingestion.HealthEventsFacade;
import com.healthassistant.application.ingestion.StoreHealthEventsCommand;
import com.healthassistant.application.ingestion.StoreHealthEventsResult;
import com.healthassistant.domain.event.DeviceId;
import com.healthassistant.domain.event.IdempotencyKey;
import com.healthassistant.dto.request.SubmitHealthEventsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/health-events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health Events", description = "Generic health event ingestion endpoints for mobile applications")
public class HealthEventsController {

    private final HealthEventsFacade healthEventsFacade;

    @PostMapping
    @Operation(
            summary = "Submit health events",
            description = """
                    Accepts health events from mobile applications and stores them in the system.

                    Supports multiple event types:
                    - WorkoutRecorded.v1: Gym workout sessions with exercises, sets, reps, and weights
                    - (Future: NutritionRecorded.v1, CustomMetricRecorded.v1, etc.)

                    Features:
                    - Batch submission: Send multiple events in one request
                    - Idempotency: Duplicate events (same idempotencyKey) are handled gracefully
                    - Validation: Each event is validated according to its type
                    - Flexible device identification: Specify source app/device

                    The system will:
                    1. Validate each event's structure and data
                    2. Store events in the database with idempotency check
                    3. Return detailed status for each event (stored/duplicate/invalid)
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Events processed (some may be invalid/duplicate)"),
            @ApiResponse(responseCode = "400", description = "Invalid request structure"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> submitHealthEvents(
            @RequestBody SubmitHealthEventsRequest request) {

        if (request.events() == null || request.events().isEmpty()) {
            log.warn("Received empty events list");
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "status", "error",
                            "message", "Events list cannot be empty"
                    ));
        }

        log.info("Received {} health events from device: {}",
                request.events().size(),
                request.deviceId() != null ? request.deviceId() : "mobile-app");

        try {
            // Map request to command
            DeviceId deviceId = new DeviceId(
                    request.deviceId() != null ? request.deviceId() : "mobile-app"
            );

            List<StoreHealthEventsCommand.EventEnvelope> eventEnvelopes = new ArrayList<>();

            for (int i = 0; i < request.events().size(); i++) {
                var eventRequest = request.events().get(i);

                // Generate idempotency key if not provided
                String idempotencyKeyValue = eventRequest.idempotencyKey();
                if (idempotencyKeyValue == null || idempotencyKeyValue.isBlank()) {
                    // Auto-generate from payload if possible
                    idempotencyKeyValue = generateIdempotencyKey(
                            deviceId.value(),
                            eventRequest.type(),
                            eventRequest.payload(),
                            i
                    );
                }

                IdempotencyKey idempotencyKey = new IdempotencyKey(idempotencyKeyValue);

                StoreHealthEventsCommand.EventEnvelope envelope = new StoreHealthEventsCommand.EventEnvelope(
                        idempotencyKey,
                        eventRequest.type(),
                        eventRequest.occurredAt(),
                        eventRequest.payload()
                );

                eventEnvelopes.add(envelope);
            }

            // Store events
            StoreHealthEventsCommand command = new StoreHealthEventsCommand(
                    eventEnvelopes,
                    deviceId
            );

            StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("totalEvents", request.events().size());

            List<Map<String, Object>> eventResults = new ArrayList<>();
            long storedCount = 0;
            long duplicateCount = 0;
            long invalidCount = 0;

            for (StoreHealthEventsResult.EventResult eventResult : result.results()) {
                Map<String, Object> eventResponse = new HashMap<>();
                eventResponse.put("index", eventResult.index());
                eventResponse.put("status", eventResult.status().toString());

                if (eventResult.eventId() != null) {
                    eventResponse.put("eventId", eventResult.eventId().value());
                }

                if (eventResult.error() != null) {
                    eventResponse.put("error", Map.of(
                            "field", eventResult.error().field(),
                            "message", eventResult.error().message()
                    ));
                }

                eventResults.add(eventResponse);

                switch (eventResult.status()) {
                    case stored -> storedCount++;
                    case duplicate -> duplicateCount++;
                    case invalid -> invalidCount++;
                }
            }

            response.put("events", eventResults);
            response.put("summary", Map.of(
                    "stored", storedCount,
                    "duplicate", duplicateCount,
                    "invalid", invalidCount
            ));

            // Determine overall status
            if (invalidCount == request.events().size()) {
                response.put("status", "all_invalid");
                log.warn("All {} events were invalid", request.events().size());
            } else if (invalidCount > 0) {
                response.put("status", "partial_success");
                log.info("Processed {} events: {} stored, {} duplicate, {} invalid",
                        request.events().size(), storedCount, duplicateCount, invalidCount);
            } else {
                response.put("status", "success");
                log.info("Successfully processed {} events: {} stored, {} duplicate",
                        request.events().size(), storedCount, duplicateCount);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to process health events", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to process health events: " + e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }

    /**
     * Generate idempotency key from event data if not provided by client.
     * For workout events, tries to extract workoutId from payload.
     */
    private String generateIdempotencyKey(
            String deviceId,
            String eventType,
            Map<String, Object> payload,
            int index) {

        // For workout events, try to extract workoutId
        if ("WorkoutRecorded.v1".equals(eventType)) {
            Object workoutId = payload.get("workoutId");
            if (workoutId != null) {
                return deviceId + "|workout|" + workoutId;
            }
        }

        // Fallback: generate from timestamp and index
        return deviceId + "|" + eventType + "|" + System.currentTimeMillis() + "-" + index;
    }
}
