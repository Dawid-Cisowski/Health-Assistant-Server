package com.healthassistant

import spock.lang.Title

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Integration tests for Workout CRUD operations (Update, Delete)
 * Note: Create is done via POST /v1/health-events with WorkoutRecorded.v1 event
 */
@Title("Feature: Workout CRUD Operations (Update, Delete)")
class WorkoutCrudSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-workout-crud"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
    }

    // ===================== Helper Methods =====================

    private Map createWorkout(String workoutId, Instant performedAt = Instant.now().minus(1, ChronoUnit.HOURS)) {
        def workoutEvent = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|workout|${workoutId}",
                    "type": "WorkoutRecorded.v1",
                    "occurredAt": "${performedAt}",
                    "payload": {
                        "workoutId": "${workoutId}",
                        "performedAt": "${performedAt}",
                        "source": "TEST",
                        "note": "Test workout",
                        "exercises": [
                            {
                                "name": "Bench Press",
                                "exerciseId": "chest_1",
                                "muscleGroup": "Chest",
                                "orderInWorkout": 1,
                                "sets": [
                                    { "setNumber": 1, "weightKg": 60.0, "reps": 10, "isWarmup": true },
                                    { "setNumber": 2, "weightKg": 80.0, "reps": 8, "isWarmup": false },
                                    { "setNumber": 3, "weightKg": 80.0, "reps": 8, "isWarmup": false }
                                ]
                            },
                            {
                                "name": "Incline Dumbbell Press",
                                "exerciseId": "chest_2",
                                "muscleGroup": "Chest",
                                "orderInWorkout": 2,
                                "sets": [
                                    { "setNumber": 1, "weightKg": 25.0, "reps": 12, "isWarmup": false },
                                    { "setNumber": 2, "weightKg": 25.0, "reps": 10, "isWarmup": false }
                                ]
                            }
                        ]
                    }
                }
            ]
        }
        """

        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", workoutEvent)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
                .extract()

        def body = response.body().jsonPath()
        def eventId = body.getList("events.eventId").first()
        return [eventId: eventId, workoutId: workoutId, performedAt: performedAt]
    }

    // ===================== DELETE /v1/workouts/event/{eventId} =====================

    def "Scenario 1: Delete existing workout returns 204"() {
        given: "an existing workout"
        def workout = createWorkout("workout-delete-test-${System.currentTimeMillis()}")

        when: "I delete the workout"
        def deleteResponse = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}")
                .delete("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response status is 204 No Content"
        deleteResponse.statusCode() == 204
    }

    def "Scenario 2: Delete non-existing workout returns 404"() {
        when: "I try to delete a non-existing workout"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/non-existing-id")
                .delete("/v1/workouts/event/non-existing-id")
                .then()
                .extract()

        then: "response status is 404 Not Found"
        response.statusCode() == 404
    }

    def "Scenario 3: Delete workout from different device returns 404"() {
        given: "an existing workout created by one device"
        def workout = createWorkout("workout-device-test-${System.currentTimeMillis()}")

        when: "I try to delete the workout from a different device"
        def response = authenticatedDeleteRequest("different-device-id", "ZGlmZmVyZW50LXNlY3JldC0xMjM=", "/v1/workouts/event/${workout.eventId}")
                .delete("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response status is 404 Not Found (workout not visible to other device)"
        response.statusCode() == 404
    }

    // ===================== PUT /v1/workouts/event/{eventId} =====================

    def "Scenario 4: Update existing workout returns 200 with updated data"() {
        given: "an existing workout"
        def workout = createWorkout("workout-update-test-${System.currentTimeMillis()}")

        and: "an update request with new values"
        def newPerformedAt = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        def updateRequest = """
        {
            "performedAt": "${newPerformedAt}",
            "source": "UPDATED_TEST",
            "note": "Updated workout note",
            "exercises": [
                {
                    "name": "Squat",
                    "exerciseId": "legs_1",
                    "muscleGroup": "Legs",
                    "orderInWorkout": 1,
                    "sets": [
                        { "setNumber": 1, "weightKg": 100.0, "reps": 5, "isWarmup": false },
                        { "setNumber": 2, "weightKg": 100.0, "reps": 5, "isWarmup": false }
                    ]
                }
            ]
        }
        """

        when: "I update the workout"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}", updateRequest)
                .put("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains updated data"
        def body = response.body().jsonPath()
        body.getString("eventId") != null
        body.getString("workoutId") == workout.workoutId
        body.getInt("totalExercises") == 1
        body.getInt("totalSets") == 2
    }

    def "Scenario 5: Update non-existing workout returns 404"() {
        given: "an update request"
        def updateRequest = """
        {
            "performedAt": "${Instant.now().minus(1, ChronoUnit.HOURS)}",
            "exercises": [
                {
                    "name": "Deadlift",
                    "orderInWorkout": 1,
                    "sets": [
                        { "setNumber": 1, "weightKg": 120.0, "reps": 5, "isWarmup": false }
                    ]
                }
            ]
        }
        """

        when: "I try to update a non-existing workout"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/non-existing-id", updateRequest)
                .put("/v1/workouts/event/non-existing-id")
                .then()
                .extract()

        then: "response status is 404 Not Found"
        response.statusCode() == 404
    }

    def "Scenario 6: Update workout with date more than 30 days ago fails"() {
        given: "an existing workout"
        def workout = createWorkout("workout-old-date-test-${System.currentTimeMillis()}")

        and: "an update request with performedAt more than 30 days ago"
        def tooOld = Instant.now().minus(31, ChronoUnit.DAYS).toString()
        def updateRequest = """
        {
            "performedAt": "${tooOld}",
            "exercises": [
                {
                    "name": "Bench Press",
                    "orderInWorkout": 1,
                    "sets": [
                        { "setNumber": 1, "weightKg": 80.0, "reps": 8, "isWarmup": false }
                    ]
                }
            ]
        }
        """

        when: "I try to update the workout with too old date"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}", updateRequest)
                .put("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response status is 400 Bad Request"
        response.statusCode() == 400
    }

    def "Scenario 7: Update workout with future date fails"() {
        given: "an existing workout"
        def workout = createWorkout("workout-future-date-test-${System.currentTimeMillis()}")

        and: "an update request with performedAt in the future"
        def future = Instant.now().plus(1, ChronoUnit.HOURS).toString()
        def updateRequest = """
        {
            "performedAt": "${future}",
            "exercises": [
                {
                    "name": "Pull-up",
                    "orderInWorkout": 1,
                    "sets": [
                        { "setNumber": 1, "weightKg": 0.0, "reps": 10, "isWarmup": false }
                    ]
                }
            ]
        }
        """

        when: "I try to update the workout with future date"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}", updateRequest)
                .put("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response status is 400 Bad Request"
        response.statusCode() == 400
    }

    def "Scenario 8: Update workout with empty exercises fails"() {
        given: "an existing workout"
        def workout = createWorkout("workout-empty-exercises-test-${System.currentTimeMillis()}")

        and: "an update request with empty exercises array"
        def updateRequest = """
        {
            "performedAt": "${Instant.now().minus(1, ChronoUnit.HOURS)}",
            "exercises": []
        }
        """

        when: "I try to update the workout with empty exercises"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}", updateRequest)
                .put("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response status is 400 Bad Request"
        response.statusCode() == 400
    }

    def "Scenario 9: Update workout from different device returns 404"() {
        given: "an existing workout created by one device"
        def workout = createWorkout("workout-cross-device-test-${System.currentTimeMillis()}")

        and: "an update request"
        def updateRequest = """
        {
            "performedAt": "${Instant.now().minus(1, ChronoUnit.HOURS)}",
            "exercises": [
                {
                    "name": "Row",
                    "orderInWorkout": 1,
                    "sets": [
                        { "setNumber": 1, "weightKg": 70.0, "reps": 10, "isWarmup": false }
                    ]
                }
            ]
        }
        """

        when: "I try to update the workout from a different device"
        def response = authenticatedPutRequestWithBody("different-device-id", "ZGlmZmVyZW50LXNlY3JldC0xMjM=", "/v1/workouts/event/${workout.eventId}", updateRequest)
                .put("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response status is 404 Not Found"
        response.statusCode() == 404
    }

    // ===================== E2E Flow =====================

    def "Scenario 10: Full CRUD flow - create, update, then delete"() {
        given: "a new workout created via health-events API"
        def workoutId = "workout-crud-flow-${System.currentTimeMillis()}"
        def workout = createWorkout(workoutId)

        when: "I update the workout"
        def newPerformedAt = Instant.now().minus(3, ChronoUnit.HOURS).toString()
        def updateRequest = """
        {
            "performedAt": "${newPerformedAt}",
            "source": "CRUD_TEST",
            "note": "Updated via CRUD test",
            "exercises": [
                {
                    "name": "Overhead Press",
                    "exerciseId": "shoulders_1",
                    "muscleGroup": "Shoulders",
                    "orderInWorkout": 1,
                    "sets": [
                        { "setNumber": 1, "weightKg": 40.0, "reps": 10, "isWarmup": true },
                        { "setNumber": 2, "weightKg": 50.0, "reps": 8, "isWarmup": false },
                        { "setNumber": 3, "weightKg": 50.0, "reps": 8, "isWarmup": false },
                        { "setNumber": 4, "weightKg": 50.0, "reps": 6, "isWarmup": false }
                    ]
                },
                {
                    "name": "Lateral Raise",
                    "exerciseId": "shoulders_2",
                    "muscleGroup": "Shoulders",
                    "orderInWorkout": 2,
                    "sets": [
                        { "setNumber": 1, "weightKg": 10.0, "reps": 15, "isWarmup": false },
                        { "setNumber": 2, "weightKg": 10.0, "reps": 15, "isWarmup": false }
                    ]
                }
            ]
        }
        """
        def updateResponse = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}", updateRequest)
                .put("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "workout is updated"
        updateResponse.statusCode() == 200
        def updateBody = updateResponse.body().jsonPath()
        updateBody.getString("workoutId") == workoutId
        updateBody.getInt("totalExercises") == 2
        updateBody.getInt("totalSets") == 6

        and: "original event ID is superseded (no longer accessible)"
        def originalEventResponse = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}")
                .delete("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()
        originalEventResponse.statusCode() == 404
    }

    def "Scenario 10b: Create and delete without update works"() {
        given: "a new workout created via health-events API"
        def workoutId = "workout-simple-crud-${System.currentTimeMillis()}"
        def workout = createWorkout(workoutId)

        when: "I delete the workout using the original event ID"
        def deleteResponse = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}")
                .delete("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "workout is deleted"
        deleteResponse.statusCode() == 204
    }

    def "Scenario 11: Update preserves workoutId from original event"() {
        given: "a workout with a specific workoutId"
        def specificWorkoutId = "specific-workout-id-${System.currentTimeMillis()}"
        def workout = createWorkout(specificWorkoutId)

        when: "I update the workout"
        def updateRequest = """
        {
            "performedAt": "${Instant.now().minus(1, ChronoUnit.HOURS)}",
            "exercises": [
                {
                    "name": "Barbell Curl",
                    "orderInWorkout": 1,
                    "sets": [
                        { "setNumber": 1, "weightKg": 30.0, "reps": 12, "isWarmup": false }
                    ]
                }
            ]
        }
        """
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}", updateRequest)
                .put("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response contains the original workoutId"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("workoutId") == specificWorkoutId
    }

    def "Scenario 12: Update workout with missing performedAt fails"() {
        given: "an existing workout"
        def workout = createWorkout("workout-no-date-test-${System.currentTimeMillis()}")

        and: "an update request without performedAt"
        def updateRequest = """
        {
            "exercises": [
                {
                    "name": "Bench Press",
                    "orderInWorkout": 1,
                    "sets": [
                        { "setNumber": 1, "weightKg": 80.0, "reps": 8, "isWarmup": false }
                    ]
                }
            ]
        }
        """

        when: "I try to update the workout without performedAt"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}", updateRequest)
                .put("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response status is 400 Bad Request"
        response.statusCode() == 400
    }

    def "Scenario 13: Update workout with empty sets in exercise fails"() {
        given: "an existing workout"
        def workout = createWorkout("workout-no-sets-test-${System.currentTimeMillis()}")

        and: "an update request with empty sets array"
        def updateRequest = """
        {
            "performedAt": "${Instant.now().minus(1, ChronoUnit.HOURS)}",
            "exercises": [
                {
                    "name": "Squat",
                    "orderInWorkout": 1,
                    "sets": []
                }
            ]
        }
        """

        when: "I try to update the workout with empty sets"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/workouts/event/${workout.eventId}", updateRequest)
                .put("/v1/workouts/event/${workout.eventId}")
                .then()
                .extract()

        then: "response status is 400 Bad Request"
        response.statusCode() == 400
    }
}
