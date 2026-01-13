package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Workout Projections
 */
@Title("Feature: Workout Projections and Query API")
class WorkoutProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-workout-proj"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        cleanupProjectionsForDateRange(DEVICE_ID, LocalDate.of(2025, 11, 1), LocalDate.of(2025, 12, 31))
    }

    def "Scenario 1: WorkoutRecorded event creates complete projection"() {
        given: "a valid workout event request"
        def workoutId = "gymrun-2025-11-19-proj-1"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "workout projection is created (verified via API)"
        def response = waitForApiResponse("/v1/workouts/${workoutId}", DEVICE_ID, SECRET_BASE64)
        response.getString("workoutId") == workoutId
        response.getString("source") == "GYMRUN_SCREENSHOT"
        response.getString("note") == "Plecy i biceps"
        response.getInt("totalExercises") == 2
        response.getInt("totalSets") == 5

        and: "exercise projections are created with exerciseId"
        def exercises = response.getList("exercises")
        exercises.size() == 2
        exercises[0].exerciseId == "back_2"
        exercises[1].exerciseId == "back_5"

        and: "set projections are created"
        def totalSets = exercises.collect { it.sets.size() }.sum()
        totalSets == 5

        and: "total volume is calculated correctly"
        response.getDouble("totalVolumeKg") > 0
    }

    def "Scenario 2: Idempotent projection - duplicate event doesn't create duplicate projection"() {
        given: "a valid workout event request"
        def workoutId = "gymrun-2025-11-19-proj-2"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        and: "I submit the workout event first time"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I submit the same workout event again"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "only one projection exists (verified via API)"
        def response = waitForApiResponse("/v1/workouts/${workoutId}", DEVICE_ID, SECRET_BASE64)
        response.getString("workoutId") == workoutId

        and: "only one set of exercises exists"
        def exercises = response.getList("exercises")
        exercises.size() == 2

        and: "only one set of sets exists"
        def totalSets = exercises.collect { it.sets.size() }.sum()
        totalSets == 5
    }

    def "Scenario 3: Multiple workouts on same day create separate projections"() {
        given: "multiple workout events on the same day"
        def workout1 = createWorkoutEvent("gymrun-2025-11-19-am", "2025-11-19T08:00:00Z")
        def workout2 = createWorkoutEvent("gymrun-2025-11-19-pm", "2025-11-19T18:00:00Z")
        def request = """
        {
            "events": [${workout1}, ${workout2}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit both workout events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "two workout projections are created (verified via API)"
        def workoutsResponse = waitForApiResponse("/v1/workouts?from=2025-11-19&to=2025-11-19", DEVICE_ID, SECRET_BASE64)
        workoutsResponse.getList("").size() == 2

        and: "daily summary contains both workouts"
        def summary = waitForApiResponse("/v1/daily-summaries/2025-11-19", DEVICE_ID, SECRET_BASE64)
        summary.getList("workouts").size() == 2
    }

    def "Scenario 4: Workout without muscle group creates projection with null"() {
        given: "a workout event without muscle group"
        def event = """
        {
            "idempotencyKey": "${DEVICE_ID}|workout|gymrun-nogrup",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-19T18:00:00Z",
            "payload": {
                "workoutId": "gymrun-nogrup",
                "performedAt": "2025-11-19T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "name": "Push-ups",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 0.0, "reps": 20, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "exercise projection has null muscle group (verified via API)"
        def response = waitForApiResponse("/v1/workouts/gymrun-nogrup", DEVICE_ID, SECRET_BASE64)
        def exercises = response.getList("exercises")
        exercises.size() == 1
        exercises[0].muscleGroup == null
    }

    def "Scenario 5: Warmup sets are distinguished from working sets in volume calculation"() {
        given: "a workout with warmup and working sets"
        def event = """
        {
            "idempotencyKey": "${DEVICE_ID}|workout|gymrun-warmup",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-19T18:00:00Z",
            "payload": {
                "workoutId": "gymrun-warmup",
                "performedAt": "2025-11-19T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "name": "Bench Press",
                        "muscleGroup": "Chest",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 40.0, "reps": 15, "isWarmup": true},
                            {"setNumber": 2, "weightKg": 80.0, "reps": 10, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "total volume includes warmup sets (verified via API)"
        def response = waitForApiResponse("/v1/workouts/gymrun-warmup", DEVICE_ID, SECRET_BASE64)
        response.getDouble("totalVolumeKg") == (40.0 * 15 + 80.0 * 10)

        and: "working volume excludes warmup sets"
        response.getDouble("totalWorkingVolumeKg") == (80.0 * 10)

        and: "sets have correct warmup flags"
        def sets = response.getList("exercises")[0].sets
        sets.size() == 2
        sets.find { it.setNumber == 1 }.isWarmup == true
        sets.find { it.setNumber == 2 }.isWarmup == false
    }

    def "Scenario 6: Bodyweight exercises (weight 0) have correct volume"() {
        given: "a workout with bodyweight exercise"
        def event = """
        {
            "idempotencyKey": "${DEVICE_ID}|workout|gymrun-bodyweight",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-19T18:00:00Z",
            "payload": {
                "workoutId": "gymrun-bodyweight",
                "performedAt": "2025-11-19T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "name": "Pull-ups",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 0.0, "reps": 15, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "total volume is zero (verified via API)"
        def response = waitForApiResponse("/v1/workouts/gymrun-bodyweight", DEVICE_ID, SECRET_BASE64)
        response.getDouble("totalVolumeKg") == 0.0

        and: "set has zero weight"
        def sets = response.getList("exercises")[0].sets
        sets[0].weightKg == 0.0
        sets[0].volumeKg == 0.0
    }

    def "Scenario 7: Exercise order is preserved in projections"() {
        given: "a workout with multiple exercises"
        def workoutId = "gymrun-order-test"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "exercises are ordered correctly (verified via API)"
        def response = waitForApiResponse("/v1/workouts/${workoutId}", DEVICE_ID, SECRET_BASE64)
        def exercises = response.getList("exercises")
        exercises.size() == 2
        exercises[0].orderInWorkout == 1
        exercises[0].exerciseId == "back_2"
        exercises[0].exerciseName == "Podciąganie się nachwytem (szeroki rozstaw rąk)"
        exercises[1].orderInWorkout == 2
        exercises[1].exerciseId == "back_5"
        exercises[1].exerciseName == "Wiosłowanie sztangielkami w opadzie"
    }

    def "Scenario 8: Daily summary contains only workoutId and note reference"() {
        given: "a valid workout event"
        def workoutId = "gymrun-summary-ref"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I fetch the daily summary"
        def summary = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/2025-11-17")
                .get("/v1/daily-summaries/2025-11-17")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "daily summary workout has only workoutId and note"
        def workouts = summary.getList("workouts")
        workouts.size() == 1
        def workout = workouts[0]
        workout.workoutId == workoutId
        workout.note == "Plecy i biceps"

        and: "daily summary workout does NOT have detailed fields"
        workout.totalExercises == null
        workout.totalSets == null
        workout.totalVolume == null
        workout.performedAt == null
        workout.source == null
    }

    def "Scenario 9: Query API returns workout details"() {
        given: "a valid workout event"
        def workoutId = "gymrun-api-test"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        and: "workout is submitted and projected"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query workout details via API"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/${workoutId}")
                .get("/v1/workouts/${workoutId}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response contains full workout details"
        response.getString("workoutId") == workoutId
        response.getString("note") == "Plecy i biceps"
        response.getInt("totalExercises") == 2
        response.getInt("totalSets") == 5

        and: "response contains exercises with sets"
        def exercises = response.getList("exercises")
        exercises.size() == 2
        exercises[0].sets.size() == 3
        exercises[1].sets.size() == 2
    }

    def "Scenario 10: Query API returns workouts in date range"() {
        given: "workouts on different dates"
        def workout1 = createWorkoutEvent("gymrun-2025-11-15", "2025-11-15T18:00:00Z")
        def workout2 = createWorkoutEvent("gymrun-2025-11-17", "2025-11-17T18:00:00Z")
        def workout3 = createWorkoutEvent("gymrun-2025-11-20", "2025-11-20T18:00:00Z")
        def request = """
        {
            "events": [${workout1}, ${workout2}, ${workout3}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "all workouts are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query workouts in date range"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts?from=2025-11-16&to=2025-11-19")
                .param("from", "2025-11-16")
                .param("to", "2025-11-19")
                .get("/v1/workouts")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "only workouts in range are returned"
        def workouts = response.getList("")
        workouts.size() == 1
        workouts[0].workoutId == "gymrun-2025-11-17"
    }

    def "Scenario 11: Timezone handling converts performedAt to correct performedDate"() {
        given: "a workout performed at 23:30 UTC (should be next day in Warsaw)"
        def event = """
        {
            "idempotencyKey": "${DEVICE_ID}|workout|gymrun-tz",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-19T23:30:00Z",
            "payload": {
                "workoutId": "gymrun-tz",
                "performedAt": "2025-11-19T23:30:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "name": "Bench Press",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 80.0, "reps": 10, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "performedDate is in Warsaw timezone (2025-11-20) - verified via API"
        def response = waitForApiResponse("/v1/workouts/gymrun-tz", DEVICE_ID, SECRET_BASE64)
        response.getString("performedDate") == "2025-11-20"
    }

    def "Scenario 12: Query API returns 404 for non-existent workout"() {
        when: "I query non-existent workout"
        authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/nonexistent-id")
                .get("/v1/workouts/nonexistent-id")
                .then()
                .statusCode(404)

        then: "no error is thrown"
        noExceptionThrown()
    }

    def "Scenario 13: Device isolation - different devices have separate workout projections"() {
        given: "workouts from two different devices"
        def event1 = """
        {
            "idempotencyKey": "${DEVICE_ID}|workout|device1-workout",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-20T18:00:00Z",
            "payload": {
                "workoutId": "device1-workout",
                "performedAt": "2025-11-20T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "name": "Bench Press",
                        "orderInWorkout": 1,
                        "sets": [{"setNumber": 1, "weightKg": 100.0, "reps": 10, "isWarmup": false}]
                    }
                ]
            }
        }
        """
        def request1 = createHealthEventsRequest(event1)

        def event2 = """
        {
            "idempotencyKey": "different-device-id|workout|device2-workout",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-20T18:00:00Z",
            "payload": {
                "workoutId": "device2-workout",
                "performedAt": "2025-11-20T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "name": "Squat",
                        "orderInWorkout": 1,
                        "sets": [{"setNumber": 1, "weightKg": 150.0, "reps": 8, "isWarmup": false}]
                    }
                ]
            }
        }
        """
        def request2 = """
        {
            "events": [${event2}],
            "deviceId": "different-device-id"
        }
        """.stripIndent().trim()

        when: "I submit workouts from both devices"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
        authenticatedPostRequestWithBody("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, "/v1/health-events", request2)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "device 1 can see its workout"
        def response1 = waitForApiResponse("/v1/workouts/device1-workout", DEVICE_ID, SECRET_BASE64)
        response1.getString("workoutId") == "device1-workout"
        response1.getList("exercises")[0].exerciseName == "Bench Press"

        and: "device 2 can see its workout"
        def response2 = waitForApiResponse("/v1/workouts/device2-workout", "different-device-id", DIFFERENT_DEVICE_SECRET_BASE64)
        response2.getString("workoutId") == "device2-workout"
        response2.getList("exercises")[0].exerciseName == "Squat"

        and: "device 1 cannot see device 2's workout (returns 404)"
        authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/device2-workout")
                .get("/v1/workouts/device2-workout")
                .then()
                .statusCode(404)

        and: "device 2 cannot see device 1's workout (returns 404)"
        authenticatedGetRequest("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, "/v1/workouts/device1-workout")
                .get("/v1/workouts/device1-workout")
                .then()
                .statusCode(404)
    }

    // Helper methods

    String createHealthEventsRequest(String event) {
        return """
        {
            "events": [${event}],
            "deviceId": "${DEVICE_ID}"
        }
        """.stripIndent().trim()
    }

    String createWorkoutEvent(String workoutId, String performedAt = "2025-11-17T18:00:00Z") {
        return """
        {
            "idempotencyKey": "gymrun-app|workout|${workoutId}",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "${performedAt}",
            "payload": {
                "workoutId": "${workoutId}",
                "performedAt": "${performedAt}",
                "source": "GYMRUN_SCREENSHOT",
                "note": "Plecy i biceps",
                "exercises": [
                    {
                        "exerciseId": "back_2",
                        "name": "Podciąganie się nachwytem (szeroki rozstaw rąk)",
                        "muscleGroup": "Plecy",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 73.0, "reps": 12, "isWarmup": false},
                            {"setNumber": 2, "weightKg": 73.5, "reps": 10, "isWarmup": false},
                            {"setNumber": 3, "weightKg": 74.5, "reps": 8, "isWarmup": false}
                        ]
                    },
                    {
                        "exerciseId": "back_5",
                        "name": "Wiosłowanie sztangielkami w opadzie",
                        "muscleGroup": "Plecy",
                        "orderInWorkout": 2,
                        "sets": [
                            {"setNumber": 1, "weightKg": 18.0, "reps": 12, "isWarmup": false},
                            {"setNumber": 2, "weightKg": 18.0, "reps": 12, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """.stripIndent().trim()
    }
}
