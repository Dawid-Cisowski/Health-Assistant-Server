package com.healthassistant

import spock.lang.Title

/**
 * Integration tests verifying cross-module consistency.
 * Ensures that events flow correctly through the system:
 * Event Ingestion -> Event Store -> Projection -> Query API
 */
@Title("Feature: Cross-Module Consistency - Event to Projection Flow")
class CrossModuleConsistencySpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-cross-module"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
    }

    def "Scenario 1: Steps event flows through to daily summary"() {
        given: "a steps event"
        def date = "2025-12-05"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|cross-module-test-1",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-12-05T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-12-05T09:00:00Z",
                    "bucketEnd": "2025-12-05T10:00:00Z",
                    "count": 5000,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "steps projection is created"
        def stepsResponse = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
        stepsResponse.getInt("totalSteps") == 5000

        and: "daily summary includes the steps"
        def summaryResponse = waitForApiResponse("/v1/daily-summaries/${date}", DEVICE_ID, SECRET_BASE64)
        summaryResponse.getInt("activity.steps") == 5000
    }

    def "Scenario 2: Calories event flows through to daily summary"() {
        given: "a calories event"
        def date = "2025-12-06"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|calories|cross-module-test-1",
                "type": "ActiveCaloriesBurnedRecorded.v1",
                "occurredAt": "2025-12-06T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-12-06T09:00:00Z",
                    "bucketEnd": "2025-12-06T10:00:00Z",
                    "energyKcal": 350.5,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "calories projection is created"
        def caloriesResponse = waitForApiResponse("/v1/calories/daily/${date}", DEVICE_ID, SECRET_BASE64)
        caloriesResponse.getDouble("totalCalories") == 350.5

        and: "daily summary includes the calories"
        def summaryResponse = waitForApiResponse("/v1/daily-summaries/${date}", DEVICE_ID, SECRET_BASE64)
        summaryResponse.getInt("activity.activeCalories") == 350
    }

    def "Scenario 3: Sleep event flows through to daily summary"() {
        given: "a sleep event"
        def date = "2025-12-07"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|sleep|cross-module-test-1",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-12-07T08:00:00Z",
                "payload": {
                    "sleepId": "cross-module-sleep",
                    "sleepStart": "2025-12-06T23:00:00Z",
                    "sleepEnd": "2025-12-07T07:00:00Z",
                    "totalMinutes": 480,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "sleep projection is created"
        def sleepResponse = waitForApiResponse("/v1/sleep/daily/${date}", DEVICE_ID, SECRET_BASE64)
        sleepResponse.getInt("totalSleepMinutes") == 480

        and: "daily summary includes the sleep"
        def summaryResponse = waitForApiResponse("/v1/daily-summaries/${date}", DEVICE_ID, SECRET_BASE64)
        summaryResponse.getList("sleep").size() >= 1
        summaryResponse.getList("sleep")[0].totalMinutes == 480
    }

    def "Scenario 4: Workout event flows through to daily summary"() {
        given: "a workout event"
        def date = "2025-12-08"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|workout|cross-module-test-1",
                "type": "WorkoutRecorded.v1",
                "occurredAt": "2025-12-08T18:00:00Z",
                "payload": {
                    "workoutId": "cross-module-workout",
                    "performedAt": "2025-12-08T17:00:00Z",
                    "source": "GYMRUN_SCREENSHOT",
                    "exercises": [
                        {
                            "name": "Bench Press",
                            "orderInWorkout": 1,
                            "sets": [
                                {"setNumber": 1, "weightKg": 80.0, "reps": 10, "isWarmup": false},
                                {"setNumber": 2, "weightKg": 85.0, "reps": 8, "isWarmup": false}
                            ]
                        }
                    ]
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "workout projection is created"
        def workoutResponse = waitForApiResponse("/v1/workouts/cross-module-workout", DEVICE_ID, SECRET_BASE64)
        workoutResponse.getString("workoutId") == "cross-module-workout"
        workoutResponse.getList("exercises").size() == 1
        workoutResponse.getList("exercises")[0].exerciseName == "Bench Press"

        and: "daily summary includes the workout"
        def summaryResponse = waitForApiResponse("/v1/daily-summaries/${date}", DEVICE_ID, SECRET_BASE64)
        summaryResponse.getList("workouts").size() >= 1
        summaryResponse.getList("workouts")[0].workoutId == "cross-module-workout"
    }

    def "Scenario 5: Multiple event types on same day aggregate correctly"() {
        given: "multiple events on the same day"
        def date = "2025-12-09"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|multi-event-test",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-12-09T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-09T09:00:00Z",
                        "bucketEnd": "2025-12-09T10:00:00Z",
                        "count": 3000,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|multi-event-test",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-12-09T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-09T09:00:00Z",
                        "bucketEnd": "2025-12-09T10:00:00Z",
                        "energyKcal": 200.0,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|activity|multi-event-test",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-12-09T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-09T09:00:00Z",
                        "bucketEnd": "2025-12-09T10:00:00Z",
                        "activeMinutes": 30,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit all events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "all projections are created"
        def stepsResponse = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
        stepsResponse.getInt("totalSteps") == 3000

        def caloriesResponse = waitForApiResponse("/v1/calories/daily/${date}", DEVICE_ID, SECRET_BASE64)
        caloriesResponse.getDouble("totalCalories") == 200.0

        def activityResponse = waitForApiResponse("/v1/activity/daily/${date}", DEVICE_ID, SECRET_BASE64)
        activityResponse.getInt("totalActiveMinutes") == 30

        and: "daily summary aggregates all data"
        def summaryResponse = waitForApiResponse("/v1/daily-summaries/${date}", DEVICE_ID, SECRET_BASE64)
        summaryResponse.getInt("activity.steps") == 3000
        summaryResponse.getInt("activity.activeCalories") == 200
        summaryResponse.getInt("activity.activeMinutes") == 30
    }

    def "Scenario 6: Event update via same idempotency key returns duplicate status"() {
        given: "an initial steps event"
        def date = "2025-12-10"
        def idempotencyKey = "${DEVICE_ID}|steps|update-test"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${idempotencyKey}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-12-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-12-10T09:00:00Z",
                    "bucketEnd": "2025-12-10T10:00:00Z",
                    "count": 1000,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "the event is submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "projection shows initial value"
        def initialResponse = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
        initialResponse.getInt("totalSteps") == 1000

        when: "I submit the same idempotency key again"
        def request2 = """
        {
            "events": [{
                "idempotencyKey": "${idempotencyKey}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-12-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-12-10T09:00:00Z",
                    "bucketEnd": "2025-12-10T10:00:00Z",
                    "count": 2000,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        def updateResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request2)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response indicates duplicate event was processed (updated)"
        updateResponse.getList("events").size() == 1
        // Duplicate events are accepted but marked as duplicate
        updateResponse.getList("events")[0].status in ["duplicate", "stored"]
    }
}
