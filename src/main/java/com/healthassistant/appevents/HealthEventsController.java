package com.healthassistant.appevents;

import com.healthassistant.appevents.api.AppEventsFacade;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsRequest;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsResponse;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/health-events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health Events", description = "Generic health event ingestion endpoints for mobile applications")
class HealthEventsController {

    private final AppEventsFacade appEventsFacade;
    private final HealthEventsResponseMapper responseMapper;

    @PostMapping
    @Operation(
            summary = "Submit health events",
            description = """
                    Accepts health events from mobile applications. Requires HMAC authentication.

                    ## Supported Event Types

                    | Event Type | Description |
                    |------------|-------------|
                    | `StepsBucketedRecorded.v1` | Steps count in a time bucket |
                    | `DistanceBucketRecorded.v1` | Distance traveled in a time bucket |
                    | `HeartRateSummaryRecorded.v1` | Heart rate summary for a time bucket |
                    | `SleepSessionRecorded.v1` | Sleep session data |
                    | `ActiveCaloriesBurnedRecorded.v1` | Active calories burned in a time bucket |
                    | `ActiveMinutesRecorded.v1` | Active minutes in a time bucket |
                    | `WalkingSessionRecorded.v1` | Walking/running session |
                    | `WorkoutRecorded.v1` | Gym workout with exercises and sets |
                    | `MealRecorded.v1` | Meal/nutrition entry |

                    ## Idempotency

                    Each event requires an `idempotencyKey` for deduplication. If not provided, it will be auto-generated as:
                    - For workouts: `{deviceId}|workout|{workoutId}`
                    - For other events: `{deviceId}|{eventType}|{occurredAt}-{index}`

                    ## Modifying Events

                    Events are immutable. To modify an existing event:
                    - **Delete**: Submit `EventDeleted.v1` with `targetEventId`
                    - **Correct**: Submit `EventCorrected.v1` with `targetEventId` and corrected data

                    Submitting with the same `idempotencyKey` will NOT update the event - use compensation events instead.

                    ## Response Status Codes

                    - `stored` - Event was successfully stored
                    - `duplicate` - Event with same idempotencyKey already exists (use EventCorrected.v1 to modify)
                    - `invalid` - Event failed validation
                    """,
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Events processed - check individual event results for status"),
            @ApiResponse(responseCode = "400", description = "Invalid request - empty events list"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<SubmitHealthEventsResponse> submitHealthEvents(
            @RequestBody @Valid SubmitHealthEventsRequest request) {

        log.info("Received {} health events from device: {}",
                request.events().size(),
                request.deviceId() != null ? request.deviceId() : "mobile-app");

        StoreHealthEventsResult result = appEventsFacade.submitHealthEvents(request);
        SubmitHealthEventsResponse response = responseMapper.toResponse(result, request.events().size());

        return ResponseEntity.ok(response);
    }
}
