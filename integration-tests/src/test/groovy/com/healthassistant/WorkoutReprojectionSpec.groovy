package com.healthassistant

import spock.lang.Title

import java.time.Instant
import java.time.LocalDate

@Title("Feature: Workout Reprojection API")
class WorkoutReprojectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-reproject"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        cleanupProjectionsForDateRange(DEVICE_ID, LocalDate.of(2025, 11, 1), LocalDate.of(2025, 12, 31))
    }

    def "Scenario 1: Reproject all workouts for device"() {
        given: "multiple workout events exist"
        def workout1 = createWorkoutEvent("reproject-workout-1", "2025-11-15T10:00:00Z")
        def workout2 = createWorkoutEvent("reproject-workout-2", "2025-11-16T10:00:00Z")
        def request = """
        {
            "events": [${workout1}, ${workout2}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "projections exist"
        waitForApiResponse("/v1/workouts/reproject-workout-1", DEVICE_ID, SECRET_BASE64)
        waitForApiResponse("/v1/workouts/reproject-workout-2", DEVICE_ID, SECRET_BASE64)

        when: "I call the reproject endpoint"
        def response = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/reproject")
                .post("/v1/workouts/reproject")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "reprojection response indicates success"
        response.getInt("reprojectedCount") == 2
        response.getInt("failedCount") == 0
        response.getInt("totalEventsProcessed") == 2

        and: "workouts are still accessible after reprojection"
        def workout1Response = waitForApiResponse("/v1/workouts/reproject-workout-1", DEVICE_ID, SECRET_BASE64)
        workout1Response.getString("workoutId") == "reproject-workout-1"

        def workout2Response = waitForApiResponse("/v1/workouts/reproject-workout-2", DEVICE_ID, SECRET_BASE64)
        workout2Response.getString("workoutId") == "reproject-workout-2"
    }

    def "Scenario 2: Reprojection excludes deleted events"() {
        given: "a workout event exists"
        def workoutKey = "reproject-deleted-key-" + UUID.randomUUID()
        def workoutBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${workoutKey}",
                    "type": "WorkoutRecorded.v1",
                    "occurredAt": "2025-11-20T18:00:00Z",
                    "payload": {
                        "workoutId": "reproject-deleted-workout",
                        "performedAt": "2025-11-20T18:00:00Z",
                        "source": "GYMRUN_SCREENSHOT",
                        "exercises": [
                            {
                                "name": "Bench Press",
                                "muscleGroup": "Chest",
                                "orderInWorkout": 1,
                                "sets": [
                                    {"setNumber": 1, "weightKg": 80.0, "reps": 10, "isWarmup": false}
                                ]
                            }
                        ]
                    }
                }
            ]
        }
        """

        def workoutResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", workoutBody)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
                .extract()

        def eventId = workoutResponse.body().jsonPath().getString("events[0].eventId")

        and: "the workout is projected"
        waitForApiResponse("/v1/workouts/reproject-deleted-workout", DEVICE_ID, SECRET_BASE64)

        and: "the event is deleted"
        def deleteKey = "delete-" + UUID.randomUUID()
        def deleteBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${deleteKey}",
                    "type": "EventDeleted.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "${eventId}",
                        "reason": "User requested deletion"
                    }
                }
            ]
        }
        """

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", deleteBody)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I call the reproject endpoint"
        def response = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/reproject")
                .post("/v1/workouts/reproject")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "reprojection response indicates no workouts were processed"
        response.getInt("reprojectedCount") == 0
        response.getInt("failedCount") == 0
        response.getInt("totalEventsProcessed") == 0

        and: "deleted workout is not accessible after reprojection"
        authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/reproject-deleted-workout")
                .get("/v1/workouts/reproject-deleted-workout")
                .then()
                .statusCode(404)
    }

    def "Scenario 3: Reprojection handles empty event list gracefully"() {
        given: "no workout events exist for the device"
        // Already cleaned up in setup()

        when: "I call the reproject endpoint"
        def response = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/reproject")
                .post("/v1/workouts/reproject")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "reprojection response indicates zero events processed"
        response.getInt("reprojectedCount") == 0
        response.getInt("failedCount") == 0
        response.getInt("totalEventsProcessed") == 0
    }

    def "Scenario 4: Reprojection is isolated to requesting device only"() {
        given: "workouts exist for two different devices"
        def workout1 = createWorkoutEvent("device1-isolated-workout", "2025-11-21T10:00:00Z")
        def request1 = """
        {
            "events": [${workout1}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        def workout2 = """
        {
            "idempotencyKey": "different-device-id|workout|device2-isolated-workout",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "workoutId": "device2-isolated-workout",
                "performedAt": "2025-11-21T11:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "name": "Squat",
                        "muscleGroup": "Legs",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 100.0, "reps": 10, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request2 = """
        {
            "events": [${workout2}],
            "deviceId": "different-device-id"
        }
        """

        authenticatedPostRequestWithBody("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, "/v1/health-events", request2)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "both projections exist"
        waitForApiResponse("/v1/workouts/device1-isolated-workout", DEVICE_ID, SECRET_BASE64)
        waitForApiResponse("/v1/workouts/device2-isolated-workout", "different-device-id", DIFFERENT_DEVICE_SECRET_BASE64)

        when: "device 1 calls the reproject endpoint"
        def response = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/reproject")
                .post("/v1/workouts/reproject")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "only device 1's workout is reprojected"
        response.getInt("reprojectedCount") == 1
        response.getInt("totalEventsProcessed") == 1

        and: "device 1's workout is still accessible"
        def workout1Response = waitForApiResponse("/v1/workouts/device1-isolated-workout", DEVICE_ID, SECRET_BASE64)
        workout1Response.getString("workoutId") == "device1-isolated-workout"

        and: "device 2's workout is unaffected and still accessible"
        def workout2Response = waitForApiResponse("/v1/workouts/device2-isolated-workout", "different-device-id", DIFFERENT_DEVICE_SECRET_BASE64)
        workout2Response.getString("workoutId") == "device2-isolated-workout"
    }

    def "Scenario 5: Reprojection requires HMAC authentication"() {
        when: "I call reproject endpoint without authentication"
        def response = io.restassured.RestAssured.given()
                .contentType("application/json")
                .post("/v1/workouts/reproject")
                .then()
                .extract()

        then: "request is rejected"
        response.statusCode() == 401
    }

    // Helper methods

    String createWorkoutEvent(String workoutId, String performedAt = "2025-11-17T18:00:00Z") {
        return """
        {
            "idempotencyKey": "${DEVICE_ID}|workout|${workoutId}",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "${performedAt}",
            "payload": {
                "workoutId": "${workoutId}",
                "performedAt": "${performedAt}",
                "source": "GYMRUN_SCREENSHOT",
                "note": "Test workout",
                "exercises": [
                    {
                        "exerciseId": "chest_1",
                        "name": "Bench Press",
                        "muscleGroup": "Chest",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 80.0, "reps": 10, "isWarmup": false},
                            {"setNumber": 2, "weightKg": 85.0, "reps": 8, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """.stripIndent().trim()
    }
}
