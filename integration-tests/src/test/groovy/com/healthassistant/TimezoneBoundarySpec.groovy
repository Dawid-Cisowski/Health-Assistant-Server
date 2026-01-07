package com.healthassistant

import spock.lang.Title

/**
 * Integration tests for timezone boundary handling.
 * The application uses Poland timezone (Europe/Warsaw) for daily boundaries.
 * These tests verify correct date assignment around midnight.
 */
@Title("Feature: Timezone Boundary Handling - Warsaw Timezone")
class TimezoneBoundarySpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-timezone"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
    }

    def "Scenario 1: Event at 23:30 UTC in winter is assigned to next day in Warsaw (CET = UTC+1)"() {
        given: "a steps event at 23:30 UTC on November 20th"
        // 23:30 UTC = 00:30 Warsaw (CET, winter time)
        // Should be assigned to November 21st in Warsaw
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|winter-midnight-test",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-20T23:30:00Z",
                "payload": {
                    "bucketStart": "2025-11-20T23:00:00Z",
                    "bucketEnd": "2025-11-20T23:30:00Z",
                    "count": 500,
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

        then: "steps are assigned to November 21st (Warsaw time)"
        def response = waitForApiResponse("/v1/steps/daily/2025-11-21", DEVICE_ID, SECRET_BASE64)
        response.getInt("totalSteps") == 500

        and: "November 20th has no steps"
        apiReturns404("/v1/steps/daily/2025-11-20", DEVICE_ID, SECRET_BASE64)
    }

    def "Scenario 2: Event at 22:30 UTC in winter stays on same day in Warsaw"() {
        given: "a steps event at 22:30 UTC on November 20th"
        // 22:30 UTC = 23:30 Warsaw (CET, winter time)
        // Should be assigned to November 20th in Warsaw
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|winter-before-midnight-test",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-20T22:30:00Z",
                "payload": {
                    "bucketStart": "2025-11-20T22:00:00Z",
                    "bucketEnd": "2025-11-20T22:30:00Z",
                    "count": 300,
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

        then: "steps are assigned to November 20th (Warsaw time)"
        def response = waitForApiResponse("/v1/steps/daily/2025-11-20", DEVICE_ID, SECRET_BASE64)
        response.getInt("totalSteps") == 300
    }

    def "Scenario 3: Event at 22:30 UTC in summer is assigned to next day in Warsaw (CEST = UTC+2)"() {
        given: "a steps event at 22:30 UTC on July 15th"
        // 22:30 UTC = 00:30 Warsaw (CEST, summer time)
        // Should be assigned to July 16th in Warsaw
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|summer-midnight-test",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-07-15T22:30:00Z",
                "payload": {
                    "bucketStart": "2025-07-15T22:00:00Z",
                    "bucketEnd": "2025-07-15T22:30:00Z",
                    "count": 400,
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

        then: "steps are assigned to July 16th (Warsaw time)"
        def response = waitForApiResponse("/v1/steps/daily/2025-07-16", DEVICE_ID, SECRET_BASE64)
        response.getInt("totalSteps") == 400

        and: "July 15th has no steps"
        apiReturns404("/v1/steps/daily/2025-07-15", DEVICE_ID, SECRET_BASE64)
    }

    def "Scenario 4: Sleep session spanning midnight is assigned to wake-up date"() {
        given: "a sleep session from 22:00 UTC to 06:00 UTC next day"
        // Sleep: 22:00 UTC Nov 20 -> 06:00 UTC Nov 21
        // In Warsaw: 23:00 Nov 20 -> 07:00 Nov 21
        // Should be assigned to wake-up date (Nov 21)
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|sleep|midnight-span-test",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-11-21T06:00:00Z",
                "payload": {
                    "sleepId": "midnight-span-sleep",
                    "sleepStart": "2025-11-20T22:00:00Z",
                    "sleepEnd": "2025-11-21T06:00:00Z",
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

        then: "sleep is assigned to November 21st (wake-up date)"
        def response = waitForApiResponse("/v1/sleep/daily/2025-11-21", DEVICE_ID, SECRET_BASE64)
        response.getInt("totalSleepMinutes") == 480
    }

    def "Scenario 5: Workout performed at 23:30 UTC in winter is assigned to next day in Warsaw"() {
        given: "a workout at 23:30 UTC on November 20th"
        // 23:30 UTC = 00:30 Warsaw (CET, winter time)
        // Should be assigned to November 21st in Warsaw
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|workout|winter-midnight-workout",
                "type": "WorkoutRecorded.v1",
                "occurredAt": "2025-11-20T23:30:00Z",
                "payload": {
                    "workoutId": "midnight-workout",
                    "performedAt": "2025-11-20T23:30:00Z",
                    "source": "GYMRUN_SCREENSHOT",
                    "exercises": [
                        {
                            "name": "Bench Press",
                            "orderInWorkout": 1,
                            "sets": [{"setNumber": 1, "weightKg": 80.0, "reps": 10, "isWarmup": false}]
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

        then: "workout is assigned to November 21st (Warsaw time)"
        def response = waitForApiResponse("/v1/workouts/midnight-workout", DEVICE_ID, SECRET_BASE64)
        response.getString("performedDate") == "2025-11-21"
    }

    def "Scenario 6: Range query respects Warsaw timezone boundaries"() {
        given: "steps events around midnight Warsaw time"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|range-test-before-midnight",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-22T22:30:00Z",
                    "payload": {
                        "bucketStart": "2025-11-22T22:00:00Z",
                        "bucketEnd": "2025-11-22T22:30:00Z",
                        "count": 1000,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|range-test-after-midnight",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-22T23:30:00Z",
                    "payload": {
                        "bucketStart": "2025-11-22T23:00:00Z",
                        "bucketEnd": "2025-11-22T23:30:00Z",
                        "count": 500,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit both events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "first event (22:30 UTC = 23:30 Warsaw) is on Nov 22"
        def nov22Response = waitForApiResponse("/v1/steps/daily/2025-11-22", DEVICE_ID, SECRET_BASE64)
        nov22Response.getInt("totalSteps") == 1000

        and: "second event (23:30 UTC = 00:30 Warsaw) is on Nov 23"
        def nov23Response = waitForApiResponse("/v1/steps/daily/2025-11-23", DEVICE_ID, SECRET_BASE64)
        nov23Response.getInt("totalSteps") == 500

        and: "range query returns correct daily breakdown"
        def rangeResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/steps/range?startDate=2025-11-22&endDate=2025-11-23")
                .get("/v1/steps/range?startDate=2025-11-22&endDate=2025-11-23")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        def dailyStats = rangeResponse.getList("dailyStats")
        dailyStats.size() == 2
        dailyStats[0].date == "2025-11-22"
        dailyStats[0].totalSteps == 1000
        dailyStats[1].date == "2025-11-23"
        dailyStats[1].totalSteps == 500
    }
}
