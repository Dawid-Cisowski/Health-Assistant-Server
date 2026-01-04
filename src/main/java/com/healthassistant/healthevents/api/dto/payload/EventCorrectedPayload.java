package com.healthassistant.healthevents.api.dto.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Payload for correcting a previously recorded health event")
public record EventCorrectedPayload(
    @NotBlank(message = "targetEventId is required")
    @Schema(description = "The event_id of the event being corrected", example = "evt_abc123")
    String targetEventId,

    @Schema(description = "The idempotency_key of the event being corrected (for verification)")
    String targetIdempotencyKey,

    @NotBlank(message = "correctedEventType is required")
    @Schema(description = "The event type of the corrected event", example = "StepsBucketedRecorded.v1")
    String correctedEventType,

    @NotNull(message = "correctedPayload is required")
    @Schema(description = "The corrected payload data")
    Map<String, Object> correctedPayload,

    @NotNull(message = "correctedOccurredAt is required")
    @Schema(description = "The corrected occurred_at timestamp")
    Instant correctedOccurredAt,

    @Schema(description = "Reason for correction", example = "Step count was incorrect")
    String reason
) implements EventPayload {}
