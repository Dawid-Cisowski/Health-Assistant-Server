package com.healthassistant.appevents.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(
    description = "Request to submit health events from mobile applications",
    example = """
        {
          "events": [
            {
              "idempotencyKey": "gymrun-2025-11-17-1",
              "type": "WorkoutRecorded.v1",
              "occurredAt": "2025-11-17T18:00:00Z",
              "payload": {
                "workoutId": "gymrun-2025-11-17-1",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [...]
              }
            }
          ],
          "deviceId": "gymrun-app"
        }
        """
)
public record SubmitHealthEventsRequest(
    @JsonProperty("events")
    @Schema(description = "List of health events to submit")
    List<HealthEventRequest> events,

    @JsonProperty("deviceId")
    @Schema(description = "Source device/app identifier (optional, defaults to 'mobile-app')",
            example = "gymrun-app",
            nullable = true)
    String deviceId
) {

    @Schema(description = "Single health event")
    public record HealthEventRequest(
        @JsonProperty("idempotencyKey")
        @Schema(description = "Unique key for idempotency (optional, will be auto-generated if not provided)",
                example = "gymrun-2025-11-17-1",
                nullable = true)
        String idempotencyKey,

        @JsonProperty("type")
        @Schema(description = "Event type identifier",
                example = "WorkoutRecorded.v1")
        String type,

        @JsonProperty("occurredAt")
        @Schema(description = "When the event occurred (ISO-8601 UTC)",
                example = "2025-11-17T18:00:00Z")
        Instant occurredAt,

        @JsonProperty("payload")
        @Schema(description = "Event-specific data (structure depends on event type)")
        Map<String, Object> payload
    ) {}
}
