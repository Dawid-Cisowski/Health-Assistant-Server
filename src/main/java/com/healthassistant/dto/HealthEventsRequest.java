package com.healthassistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for batch health event ingestion")
public class HealthEventsRequest {

    @Valid
    @NotEmpty(message = "Events list cannot be empty")
    @Size(min = 1, max = 100, message = "Events list must contain between 1 and 100 items")
    @Schema(
        description = "List of health events to store (1-100 events per batch)",
        example = """
            [
              {
                "idempotencyKey": "device123|StepsBucketedRecorded.v1|2025-11-10T12:00:00Z|abc123",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T12:00:00Z",
                "payload": {
                  "bucketStart": "2025-11-10T11:00:00Z",
                  "bucketEnd": "2025-11-10T12:00:00Z",
                  "count": 742,
                  "originPackage": "com.google.android.apps.fitness"
                }
              },
              {
                "idempotencyKey": "device123|HeartRateSummaryRecorded.v1|2025-11-10T12:15:00Z|def456",
                "type": "HeartRateSummaryRecorded.v1",
                "occurredAt": "2025-11-10T12:15:00Z",
                "payload": {
                  "bucketStart": "2025-11-10T12:00:00Z",
                  "bucketEnd": "2025-11-10T12:15:00Z",
                  "avg": 78.3,
                  "min": 61,
                  "max": 115,
                  "samples": 46,
                  "originPackage": "com.google.android.apps.fitness"
                }
              }
            ]
            """
    )
    private List<EventEnvelope> events;
}
