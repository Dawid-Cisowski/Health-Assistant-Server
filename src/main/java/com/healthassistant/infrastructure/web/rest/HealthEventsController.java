package com.healthassistant.infrastructure.web.rest;

import com.healthassistant.application.ingestion.HealthEventsFacade;
import com.healthassistant.application.ingestion.StoreHealthEventsCommand;
import com.healthassistant.dto.HealthEventsRequest;
import com.healthassistant.dto.HealthEventsResponse;
import com.healthassistant.infrastructure.web.rest.mapper.HealthEventsMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/health-events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health Events", description = "Store normalized health events")
class HealthEventsController {

    private final HealthEventsFacade healthEventsFacade;

    @PostMapping
    @Operation(
        summary = "Store a batch of health events (append-only)",
        description = """
            Accepts up to 100 health events in a single batch request. Each event must have a unique idempotencyKey 
            to ensure idempotency. Events are stored in an append-only fashion - once stored, they cannot be modified.
            
            **Supported Event Types:**
            - `StepsBucketedRecorded.v1` - Step count for a time bucket
            - `HeartRateSummaryRecorded.v1` - Heart rate summary (avg/min/max) for a time bucket
            - `SleepSessionRecorded.v1` - Sleep session with start/end times
            - `ActiveCaloriesBurnedRecorded.v1` - Active calories burned in a time bucket
            - `ActiveMinutesRecorded.v1` - Active minutes (moderate/vigorous activity) in a time bucket
            
            **Response Status Codes:**
            - `stored` - Event was successfully stored
            - `duplicate` - Event with same idempotencyKey already exists (not stored again)
            - `invalid` - Event failed validation (see error details)
            
            **Idempotency:**
            If you send the same event twice with the same idempotencyKey, the second request will return 
            `status: duplicate` for that event without storing it again. This ensures safe retries.
            """,
        security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Batch processed successfully. Check individual event results for status.",
            content = @Content(
                schema = @Schema(implementation = HealthEventsResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "All events stored",
                        value = """
                            {
                              "results": [
                                {
                                  "index": 0,
                                  "status": "stored",
                                  "eventId": "evt_abc123xyz456",
                                  "error": null
                                }
                              ]
                            }
                            """
                    ),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "Mixed results",
                        value = """
                            {
                              "results": [
                                {
                                  "index": 0,
                                  "status": "stored",
                                  "eventId": "evt_abc123xyz456",
                                  "error": null
                                },
                                {
                                  "index": 1,
                                  "status": "duplicate",
                                  "eventId": null,
                                  "error": null
                                },
                                {
                                  "index": 2,
                                  "status": "invalid",
                                  "eventId": null,
                                  "error": {
                                    "field": "payload",
                                    "message": "Missing required field: count"
                                  }
                                }
                              ]
                            }
                            """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Malformed JSON or schema violation",
            content = @Content(
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = """
                        {
                          "code": "VALIDATION_ERROR",
                          "message": "Request validation failed",
                          "details": ["events[0].type: must not be blank"]
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "HMAC authentication failed",
            content = @Content(
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = """
                        {
                          "code": "HMAC_AUTH_FAILED",
                          "message": "Invalid signature",
                          "details": []
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "413",
            description = "Too many events in batch (exceeds 100)",
            content = @Content(
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                    value = """
                        {
                          "code": "BATCH_TOO_LARGE",
                          "message": "Too many events in batch",
                          "details": ["Events list must contain between 1 and 100 items"]
                        }
                        """
                )
            )
        )
    })
    ResponseEntity<HealthEventsResponse> storeHealthEvents(
            @Valid @RequestBody 
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Batch of health events to store. Each event must have a unique idempotencyKey.",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = HealthEventsRequest.class),
                    examples = {
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Single Steps Event",
                            summary = "StepsBucketedRecorded.v1",
                            description = "Example of a single steps event",
                            value = """
                                {
                                  "events": [
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
                                    }
                                  ]
                                }
                                """
                        ),
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Heart Rate Summary Event",
                            summary = "HeartRateSummaryRecorded.v1",
                            description = "Example of a heart rate summary event",
                            value = """
                                {
                                  "events": [
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
                                }
                                """
                        ),
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Sleep Session Event",
                            summary = "SleepSessionRecorded.v1",
                            description = "Example of a sleep session event",
                            value = """
                                {
                                  "events": [
                                    {
                                      "idempotencyKey": "device123|SleepSessionRecorded.v1|2025-11-10T08:00:00Z|ghi789",
                                      "type": "SleepSessionRecorded.v1",
                                      "occurredAt": "2025-11-10T08:00:00Z",
                                      "payload": {
                                        "sleepStart": "2025-11-10T00:30:00Z",
                                        "sleepEnd": "2025-11-10T08:00:00Z",
                                        "totalMinutes": 450,
                                        "originPackage": "com.google.android.apps.fitness"
                                      }
                                    }
                                  ]
                                }
                                """
                        ),
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Active Calories Event",
                            summary = "ActiveCaloriesBurnedRecorded.v1",
                            description = "Example of an active calories burned event",
                            value = """
                                {
                                  "events": [
                                    {
                                      "idempotencyKey": "device123|ActiveCaloriesBurnedRecorded.v1|2025-11-10T14:00:00Z|jkl012",
                                      "type": "ActiveCaloriesBurnedRecorded.v1",
                                      "occurredAt": "2025-11-10T14:00:00Z",
                                      "payload": {
                                        "bucketStart": "2025-11-10T13:00:00Z",
                                        "bucketEnd": "2025-11-10T14:00:00Z",
                                        "energyKcal": 125.5,
                                        "originPackage": "com.google.android.apps.fitness"
                                      }
                                    }
                                  ]
                                }
                                """
                        ),
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Active Minutes Event",
                            summary = "ActiveMinutesRecorded.v1",
                            description = "Example of an active minutes event",
                            value = """
                                {
                                  "events": [
                                    {
                                      "idempotencyKey": "device123|ActiveMinutesRecorded.v1|2025-11-10T15:00:00Z|mno345",
                                      "type": "ActiveMinutesRecorded.v1",
                                      "occurredAt": "2025-11-10T15:00:00Z",
                                      "payload": {
                                        "bucketStart": "2025-11-10T14:00:00Z",
                                        "bucketEnd": "2025-11-10T15:00:00Z",
                                        "activeMinutes": 25,
                                        "originPackage": "com.google.android.apps.fitness"
                                      }
                                    }
                                  ]
                                }
                                """
                        ),
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Mixed Batch",
                            summary = "Multiple Event Types",
                            description = "Example of a batch with multiple event types",
                            value = """
                                {
                                  "events": [
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
                                    },
                                    {
                                      "idempotencyKey": "device123|SleepSessionRecorded.v1|2025-11-10T08:00:00Z|ghi789",
                                      "type": "SleepSessionRecorded.v1",
                                      "occurredAt": "2025-11-10T08:00:00Z",
                                      "payload": {
                                        "sleepStart": "2025-11-10T00:30:00Z",
                                        "sleepEnd": "2025-11-10T08:00:00Z",
                                        "totalMinutes": 450,
                                        "originPackage": "com.google.android.apps.fitness"
                                      }
                                    }
                                  ]
                                }
                                """
                        )
                    }
                )
            )
            HealthEventsRequest request,
            @RequestAttribute("deviceId") String deviceId
    ) {
        log.info("Received batch of {} events from device: {}", request.getEvents().size(), deviceId);
        
        StoreHealthEventsCommand command = HealthEventsMapper.toCommand(request, deviceId);
        var result = healthEventsFacade.storeHealthEvents(command);
        HealthEventsResponse response = HealthEventsMapper.toResponse(result);
        
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(response);
    }
}
