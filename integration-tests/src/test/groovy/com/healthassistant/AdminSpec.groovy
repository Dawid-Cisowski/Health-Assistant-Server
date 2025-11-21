package com.healthassistant

import com.healthassistant.dailysummary.DailySummaryJpaRepository
import com.healthassistant.googlefit.GoogleFitSyncStateRepository
import com.healthassistant.sleep.SleepDailyProjectionJpaRepository
import com.healthassistant.sleep.SleepSessionProjectionJpaRepository
import com.healthassistant.steps.StepsDailyProjectionJpaRepository
import com.healthassistant.steps.StepsHourlyProjectionJpaRepository
import com.healthassistant.workout.WorkoutExerciseProjectionJpaRepository
import com.healthassistant.workout.WorkoutProjectionJpaRepository
import com.healthassistant.workout.WorkoutSetProjectionJpaRepository
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired

class AdminSpec extends BaseIntegrationSpec {

    @Autowired
    DailySummaryJpaRepository dailySummaryRepository

    @Autowired
    StepsHourlyProjectionJpaRepository stepsHourlyProjectionRepository

    @Autowired
    StepsDailyProjectionJpaRepository stepsDailyProjectionRepository

    @Autowired
    WorkoutProjectionJpaRepository workoutProjectionRepository

    @Autowired
    WorkoutExerciseProjectionJpaRepository workoutExerciseProjectionRepository

    @Autowired
    WorkoutSetProjectionJpaRepository workoutSetProjectionRepository

    @Autowired
    SleepSessionProjectionJpaRepository sleepSessionProjectionRepository

    @Autowired
    SleepDailyProjectionJpaRepository sleepDailyProjectionRepository

    @Autowired
    GoogleFitSyncStateRepository googleFitSyncStateRepository

    def "DELETE /v1/admin/all-data should delete all data from database"() {
        given: "some health events exist in the database"
        def event1 = createStepsEvent("test-key-1", "2025-11-10T07:00:00Z")
        def event2 = createHeartRateEvent("test-key-2", "2025-11-10T07:15:00Z")
        def event3 = createSleepSessionEvent("test-key-3", "2025-11-10T08:00:00Z")

        authenticatedPostRequestWithBody("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/health-events", event1)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        authenticatedPostRequestWithBody("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/health-events", event2)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        authenticatedPostRequestWithBody("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/health-events", event3)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "verify data exists in all tables"
        eventRepository.count() > 0

        when: "DELETE /v1/admin/all-data is called"
        def response = authenticatedDeleteRequest("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        then: "the response should be 204 No Content"
        response.statusCode() == 204

        and: "all data should be deleted from all tables"
        eventRepository.count() == 0
        dailySummaryRepository.count() == 0
        stepsHourlyProjectionRepository.count() == 0
        stepsDailyProjectionRepository.count() == 0
        workoutProjectionRepository.count() == 0
        workoutExerciseProjectionRepository.count() == 0
        workoutSetProjectionRepository.count() == 0
        sleepSessionProjectionRepository.count() == 0
        sleepDailyProjectionRepository.count() == 0
        googleFitSyncStateRepository.count() == 0
    }

    def "DELETE /v1/admin/all-data should be idempotent"() {
        given: "some health events exist"
        def event = createStepsEvent("test-key-1", "2025-11-10T07:00:00Z")
        authenticatedPostRequestWithBody("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/health-events", event)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "DELETE /v1/admin/all-data is called twice"
        def response1 = authenticatedDeleteRequest("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        def response2 = authenticatedDeleteRequest("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        then: "both calls should succeed"
        response1.statusCode() == 204
        response2.statusCode() == 204

        and: "database should be empty"
        eventRepository.count() == 0
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
                        "note": "Morning workout",
                        "exercises": [
                            {
                                "name": "Bench Press",
                                "sets": [
                                    {
                                        "reps": 10,
                                        "weightKg": 80.0
                                    }
                                ]
                            }
                        ]
                    }
                }
            ]
        }
        """

        authenticatedPostRequestWithBody("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/health-events", workoutEvent)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        // Wait for projections to be created
        Thread.sleep(500)

        and: "verify projections were created"
        workoutProjectionRepository.count() > 0

        when: "DELETE /v1/admin/all-data is called"
        def response = authenticatedDeleteRequest("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        then: "all projections should be deleted"
        response.statusCode() == 204
        workoutProjectionRepository.count() == 0
        workoutExerciseProjectionRepository.count() == 0
        workoutSetProjectionRepository.count() == 0
    }

    def "DELETE /v1/admin/all-data should work when database is already empty"() {
        given: "database is empty"
        eventRepository.count() == 0

        when: "DELETE /v1/admin/all-data is called"
        def response = authenticatedDeleteRequest("test-device", "dGVzdC1zZWNyZXQtMTIz", "/v1/admin/all-data")
                .when()
                .delete("/v1/admin/all-data")

        then: "should return 204 No Content"
        response.statusCode() == 204

        and: "database should remain empty"
        eventRepository.count() == 0
        dailySummaryRepository.count() == 0
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
