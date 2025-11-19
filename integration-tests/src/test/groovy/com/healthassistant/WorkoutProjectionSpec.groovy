package com.healthassistant

import com.healthassistant.application.summary.DailySummaryJpaRepository
import com.healthassistant.application.workout.projection.WorkoutExerciseProjectionJpaRepository
import com.healthassistant.application.workout.projection.WorkoutProjectionJpaRepository
import com.healthassistant.application.workout.projection.WorkoutSetProjectionJpaRepository
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Workout Projections
 */
@Title("Feature: Workout Projections and Query API")
class WorkoutProjectionSpec extends BaseIntegrationSpec {

    @Autowired
    WorkoutProjectionJpaRepository workoutProjectionRepository

    @Autowired
    WorkoutExerciseProjectionJpaRepository exerciseProjectionRepository

    @Autowired
    WorkoutSetProjectionJpaRepository setProjectionRepository

    @Autowired
    DailySummaryJpaRepository dailySummaryRepository

    def setup() {
        // Clean projection tables (in addition to base cleanup)
        setProjectionRepository?.deleteAll()
        exerciseProjectionRepository?.deleteAll()
        workoutProjectionRepository?.deleteAll()
        dailySummaryRepository?.deleteAll()
    }

    def "Scenario 1: WorkoutRecorded event creates complete projection"() {
        given: "a valid workout event request"
        def workoutId = "gymrun-2025-11-19-proj-1"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        when: "I submit the workout event"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "workout projection is created"
        def workouts = workoutProjectionRepository.findAll()
        workouts.size() == 1
        def workout = workouts.first()
        workout.workoutId == workoutId
        workout.source == "GYMRUN_SCREENSHOT"
        workout.note == "Plecy i biceps"
        workout.totalExercises == 2
        workout.totalSets == 5

        and: "exercise projections are created"
        def exercises = exerciseProjectionRepository.findAll()
        exercises.size() == 2
        exercises.every { it.workout.workoutId == workoutId }

        and: "set projections are created"
        def sets = setProjectionRepository.findAll()
        sets.size() == 5
        sets.every { it.workoutId == workoutId }

        and: "total volume is calculated correctly"
        workout.totalVolumeKg > 0
    }

    def "Scenario 2: Idempotent projection - duplicate event doesn't create duplicate projection"() {
        given: "a valid workout event request"
        def workoutId = "gymrun-2025-11-19-proj-2"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        and: "I submit the workout event first time"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I submit the same workout event again"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "only one projection exists"
        def workouts = workoutProjectionRepository.findAll()
        workouts.size() == 1

        and: "only one set of exercises exists"
        def exercises = exerciseProjectionRepository.findAll()
        exercises.size() == 2

        and: "only one set of sets exists"
        def sets = setProjectionRepository.findAll()
        sets.size() == 5
    }

    def "Scenario 3: Multiple workouts on same day create separate projections"() {
        given: "multiple workout events on the same day"
        def workout1 = createWorkoutEvent("gymrun-2025-11-19-am", "2025-11-19T08:00:00Z")
        def workout2 = createWorkoutEvent("gymrun-2025-11-19-pm", "2025-11-19T18:00:00Z")
        def request = """
        {
            "events": [${workout1}, ${workout2}],
            "deviceId": "gymrun-app"
        }
        """

        when: "I submit both workout events"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "two workout projections are created"
        def workouts = workoutProjectionRepository.findAll()
        workouts.size() == 2
        workouts.every { it.performedDate == LocalDate.parse("2025-11-19") }

        and: "daily summary contains both workouts"
        RestAssured.given()
                .get("/v1/daily-summaries/2025-11-19")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
                .getList("workouts").size() == 2
    }

    def "Scenario 4: Workout without muscle group creates projection with null"() {
        given: "a workout event without muscle group"
        def event = """
        {
            "idempotencyKey": "gymrun-app|workout|gymrun-nogrup",
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
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "exercise projection has null muscle group"
        def exercises = exerciseProjectionRepository.findAll()
        exercises.size() == 1
        exercises.first().muscleGroup == null
    }

    def "Scenario 5: Warmup sets are distinguished from working sets in volume calculation"() {
        given: "a workout with warmup and working sets"
        def event = """
        {
            "idempotencyKey": "gymrun-app|workout|gymrun-warmup",
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
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "total volume includes warmup sets"
        def workout = workoutProjectionRepository.findAll().first()
        workout.totalVolumeKg == (40.0 * 15 + 80.0 * 10)

        and: "working volume excludes warmup sets"
        workout.totalWorkingVolumeKg == (80.0 * 10)

        and: "sets have correct warmup flags"
        def sets = setProjectionRepository.findAll()
        sets.size() == 2
        sets.find { it.setNumber == 1 }.isWarmup == true
        sets.find { it.setNumber == 2 }.isWarmup == false
    }

    def "Scenario 6: Bodyweight exercises (weight 0) have correct volume"() {
        given: "a workout with bodyweight exercise"
        def event = """
        {
            "idempotencyKey": "gymrun-app|workout|gymrun-bodyweight",
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
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "total volume is zero"
        def workout = workoutProjectionRepository.findAll().first()
        workout.totalVolumeKg == 0.0

        and: "set has zero weight"
        def sets = setProjectionRepository.findAll()
        sets.first().weightKg == 0.0
        sets.first().volumeKg == 0.0
    }

    def "Scenario 7: Exercise order is preserved in projections"() {
        given: "a workout with multiple exercises"
        def workoutId = "gymrun-order-test"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        when: "I submit the workout event"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "exercises are ordered correctly"
        def exercises = exerciseProjectionRepository.findAll().sort { it.orderInWorkout }
        exercises.size() == 2
        exercises[0].orderInWorkout == 1
        exercises[0].exerciseName == "Podciąganie się nachwytem (szeroki rozstaw rąk)"
        exercises[1].orderInWorkout == 2
        exercises[1].exerciseName == "Wiosłowanie sztangielkami w opadzie"
    }

    def "Scenario 8: Daily summary contains only workoutId and note reference"() {
        given: "a valid workout event"
        def workoutId = "gymrun-summary-ref"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        when: "I submit the workout event"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I fetch the daily summary"
        def summary = RestAssured.given()
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
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query workout details via API"
        def response = RestAssured.given()
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
            "deviceId": "gymrun-app"
        }
        """

        and: "all workouts are submitted"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query workouts in date range"
        def response = RestAssured.given()
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
            "idempotencyKey": "gymrun-app|workout|gymrun-tz",
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
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "performedDate is in Warsaw timezone (2025-11-20)"
        def workout = workoutProjectionRepository.findAll().first()
        workout.performedDate == LocalDate.parse("2025-11-20")
    }

    def "Scenario 12: Query API returns 404 for non-existent workout"() {
        when: "I query non-existent workout"
        RestAssured.given()
                .get("/v1/workouts/nonexistent-id")
                .then()
                .statusCode(404)

        then: "no error is thrown"
        noExceptionThrown()
    }

    // Helper methods

    String createHealthEventsRequest(String event) {
        return """
        {
            "events": [${event}],
            "deviceId": "gymrun-app"
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
