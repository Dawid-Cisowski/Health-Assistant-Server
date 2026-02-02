package com.healthassistant.appevents.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

@Schema(description = "Request to submit health events from mobile applications")
public record SubmitHealthEventsRequest(
    @JsonProperty("events")
    @NotNull(message = "Events list is required")
    @Size(min = 1, max = 1000, message = "Events list must contain between 1 and 1000 events")
    @Schema(description = "List of health events to submit")
    List<HealthEventRequest> events,

    @JsonProperty("deviceId")
    @Size(max = 255, message = "Device ID must not exceed 255 characters")
    @Schema(description = "Source device/app identifier (optional, defaults to 'mobile-app')",
            example = "gymrun-app",
            nullable = true)
    String deviceId
) {

    @Schema(description = """
            Single health event.

            ## Event Types and Payload Schemas

            ### StepsBucketedRecorded.v1
            Steps count in a time bucket (usually 1 hour).
            ```json
            {
              "idempotencyKey": "mobile-app|StepsBucketedRecorded.v1|2025-11-21T10:00:00Z",
              "type": "StepsBucketedRecorded.v1",
              "occurredAt": "2025-11-21T10:00:00Z",
              "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "count": 1500,
                "originPackage": "com.google.android.apps.fitness"
              }
            }
            ```

            ### DistanceBucketRecorded.v1
            Distance traveled in a time bucket.
            ```json
            {
              "type": "DistanceBucketRecorded.v1",
              "occurredAt": "2025-11-21T10:00:00Z",
              "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "distanceMeters": 1250.5,
                "originPackage": "com.google.android.apps.fitness"
              }
            }
            ```

            ### HeartRateSummaryRecorded.v1
            Heart rate summary for a time bucket.
            ```json
            {
              "type": "HeartRateSummaryRecorded.v1",
              "occurredAt": "2025-11-21T10:00:00Z",
              "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "avg": 75.5,
                "min": 62.0,
                "max": 95.0,
                "samples": 60,
                "originPackage": "com.google.android.apps.fitness"
              }
            }
            ```

            ### SleepSessionRecorded.v1
            Sleep session data.
            ```json
            {
              "type": "SleepSessionRecorded.v1",
              "occurredAt": "2025-11-21T06:30:00Z",
              "payload": {
                "sleepId": "sleep-2025-11-21",
                "sleepStart": "2025-11-20T23:00:00Z",
                "sleepEnd": "2025-11-21T06:30:00Z",
                "totalMinutes": 450,
                "originPackage": "com.google.android.apps.fitness"
              }
            }
            ```

            ### ActiveCaloriesBurnedRecorded.v1
            Active calories burned in a time bucket.
            ```json
            {
              "type": "ActiveCaloriesBurnedRecorded.v1",
              "occurredAt": "2025-11-21T10:00:00Z",
              "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "energyKcal": 150.5,
                "originPackage": "com.google.android.apps.fitness"
              }
            }
            ```

            ### ActiveMinutesRecorded.v1
            Active minutes in a time bucket.
            ```json
            {
              "type": "ActiveMinutesRecorded.v1",
              "occurredAt": "2025-11-21T10:00:00Z",
              "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "activeMinutes": 35,
                "originPackage": "com.google.android.apps.fitness"
              }
            }
            ```

            ### WalkingSessionRecorded.v1
            Walking or running session.
            ```json
            {
              "type": "WalkingSessionRecorded.v1",
              "occurredAt": "2025-11-21T07:00:00Z",
              "payload": {
                "sessionId": "walk-2025-11-21-morning",
                "start": "2025-11-21T07:00:00Z",
                "end": "2025-11-21T07:45:00Z",
                "durationMinutes": 45,
                "totalSteps": 5000,
                "totalDistanceMeters": 4200.0,
                "totalCalories": 250.0,
                "avgHeartRate": 95.0,
                "maxHeartRate": 120.0,
                "originPackage": "com.google.android.apps.fitness"
              }
            }
            ```
            Note: totalSteps, totalDistanceMeters, totalCalories, avgHeartRate, maxHeartRate are optional.

            ### WorkoutRecorded.v1
            Gym workout with exercises and sets.
            ```json
            {
              "type": "WorkoutRecorded.v1",
              "occurredAt": "2025-11-21T18:00:00Z",
              "payload": {
                "workoutId": "gymrun-2025-11-21-1",
                "performedAt": "2025-11-21T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "note": "Back and biceps",
                "exercises": [
                  {
                    "name": "Pull-ups",
                    "muscleGroup": "Back",
                    "orderInWorkout": 1,
                    "sets": [
                      {"setNumber": 1, "weightKg": 0, "reps": 12, "isWarmup": true},
                      {"setNumber": 2, "weightKg": 10, "reps": 10, "isWarmup": false},
                      {"setNumber": 3, "weightKg": 10, "reps": 8, "isWarmup": false}
                    ]
                  },
                  {
                    "name": "Barbell Row",
                    "muscleGroup": "Back",
                    "orderInWorkout": 2,
                    "sets": [
                      {"setNumber": 1, "weightKg": 60, "reps": 12, "isWarmup": false},
                      {"setNumber": 2, "weightKg": 70, "reps": 10, "isWarmup": false}
                    ]
                  }
                ]
              }
            }
            ```
            **Required exercise fields:** name, orderInWorkout, sets
            **Optional exercise fields:** muscleGroup
            **Required set fields:** setNumber, weightKg, reps, isWarmup
            **Source values:** GYMRUN_SCREENSHOT, MANUAL, IMPORT, etc.

            ### MealRecorded.v1
            Meal/nutrition entry.
            ```json
            {
              "type": "MealRecorded.v1",
              "occurredAt": "2025-11-21T12:30:00Z",
              "payload": {
                "title": "Grilled chicken salad",
                "mealType": "LUNCH",
                "caloriesKcal": 450,
                "proteinGrams": 35,
                "fatGrams": 15,
                "carbohydratesGrams": 30,
                "healthRating": "HEALTHY"
              }
            }
            ```
            **mealType values:** BREAKFAST, BRUNCH, LUNCH, DINNER, SNACK, DESSERT, DRINK
            **healthRating values:** VERY_HEALTHY, HEALTHY, NEUTRAL, UNHEALTHY, VERY_UNHEALTHY
            """)
    @JsonDeserialize(using = HealthEventRequestDeserializer.class)
    public record HealthEventRequest(
        @JsonProperty("idempotencyKey")
        @Schema(description = """
                Unique key for idempotency (optional).
                If not provided, will be auto-generated as:
                - For workouts: `{deviceId}|workout|{workoutId}`
                - For other events: `{deviceId}|{eventType}|{occurredAt}-{index}`
                """,
                example = "gymrun-2025-11-17-1",
                nullable = true)
        String idempotencyKey,

        @JsonProperty("type")
        @Schema(description = """
                Event type identifier. Supported types:
                - StepsBucketedRecorded.v1
                - DistanceBucketRecorded.v1
                - HeartRateSummaryRecorded.v1
                - SleepSessionRecorded.v1
                - ActiveCaloriesBurnedRecorded.v1
                - ActiveMinutesRecorded.v1
                - WalkingSessionRecorded.v1
                - WorkoutRecorded.v1
                - MealRecorded.v1
                """,
                example = "WorkoutRecorded.v1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String type,

        @JsonProperty("occurredAt")
        @Schema(description = "When the event occurred (ISO-8601 UTC timestamp)",
                example = "2025-11-17T18:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant occurredAt,

        @JsonProperty("payload")
        @Valid
        @Schema(description = "Event-specific data. Structure depends on event type - see HealthEventRequest description for payload schemas.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        EventPayload payload,

        @Schema(hidden = true)
        String deserializationError
    ) {
        /**
         * Constructor without deserializationError for backward compatibility.
         */
        public HealthEventRequest(String idempotencyKey, String type, Instant occurredAt, EventPayload payload) {
            this(idempotencyKey, type, occurredAt, payload, null);
        }
    }
}
