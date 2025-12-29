package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Workout event ingestion via generic health events endpoint
 */
@Title("Feature: Workout Event Ingestion via Health Events API")
class WorkoutSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-workout"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
    }

    def "Scenario 1: Submit valid workout event returns success"() {
        given: "a valid workout event request"
        def request = createHealthEventsRequest(createWorkoutEvent("gymrun-2025-11-17-1"))

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("totalEvents") == 1

        and: "event result indicates stored"
        def events = body.getList("events")
        events.size() == 1
        events[0].status == "stored"
        events[0].eventId != null
    }

    def "Scenario 2: Workout event is stored in database"() {
        given: "a valid workout event request"
        def request = createHealthEventsRequest(createWorkoutEvent("gymrun-2025-11-17-2"))

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1

        and: "event has correct type"
        def workoutEvent = events.first()
        workoutEvent.eventType() == "WorkoutRecorded.v1"

        and: "event has correct device ID"
        workoutEvent.deviceId() == DEVICE_ID

        and: "event has correct payload"
        def payload = workoutEvent.payload()
        payload.workoutId() == "gymrun-2025-11-17-2"
        payload.source() == "GYMRUN_SCREENSHOT"
        payload.note() == "Plecy i biceps"
        payload.exercises() != null
        payload.exercises().size() == 2
    }

    def "Scenario 3: Duplicate workout event returns duplicate status"() {
        given: "a valid workout event request"
        def request = createHealthEventsRequest(createWorkoutEvent("gymrun-2025-11-17-3"))

        and: "I submit the workout event first time"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I submit the same workout event again"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains duplicate status for the event"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        def events = body.getList("events")
        events[0].status == "duplicate"

        and: "only one event is stored in database"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
    }

    def "Scenario 4: Workout event with missing workoutId returns validation error"() {
        given: "a workout event without workoutId"
        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-17T18:00:00Z",
            "payload": {
                "performedAt": "2025-11-17T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "note": "Test workout",
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
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status for the event"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
    }

    def "Scenario 5: Workout event with empty exercises list returns validation error"() {
        given: "a workout event with empty exercises list"
        def event = createWorkoutEventMap("gymrun-2025-11-17-5", "2025-11-17T18:00:00Z")
        event.payload.exercises = []
        def request = createHealthEventsRequestFromMap([event])

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
        events[0].error != null
    }

    def "Scenario 6: Workout event with missing exercise name returns validation error"() {
        given: "a workout event with missing exercise name"
        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-17T18:00:00Z",
            "payload": {
                "workoutId": "gymrun-2025-11-17-6",
                "performedAt": "2025-11-17T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "muscleGroup": "Chest",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 80.0, "reps": 10, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
    }

    def "Scenario 7: Workout event with negative weight returns validation error"() {
        given: "a workout event with negative weight"
        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-17T18:00:00Z",
            "payload": {
                "workoutId": "gymrun-2025-11-17-7",
                "performedAt": "2025-11-17T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "name": "Bench Press",
                        "muscleGroup": "Chest",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": -80.0, "reps": 10, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
        events[0].error.message.toString().contains("non-negative")
    }

    def "Scenario 8: Workout event with zero reps returns validation error"() {
        given: "a workout event with zero reps"
        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-17T18:00:00Z",
            "payload": {
                "workoutId": "gymrun-2025-11-17-8",
                "performedAt": "2025-11-17T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "exercises": [
                    {
                        "name": "Bench Press",
                        "muscleGroup": "Chest",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 80.0, "reps": 0, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
        events[0].error.message.toString().contains("positive")
    }

    def "Scenario 9: Workout event has correct idempotency key based on workoutId"() {
        given: "a valid workout event request"
        def workoutId = "gymrun-2025-11-17-9"
        def request = createHealthEventsRequest(createWorkoutEvent(workoutId))

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event has correct idempotency key"
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1
        def workoutEvent = events.first()
        workoutEvent.idempotencyKey() == "${DEVICE_ID}|workout|${workoutId}"
    }

    def "Scenario 10: Multiple workout events are stored correctly"() {
        given: "multiple valid workout events"
        def event1 = createWorkoutEvent("gymrun-2025-11-17-10-1")
        def event2 = createWorkoutEvent("gymrun-2025-11-17-10-2")
        def event3 = createWorkoutEvent("gymrun-2025-11-17-10-3")

        def request = """
        {
            "events": [${event1}, ${event2}, ${event3}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit all workout events"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates success"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("totalEvents") == 3

        and: "all events are stored in database"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 3
        dbEvents.every { it.eventType() == "WorkoutRecorded.v1" }
        dbEvents.every { it.deviceId() == DEVICE_ID }
    }

    def "Scenario 11: Workout event with minimal data (no note, no muscleGroup)"() {
        given: "a workout event with minimal data"
        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-17T18:00:00Z",
            "payload": {
                "workoutId": "gymrun-2025-11-17-11",
                "performedAt": "2025-11-17T18:00:00Z",
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
        def request = """
        {
            "events": [${event}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored in database"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
        def workoutEvent = dbEvents.first()
        workoutEvent.eventType() == "WorkoutRecorded.v1"
        workoutEvent.payload().note() == null
    }

    def "Scenario 12: Workout event with complex data (multiple exercises, multiple sets)"() {
        given: "a workout event with complex data"
        def request = createHealthEventsRequest(createComplexWorkoutEvent("gymrun-2025-11-17-12"))

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored with all exercises and sets"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
        def workoutEvent = dbEvents.first()
        def exercises = workoutEvent.payload().exercises()
        exercises.size() == 5

        and: "first exercise has 3 sets"
        def firstExercise = exercises.get(0)
        def firstExerciseSets = firstExercise.sets()
        firstExerciseSets.size() == 3

        and: "exercise details are preserved"
        firstExercise.name() == "Podciąganie się nachwytem (szeroki rozstaw rąk)"
        firstExercise.orderInWorkout() == 1
    }

    def "Scenario 13: Workout event occurredAt matches performedAt timestamp"() {
        given: "a workout event with specific timestamp"
        def performedAt = "2025-11-17T18:30:45Z"
        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "${performedAt}",
            "payload": {
                "workoutId": "gymrun-2025-11-17-13",
                "performedAt": "${performedAt}",
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
        def request = """
        {
            "events": [${event}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the workout event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches performedAt"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
        def workoutEvent = dbEvents.first()
        def occurredAt = Instant.parse(workoutEvent.occurredAt().toString())
        occurredAt == Instant.parse(performedAt)
    }

    def "Scenario 14: Workout event with warmup and working sets"() {
        given: "a workout event with warmup and working sets"
        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-17T18:00:00Z",
            "payload": {
                "workoutId": "gymrun-2025-11-17-14",
                "performedAt": "2025-11-17T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "note": "Test warmup sets",
                "exercises": [
                    {
                        "name": "Bench Press",
                        "muscleGroup": "Chest",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 40.0, "reps": 15, "isWarmup": true},
                            {"setNumber": 2, "weightKg": 60.0, "reps": 12, "isWarmup": true},
                            {"setNumber": 3, "weightKg": 80.0, "reps": 10, "isWarmup": false},
                            {"setNumber": 4, "weightKg": 85.0, "reps": 8, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored with warmup flags preserved"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
        def workoutEvent = dbEvents.first()
        def exercises = workoutEvent.payload().exercises()
        def exercise = exercises.get(0)
        def sets = exercise.sets()
        sets.size() == 4
        sets.get(0).isWarmup() == true
        sets.get(1).isWarmup() == true
        sets.get(2).isWarmup() == false
        sets.get(3).isWarmup() == false
    }

    def "Scenario 15: Workout event with bodyweight exercise (weight 0)"() {
        given: "a workout event with bodyweight exercise"
        def event = """
        {
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-17T18:00:00Z",
            "payload": {
                "workoutId": "gymrun-2025-11-17-15",
                "performedAt": "2025-11-17T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "note": "Bodyweight exercises",
                "exercises": [
                    {
                        "name": "Push-ups",
                        "muscleGroup": "Chest",
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 0.0, "reps": 20, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the workout event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored with zero weight"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
        def workoutEvent = dbEvents.first()
        def exercises = workoutEvent.payload().exercises()
        def exercise = exercises.get(0)
        def sets = exercise.sets()
        sets.get(0).weightKg() == 0.0
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

    String createHealthEventsRequestFromMap(List<Map> events) {
        def eventsJson = events.collect { event ->
            def payloadJson = groovy.json.JsonOutput.toJson(event.payload)
            """
            {
                "idempotencyKey": "${event.idempotencyKey}",
                "type": "${event.type}",
                "occurredAt": "${event.occurredAt}",
                "payload": ${payloadJson}
            }
            """
        }.join(',')

        return """
        {
            "events": [${eventsJson}],
            "deviceId": "${DEVICE_ID}"
        }
        """.stripIndent().trim()
    }

    Map createWorkoutEventMap(String workoutId, String performedAt = "2025-11-17T18:00:00Z") {
        return [
            idempotencyKey: "${DEVICE_ID}|workout|${workoutId}",
            type: "WorkoutRecorded.v1",
            occurredAt: performedAt,
            payload: [
                workoutId: workoutId,
                performedAt: performedAt,
                source: "GYMRUN_SCREENSHOT",
                note: "Plecy i biceps",
                exercises: [
                    [
                        name: "Podciąganie się nachwytem (szeroki rozstaw rąk)",
                        muscleGroup: "Plecy",
                        orderInWorkout: 1,
                        sets: [
                            [setNumber: 1, weightKg: 73.0, reps: 12, isWarmup: false],
                            [setNumber: 2, weightKg: 73.5, reps: 10, isWarmup: false]
                        ]
                    ]
                ]
            ]
        ]
    }

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

    String createComplexWorkoutEvent(String workoutId) {
        return """
        {
            "idempotencyKey": "${DEVICE_ID}|workout|${workoutId}",
            "type": "WorkoutRecorded.v1",
            "occurredAt": "2025-11-17T18:00:00Z",
            "payload": {
                "workoutId": "${workoutId}",
                "performedAt": "2025-11-17T18:00:00Z",
                "source": "GYMRUN_SCREENSHOT",
                "note": "Plecy i biceps",
                "exercises": [
                    {
                        "name": "Podciąganie się nachwytem (szeroki rozstaw rąk)",
                        "muscleGroup": null,
                        "orderInWorkout": 1,
                        "sets": [
                            {"setNumber": 1, "weightKg": 73.0, "reps": 12, "isWarmup": false},
                            {"setNumber": 2, "weightKg": 73.5, "reps": 10, "isWarmup": false},
                            {"setNumber": 3, "weightKg": 74.5, "reps": 8, "isWarmup": false}
                        ]
                    },
                    {
                        "name": "Wiosłowanie sztangielkami w opadzie",
                        "muscleGroup": null,
                        "orderInWorkout": 2,
                        "sets": [
                            {"setNumber": 1, "weightKg": 18.0, "reps": 12, "isWarmup": false},
                            {"setNumber": 2, "weightKg": 18.0, "reps": 12, "isWarmup": false},
                            {"setNumber": 3, "weightKg": 18.0, "reps": 12, "isWarmup": false}
                        ]
                    },
                    {
                        "name": "Podciąganie podchwytem (średni rozstaw rąk)",
                        "muscleGroup": null,
                        "orderInWorkout": 3,
                        "sets": [
                            {"setNumber": 1, "weightKg": 74.5, "reps": 12, "isWarmup": false},
                            {"setNumber": 2, "weightKg": 74.5, "reps": 10, "isWarmup": false},
                            {"setNumber": 3, "weightKg": 74.5, "reps": 8, "isWarmup": false}
                        ]
                    },
                    {
                        "name": "Naprzemienne uginanie przedramion ze sztangielkami",
                        "muscleGroup": null,
                        "orderInWorkout": 4,
                        "sets": [
                            {"setNumber": 1, "weightKg": 9.0, "reps": 12, "isWarmup": false},
                            {"setNumber": 2, "weightKg": 9.0, "reps": 12, "isWarmup": false},
                            {"setNumber": 3, "weightKg": 9.0, "reps": 12, "isWarmup": false}
                        ]
                    },
                    {
                        "name": "Uginanie przedramion ze sztangielkami w chwycie młotkowym",
                        "muscleGroup": null,
                        "orderInWorkout": 5,
                        "sets": [
                            {"setNumber": 1, "weightKg": 9.0, "reps": 12, "isWarmup": false},
                            {"setNumber": 2, "weightKg": 9.0, "reps": 12, "isWarmup": false},
                            {"setNumber": 3, "weightKg": 9.0, "reps": 12, "isWarmup": false}
                        ]
                    }
                ]
            }
        }
        """.stripIndent().trim()
    }
}
