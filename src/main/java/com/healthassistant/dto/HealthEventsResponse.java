package com.healthassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Schema(description = "Response containing processing results for each event in the batch")
public class HealthEventsResponse {

    @Schema(
        description = "Results for each event, in the same order as the input events",
        example = """
            [
              {
                "index": 0,
                "status": "stored",
                "eventId": "evt_abc123xyz456",
                "error": null
              }
            ]
            """
    )
    private List<EventResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(
        description = "Processing result for a single event",
        example = """
            {
              "index": 0,
              "status": "stored",
              "eventId": "evt_abc123xyz456",
              "error": null
            }
            """
    )
    public static class EventResult {
        @Schema(
            description = "Zero-based index of the event in the input array",
            example = "0"
        )
        private int index;

        @Schema(
            description = """
                Processing status:
                - `stored`: Event was successfully stored
                - `duplicate`: Event with same idempotencyKey already exists (not stored)
                - `invalid`: Event failed validation (see error field)
                """,
            example = "stored"
        )
        private EventStatus status;

        @Schema(
            description = "Server-generated unique event ID (format: evt_XXXX). Only present when status is 'stored'",
            example = "evt_abc123xyz456",
            nullable = true
        )
        private String eventId;

        @Schema(
            description = "Validation error details. Only present when status is 'invalid'",
            nullable = true
        )
        private ItemError error;
    }

    @Schema(description = "Event processing status")
    public enum EventStatus {
        @Schema(description = "Event was successfully stored in the database")
        stored,
        @Schema(description = "Event with same idempotencyKey already exists (idempotent response)")
        duplicate,
        @Schema(description = "Event failed validation (see error field for details)")
        invalid
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Validation error information")
    public static class ItemError {
        @Schema(
            description = "Field name that caused the validation error (or 'payload'/'type' for general errors)",
            example = "count"
        )
        private String field;

        @Schema(
            description = "Human-readable error message",
            example = "Missing required field: count"
        )
        private String message;
    }
}
