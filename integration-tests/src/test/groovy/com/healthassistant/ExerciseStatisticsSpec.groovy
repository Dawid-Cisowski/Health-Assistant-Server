package com.healthassistant

import com.healthassistant.workout.api.WorkoutFacade
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Exercise Statistics API
 */
@Title("Feature: Exercise Statistics API")
class ExerciseStatisticsSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-exercises"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String CHEST_1_ID = "chest_1"
    private static final String BENCH_PRESS_NAME = "Wyciskanie sztangi leżąc (płasko)"

    @Autowired
    WorkoutFacade workoutFacade

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        workoutFacade.deleteProjectionsByDeviceId(DEVICE_ID)
    }

    def "Scenario 1: Get statistics for exercise with multiple workouts returns complete data"() {
        given: "multiple workouts containing bench press"
        submitWorkout("workout-1", "2024-12-01", [
            [name: BENCH_PRESS_NAME, sets: [[w: 80.0, r: 10], [w: 85.0, r: 8], [w: 90.0, r: 6]]]
        ])
        submitWorkout("workout-2", "2024-12-08", [
            [name: BENCH_PRESS_NAME, sets: [[w: 82.5, r: 10], [w: 87.5, r: 8], [w: 92.5, r: 6]]]
        ])
        submitWorkout("workout-3", "2024-12-15", [
            [name: BENCH_PRESS_NAME, sets: [[w: 85.0, r: 10], [w: 90.0, r: 8], [w: 95.0, r: 6]]]
        ])

        when: "I request exercise statistics"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics")
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains exercise metadata from catalog"
        def body = response.body().jsonPath()
        body.getString("exerciseId") == CHEST_1_ID
        body.getString("exerciseName") == BENCH_PRESS_NAME
        body.getString("muscleGroup") == "CHEST"
        body.getString("description") != null

        and: "summary contains correct PR"
        body.getDouble("summary.personalRecordKg") == 95.0

        and: "summary contains correct total sets"
        body.getInt("summary.totalSets") == 9

        and: "history contains 3 entries"
        def history = body.getList("history")
        history.size() == 3

        and: "history is ordered by date descending"
        history[0].date == "2024-12-15"
        history[1].date == "2024-12-08"
        history[2].date == "2024-12-01"
    }

    def "Scenario 2: Get statistics for unknown exercise returns 404"() {
        when: "I request statistics for non-existent exercise"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/unknown_exercise/statistics")
                .get("/v1/exercises/unknown_exercise/statistics")
                .then()
                .extract()

        then: "response status is 404"
        response.statusCode() == 404
    }

    def "Scenario 3: Get statistics for exercise with no user data returns 204"() {
        given: "no workout data for the user"
        // Device is clean from setup

        when: "I request exercise statistics"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics")
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 204 No Content"
        response.statusCode() == 204
    }

    def "Scenario 4: Request without HMAC authentication returns 401"() {
        when: "I request statistics without authentication"
        def response = io.restassured.RestAssured.given()
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 401"
        response.statusCode() == 401
    }

    def "Scenario 5: Statistics correctly calculates estimated 1RM using Brzycki formula"() {
        given: "a workout with bench press - 80kg x 10 reps"
        submitWorkout("workout-1rm", "2024-12-01", [
            [name: BENCH_PRESS_NAME, sets: [[w: 80.0, r: 10]]]
        ])

        when: "I request exercise statistics"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics")
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "estimated 1RM is calculated correctly (Brzycki: 80 * 36 / (37-10) = 106.67)"
        def body = response.body().jsonPath()
        def estimated1RM = body.getDouble("history[0].estimated1RM")
        estimated1RM >= 106.0 && estimated1RM <= 107.0
    }

    def "Scenario 6: Statistics filters by date range when provided"() {
        given: "workouts spanning multiple months"
        submitWorkout("workout-jan", "2024-01-15", [
            [name: BENCH_PRESS_NAME, sets: [[w: 70.0, r: 10]]]
        ])
        submitWorkout("workout-jun", "2024-06-15", [
            [name: BENCH_PRESS_NAME, sets: [[w: 80.0, r: 10]]]
        ])
        submitWorkout("workout-dec", "2024-12-15", [
            [name: BENCH_PRESS_NAME, sets: [[w: 90.0, r: 10]]]
        ])

        when: "I request statistics filtered to June-December"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics?fromDate=2024-06-01&toDate=2024-12-31")
                .get("/v1/exercises/${CHEST_1_ID}/statistics?fromDate=2024-06-01&toDate=2024-12-31")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only 2 workouts are in history"
        def body = response.body().jsonPath()
        def history = body.getList("history")
        history.size() == 2

        and: "PR is from filtered range"
        body.getDouble("summary.personalRecordKg") == 90.0
    }

    def "Scenario 7: Personal record flag is set correctly on sets"() {
        given: "workouts with varying weights"
        submitWorkout("workout-pr", "2024-12-01", [
            [name: BENCH_PRESS_NAME, sets: [[w: 80.0, r: 10], [w: 100.0, r: 5], [w: 90.0, r: 8]]]
        ])

        when: "I request exercise statistics"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics")
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only the PR set is marked as isPr"
        def body = response.body().jsonPath()
        def sets = body.getList("history[0].sets")
        sets.find { it.weightKg == 100.0 }.isPr == true
        sets.find { it.weightKg == 80.0 }.isPr == false
        sets.find { it.weightKg == 90.0 }.isPr == false
    }

    def "Scenario 8: Warmup sets are excluded from PR calculation"() {
        given: "a workout with warmup and working sets"
        submitWorkoutWithWarmups("workout-warmup", "2024-12-01", [
            [name: BENCH_PRESS_NAME, sets: [
                [w: 40.0, r: 15, warmup: true],
                [w: 60.0, r: 12, warmup: true],
                [w: 80.0, r: 10, warmup: false],
                [w: 85.0, r: 8, warmup: false]
            ]]
        ])

        when: "I request exercise statistics"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics")
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "PR is from working sets only (not warmup 60kg)"
        def body = response.body().jsonPath()
        body.getDouble("summary.personalRecordKg") == 85.0
    }

    def "Scenario 9: Statistics works with exercise name variations via mapping"() {
        given: "workouts using different name variations mapped to same catalog exercise"
        submitWorkout("workout-v1", "2024-12-01", [
            [name: "Wyciskanie sztangi leżąc", sets: [[w: 80.0, r: 10]]]
        ])
        submitWorkout("workout-v2", "2024-12-08", [
            [name: "Bench press", sets: [[w: 85.0, r: 10]]]
        ])
        submitWorkout("workout-v3", "2024-12-15", [
            [name: BENCH_PRESS_NAME, sets: [[w: 90.0, r: 10]]]
        ])

        when: "I request exercise statistics by catalog ID"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics")
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "all name variations are included in history"
        def body = response.body().jsonPath()
        def history = body.getList("history")
        history.size() == 3

        and: "summary aggregates all variations"
        body.getInt("summary.totalSets") == 3
        body.getDouble("summary.personalRecordKg") == 90.0
    }

    def "Scenario 10: Device isolation - user cannot see other user's data"() {
        given: "workouts from different device"
        submitWorkoutForDevice("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, "other-workout", "2024-12-01", [
            [name: BENCH_PRESS_NAME, sets: [[w: 200.0, r: 10]]]
        ])

        and: "workouts from test device"
        submitWorkout("my-workout", "2024-12-01", [
            [name: BENCH_PRESS_NAME, sets: [[w: 80.0, r: 10]]]
        ])

        when: "I request exercise statistics"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics")
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only my data is returned"
        def body = response.body().jsonPath()
        body.getDouble("summary.personalRecordKg") == 80.0
        body.getList("history").size() == 1
    }

    def "Scenario 11: Progression percentage is calculated correctly for improving trend"() {
        given: "workouts showing clear improvement"
        submitWorkout("w1", "2024-01-01", [[name: BENCH_PRESS_NAME, sets: [[w: 60.0, r: 10]]]])
        submitWorkout("w2", "2024-03-01", [[name: BENCH_PRESS_NAME, sets: [[w: 70.0, r: 10]]]])
        submitWorkout("w3", "2024-06-01", [[name: BENCH_PRESS_NAME, sets: [[w: 80.0, r: 10]]]])
        submitWorkout("w4", "2024-09-01", [[name: BENCH_PRESS_NAME, sets: [[w: 90.0, r: 10]]]])
        submitWorkout("w5", "2024-12-01", [[name: BENCH_PRESS_NAME, sets: [[w: 100.0, r: 10]]]])

        when: "I request exercise statistics"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics")
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "progression percentage is positive"
        def body = response.body().jsonPath()
        def progression = body.getDouble("summary.progressionPercentage")
        progression > 0
    }

    def "Scenario 12: Single workout returns zero progression"() {
        given: "only one workout"
        submitWorkout("single", "2024-12-01", [
            [name: BENCH_PRESS_NAME, sets: [[w: 80.0, r: 10]]]
        ])

        when: "I request exercise statistics"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/exercises/${CHEST_1_ID}/statistics")
                .get("/v1/exercises/${CHEST_1_ID}/statistics")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "progression is 0"
        def body = response.body().jsonPath()
        body.getDouble("summary.progressionPercentage") == 0.0
    }

    // Helper methods

    void submitWorkout(String workoutId, String date, List<Map> exercises) {
        submitWorkoutForDevice(DEVICE_ID, SECRET_BASE64, workoutId, date, exercises)
    }

    void submitWorkoutWithWarmups(String workoutId, String date, List<Map> exercises) {
        def exercisesJson = exercises.collect { exercise ->
            def setsJson = exercise.sets.collect { set ->
                """{"setNumber": ${exercise.sets.indexOf(set) + 1}, "weightKg": ${set.w}, "reps": ${set.r}, "isWarmup": ${set.warmup ?: false}}"""
            }.join(',')
            """{"name": "${exercise.name}", "orderInWorkout": ${exercises.indexOf(exercise) + 1}, "sets": [${setsJson}]}"""
        }.join(',')

        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "${date}T12:00:00Z",
            "payload": {
                "workoutId": "${workoutId}",
                "performedAt": "${date}T12:00:00Z",
                "source": "TEST",
                "exercises": [${exercisesJson}]
            }
        }
        """

        def request = """{"events": [${event}], "deviceId": "${DEVICE_ID}"}"""

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
    }

    void submitWorkoutForDevice(String deviceId, String secret, String workoutId, String date, List<Map> exercises) {
        def exercisesJson = exercises.collect { exercise ->
            def setsJson = exercise.sets.collect { set ->
                """{"setNumber": ${exercise.sets.indexOf(set) + 1}, "weightKg": ${set.w}, "reps": ${set.r}, "isWarmup": false}"""
            }.join(',')
            """{"name": "${exercise.name}", "orderInWorkout": ${exercises.indexOf(exercise) + 1}, "sets": [${setsJson}]}"""
        }.join(',')

        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "${date}T12:00:00Z",
            "payload": {
                "workoutId": "${workoutId}",
                "performedAt": "${date}T12:00:00Z",
                "source": "TEST",
                "exercises": [${exercisesJson}]
            }
        }
        """

        def request = """{"events": [${event}], "deviceId": "${deviceId}"}"""

        authenticatedPostRequestWithBody(deviceId, secret, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
    }
}
