package com.healthassistant.healthevents.api.dto.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload for deleting a previously recorded health event")
public record EventDeletedPayload(
    @NotBlank(message = "targetEventId is required")
    @Schema(description = "The event_id of the event to delete", example = "evt_abc123")
    String targetEventId,

    @Schema(description = "The idempotency_key of the event to delete (for verification)",
            example = "device1|StepsBucketedRecorded.v1|2025-01-01T08:00:00Z-0")
    String targetIdempotencyKey,

    @Schema(description = "Reason for deletion", example = "User requested deletion")
    String reason
) implements EventPayload {}
