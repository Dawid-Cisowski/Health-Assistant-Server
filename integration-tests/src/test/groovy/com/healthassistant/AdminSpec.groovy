package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Isolated

/**
 * Admin endpoints that delete ALL data - must run isolated (sequentially)
 * to avoid interfering with parallel tests.
 */
@Isolated
class AdminSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-admin"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "DELETE /v1/admin/all-data should delete all data from database"() {
        given: "some health events exist in the database"
        def event1 = createStepsEvent("test-key-1", "2025-11-10T07:00:00Z", DEVICE_ID)
        def event2 = createHeartRateEvent("test-key-2", "2025-11-10T07:15:00Z", DEVICE_ID)
        def event3 = createSleepSessionEvent("test-key-3", "2025-11-10T08:00:00Z", "sleep-session-admin-1", DEVICE_ID)

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", event1)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", event2)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", event3)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "wait for projections to be created"
        waitForApiResponse("/v1/steps/daily/2025-11-10", DEVICE_ID, SECRET_BASE64)
        waitForApiResponse("/v1/sleep/daily/2025-11-10", DEVICE_ID, SECRET_BASE64)

        when: "DELETE /v1/admin/all-data is called"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        then: "the response should be 204 No Content"
        response.statusCode() == 204

        and: "all data should be deleted (verified via API returning 404)"
        apiReturns404("/v1/steps/daily/2025-11-10", DEVICE_ID, SECRET_BASE64)
        apiReturns404("/v1/sleep/daily/2025-11-10", DEVICE_ID, SECRET_BASE64)
        apiReturns404("/v1/daily-summaries/2025-11-10", DEVICE_ID, SECRET_BASE64)
    }

    def "DELETE /v1/admin/all-data should be idempotent"() {
        given: "some health events exist"
        def event = createStepsEvent("test-key-2", "2025-11-10T07:00:00Z", DEVICE_ID)
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", event)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "wait for projections"
        waitForApiResponse("/v1/steps/daily/2025-11-10", DEVICE_ID, SECRET_BASE64)

        when: "DELETE /v1/admin/all-data is called twice"
        def response1 = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        def response2 = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        then: "both calls should succeed"
        response1.statusCode() == 204
        response2.statusCode() == 204

        and: "database should be empty (verified via API returning 404)"
        apiReturns404("/v1/steps/daily/2025-11-10", DEVICE_ID, SECRET_BASE64)
    }

    def "DELETE /v1/admin/all-data should delete projections and summaries"() {
        given: "workout events are stored"
        def workoutEvent = """
        {
            "events": [
                {
                    "idempotencyKey": "workout-key-1",
                    "type": "WorkoutRecorded.v1",
                    "occurredAt": "2025-11-10T10:00:00Z",
                    "payload": {
                        "workoutId": "workout-123",
                        "performedAt": "2025-11-10T10:00:00Z",
                        "source": "MANUAL",
                        "note": "Morning workout",
                        "exercises": [
                            {
                                "name": "Bench Press",
                                "muscleGroup": "Chest",
                                "orderInWorkout": 1,
                                "sets": [
                                    {
                                        "setNumber": 1,
                                        "reps": 10,
                                        "weightKg": 80.0,
                                        "isWarmup": false
                                    }
                                ]
                            }
                        ]
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", workoutEvent)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "wait for projections to be created"
        waitForApiResponse("/v1/workouts/workout-123", DEVICE_ID, SECRET_BASE64)

        when: "DELETE /v1/admin/all-data is called"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        then: "all projections should be deleted (verified via API returning 404)"
        response.statusCode() == 204
        apiReturns404("/v1/workouts/workout-123", DEVICE_ID, SECRET_BASE64)
    }

    def "DELETE /v1/admin/all-data should work when database is already empty"() {
        given: "database is empty (verified via API returning 404)"
        apiReturns404("/v1/steps/daily/2025-11-10", DEVICE_ID, SECRET_BASE64)

        when: "DELETE /v1/admin/all-data is called"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        then: "should return 204 No Content"
        response.statusCode() == 204

        and: "database should remain empty (verified via API returning 404)"
        apiReturns404("/v1/steps/daily/2025-11-10", DEVICE_ID, SECRET_BASE64)
        apiReturns404("/v1/daily-summaries/2025-11-10", DEVICE_ID, SECRET_BASE64)
    }

    def "DELETE /v1/admin/all-data should require HMAC authentication"() {
        when: "DELETE /v1/admin/all-data is called without authentication"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/v1/admin/all-data")

        then: "should return 401 Unauthorized"
        response.statusCode() == 401

        and: "response should contain error details"
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
    }
}
