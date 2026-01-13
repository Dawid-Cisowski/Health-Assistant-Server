package com.healthassistant

import com.healthassistant.workout.api.WorkoutFacade
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

/**
 * Integration tests for Personal Records API
 */
@Title("Feature: Personal Records API")
class PersonalRecordsSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-pr"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String CHEST_1_ID = "chest_1"
    private static final String BENCH_PRESS_NAME = "Wyciskanie sztangi leżąc (płasko)"
    private static final String SQUAT_ID = "legs_1"
    private static final String SQUAT_NAME = "Przysiad ze sztangą"

    @Autowired
    WorkoutFacade workoutFacade

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        workoutFacade.deleteProjectionsByDeviceId(DEVICE_ID)
    }

    def "Scenario 1: Get personal records returns PRs for all exercises"() {
        given: "workouts with multiple exercises"
        submitWorkout("w1", "2024-12-01", [
            [name: BENCH_PRESS_NAME, exerciseId: CHEST_1_ID, sets: [[w: 80.0, r: 10], [w: 90.0, r: 6]]],
            [name: SQUAT_NAME, exerciseId: SQUAT_ID, sets: [[w: 100.0, r: 8]]]
        ])
        submitWorkout("w2", "2024-12-15", [
            [name: BENCH_PRESS_NAME, exerciseId: CHEST_1_ID, sets: [[w: 85.0, r: 10], [w: 95.0, r: 5]]]
        ])

        when: "I request all personal records"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/personal-records")
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains PRs for both exercises"
        def body = response.body().jsonPath()
        def records = body.getList("personalRecords")
        records.size() == 2

        and: "bench press PR is 95kg from latest workout"
        def benchPr = records.find { it.exerciseName == BENCH_PRESS_NAME }
        benchPr.maxWeightKg == 95.0
        benchPr.prDate == "2024-12-15"
        benchPr.exerciseId == CHEST_1_ID
        benchPr.muscleGroup == "CHEST"

        and: "squat PR is 100kg"
        def squatPr = records.find { it.exerciseName == SQUAT_NAME }
        squatPr.maxWeightKg == 100.0
        squatPr.prDate == "2024-12-01"
    }

    def "Scenario 2: Warmup sets are excluded from PR calculation"() {
        given: "a workout with warmup and working sets"
        submitWorkoutWithWarmups("w1", "2024-12-01", [
            [name: BENCH_PRESS_NAME, exerciseId: CHEST_1_ID, sets: [
                [w: 120.0, r: 15, warmup: true],
                [w: 80.0, r: 10, warmup: false],
                [w: 85.0, r: 8, warmup: false]
            ]]
        ])

        when: "I request personal records"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/personal-records")
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "PR is from working sets only, not the 120kg warmup"
        def body = response.body().jsonPath()
        def records = body.getList("personalRecords")
        records.size() == 1
        records[0].maxWeightKg == 85.0
    }

    def "Scenario 3: Empty response when no workout data exists"() {
        given: "no workout data for the device"
        // Device is clean from setup

        when: "I request personal records"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/personal-records")
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "response status is 200 with empty list"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("personalRecords").isEmpty()
    }

    def "Scenario 4: Request without HMAC authentication returns 401"() {
        when: "I request personal records without authentication"
        def response = io.restassured.RestAssured.given()
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "response status is 401"
        response.statusCode() == 401
    }

    def "Scenario 5: Device isolation - cannot see other device's PRs"() {
        given: "workouts from different device with high weights"
        submitWorkoutForDevice("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, "other-w1", "2024-12-01", [
            [name: BENCH_PRESS_NAME, exerciseId: CHEST_1_ID, sets: [[w: 200.0, r: 10]]]
        ])

        and: "workouts from test device with lower weights"
        submitWorkout("my-w1", "2024-12-01", [
            [name: BENCH_PRESS_NAME, exerciseId: CHEST_1_ID, sets: [[w: 80.0, r: 10]]]
        ])

        when: "I request personal records"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/personal-records")
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "only my PR is returned"
        def body = response.body().jsonPath()
        def records = body.getList("personalRecords")
        records.size() == 1
        records[0].maxWeightKg == 80.0
    }

    def "Scenario 6: Exercises without catalog ID still return PR"() {
        given: "workout with exercise that has no catalog ID"
        submitWorkoutWithoutExerciseId("w1", "2024-12-01", [
            [name: "Custom Exercise", sets: [[w: 50.0, r: 10]]]
        ])

        when: "I request personal records"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/personal-records")
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "PR is returned with null exerciseId and muscleGroup"
        def body = response.body().jsonPath()
        def records = body.getList("personalRecords")
        records.size() == 1
        records[0].exerciseName == "Custom Exercise"
        records[0].maxWeightKg == 50.0
        records[0].exerciseId == null
        records[0].muscleGroup == null
    }

    def "Scenario 7: PR date reflects the workout when max weight was achieved"() {
        given: "workouts where earlier workout has higher weight"
        submitWorkout("w1", "2024-01-01", [
            [name: BENCH_PRESS_NAME, exerciseId: CHEST_1_ID, sets: [[w: 100.0, r: 5]]]
        ])
        submitWorkout("w2", "2024-12-01", [
            [name: BENCH_PRESS_NAME, exerciseId: CHEST_1_ID, sets: [[w: 90.0, r: 8]]]
        ])

        when: "I request personal records"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/personal-records")
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "PR date is from the earlier workout with higher weight"
        def body = response.body().jsonPath()
        def records = body.getList("personalRecords")
        records[0].maxWeightKg == 100.0
        records[0].prDate == "2024-01-01"
    }

    def "Scenario 8: Zero weight sets are excluded"() {
        given: "workout with bodyweight exercise (0 kg)"
        submitWorkout("w1", "2024-12-01", [
            [name: "Pompki", exerciseId: "chest_2", sets: [[w: 0.0, r: 20], [w: 0.0, r: 15]]]
        ])

        when: "I request personal records"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/personal-records")
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "response status is 200 with empty list (no weighted PRs)"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("personalRecords").isEmpty()
    }

    def "Scenario 9: Multiple exercises are grouped by exerciseId"() {
        given: "workouts with same exercise using different display names"
        submitWorkout("w1", "2024-12-01", [
            [name: "Wyciskanie sztangi leżąc", exerciseId: CHEST_1_ID, sets: [[w: 80.0, r: 10]]]
        ])
        submitWorkout("w2", "2024-12-08", [
            [name: "Bench press", exerciseId: CHEST_1_ID, sets: [[w: 100.0, r: 5]]]
        ])

        when: "I request personal records"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/personal-records")
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "only one PR entry per exercise ID"
        def body = response.body().jsonPath()
        def records = body.getList("personalRecords")
        records.size() == 1
        records[0].maxWeightKg == 100.0
        records[0].exerciseId == CHEST_1_ID
    }

    def "Scenario 10: When multiple workouts have same PR weight, earliest date is returned"() {
        given: "two workouts with identical max weight on different dates"
        submitWorkout("w1", "2024-01-15", [
            [name: BENCH_PRESS_NAME, exerciseId: CHEST_1_ID, sets: [[w: 100.0, r: 5]]]
        ])
        submitWorkout("w2", "2024-12-15", [
            [name: BENCH_PRESS_NAME, exerciseId: CHEST_1_ID, sets: [[w: 100.0, r: 8]]]
        ])

        when: "I request personal records"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/personal-records")
                .get("/v1/workouts/personal-records")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "PR date reflects the first time weight was achieved"
        def body = response.body().jsonPath()
        def records = body.getList("personalRecords")
        records.size() == 1
        records[0].maxWeightKg == 100.0
        records[0].prDate == "2024-01-15"
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
            def exerciseIdJson = exercise.exerciseId ? """"exerciseId": "${exercise.exerciseId}",""" : ""
            """{${exerciseIdJson} "name": "${exercise.name}", "orderInWorkout": ${exercises.indexOf(exercise) + 1}, "sets": [${setsJson}]}"""
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

    void submitWorkoutWithoutExerciseId(String workoutId, String date, List<Map> exercises) {
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
            def exerciseIdJson = exercise.exerciseId ? """"exerciseId": "${exercise.exerciseId}",""" : ""
            """{${exerciseIdJson} "name": "${exercise.name}", "orderInWorkout": ${exercises.indexOf(exercise) + 1}, "sets": [${setsJson}]}"""
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
