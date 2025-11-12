package com.healthassistant.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthassistant.dto.payload.EventPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = EventEnvelopeDeserializer.class)
@Schema(
    description = "A single health event envelope containing metadata and type-specific payload",
    example = """
        {
          "idempotencyKey": "device123|StepsBucketedRecorded.v1|2025-11-10T12:00:00Z|abc123def456",
          "type": "StepsBucketedRecorded.v1",
          "occurredAt": "2025-11-10T12:00:00Z",
          "payload": {
            "bucketStart": "2025-11-10T11:00:00Z",
            "bucketEnd": "2025-11-10T12:00:00Z",
            "count": 742,
            "originPackage": "com.google.android.apps.fitness"
          }
        }
        """
)
public class EventEnvelope {

    @NotBlank(message = "idempotencyKey is required")
    @JsonProperty("idempotencyKey")
    @Schema(
        description = "Unique key for idempotency (8-512 chars). Must be unique per device. " +
                     "Format: recommended to include deviceId, eventType, timestamp, and hash of payload.",
        example = "device123|StepsBucketedRecorded.v1|2025-11-10T12:00:00Z|abc123def456",
        minLength = 8,
        maxLength = 512
    )
    private String idempotencyKey;

    @NotBlank(message = "type is required")
    @JsonProperty("type")
    @Schema(
        description = "Event type identifier. Supported types: " +
                     "StepsBucketedRecorded.v1, HeartRateSummaryRecorded.v1, SleepSessionRecorded.v1, " +
                     "ActiveCaloriesBurnedRecorded.v1, ActiveMinutesRecorded.v1, ExerciseSessionRecorded.v1 (🚧 logging only)",
        example = "StepsBucketedRecorded.v1",
        allowableValues = {
            "StepsBucketedRecorded.v1",
            "HeartRateSummaryRecorded.v1",
            "SleepSessionRecorded.v1",
            "ActiveCaloriesBurnedRecorded.v1",
            "ActiveMinutesRecorded.v1",
            "ExerciseSessionRecorded.v1"
        }
    )
    private String type;

    @NotNull(message = "occurredAt is required")
    @JsonProperty("occurredAt")
    @Schema(
        description = "ISO-8601 UTC timestamp when the event logically occurred (client time)",
        example = "2025-11-10T12:00:00Z",
        type = "string",
        format = "date-time"
    )
    private Instant occurredAt;

    @Valid
    @NotNull(message = "payload is required")
    @JsonProperty("payload")
    @Schema(
        description = "Event-specific payload. Structure depends on event type (see anyOf schemas below).",
        oneOf = {
            com.healthassistant.dto.payload.StepsPayload.class,
            com.healthassistant.dto.payload.HeartRatePayload.class,
            com.healthassistant.dto.payload.SleepSessionPayload.class,
            com.healthassistant.dto.payload.ActiveCaloriesPayload.class,
            com.healthassistant.dto.payload.ActiveMinutesPayload.class,
            com.healthassistant.dto.payload.ExerciseSessionPayload.class
        }
    )
    private EventPayload payload;
}

