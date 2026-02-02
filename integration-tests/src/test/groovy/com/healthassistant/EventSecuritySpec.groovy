package com.healthassistant

import spock.lang.Title

import java.time.Instant

@Title("Feature: Security tests for cross-device event manipulation attacks")
class EventSecuritySpec extends BaseIntegrationSpec {

    private static final String DEVICE_A = "test-device"
    private static final String DEVICE_B = "different-device-id"
    private static final String SECRET_A = "dGVzdC1zZWNyZXQtMTIz"
    private static final String SECRET_B = "ZGlmZmVyZW50LXNlY3JldC0xMjM="

    def setup() {
        cleanupEventsForDevice(DEVICE_A)
        cleanupEventsForDevice(DEVICE_B)
    }

    def "Scenario 1: Device B cannot delete Device A's events"() {
        given: "Device A creates a steps event"
        def stepsKey = "security-steps-" + UUID.randomUUID()
        def stepsBody = """
        {
            "deviceId": "${DEVICE_A}",
            "events": [
                {
                    "idempotencyKey": "${stepsKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-12-10T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-10T09:00:00Z",
                        "bucketEnd": "2025-12-10T10:00:00Z",
                        "count": 5000,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        def stepsResponse = authenticatedPostRequestWithBody(DEVICE_A, SECRET_A, "/v1/health-events", stepsBody)
                .post("/v1/health-events")
                .then()
                .extract()

        assert stepsResponse.statusCode() == 200
        def eventId = stepsResponse.body().jsonPath().getString("events[0].eventId")
        assert eventId != null

        when: "Device B attempts to delete Device A's event"
        def deleteKey = "delete-attack-" + UUID.randomUUID()
        def deleteBody = """
        {
            "deviceId": "${DEVICE_B}",
            "events": [
                {
                    "idempotencyKey": "${deleteKey}",
                    "type": "EventDeleted.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "${eventId}",
                        "reason": "Malicious deletion attempt"
                    }
                }
            ]
        }
        """

        def deleteResponse = authenticatedPostRequestWithBody(DEVICE_B, SECRET_B, "/v1/health-events", deleteBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "The EventDeleted event is stored but the target event is NOT deleted"
        deleteResponse.statusCode() == 200
        deleteResponse.body().jsonPath().getString("events[0].status") == "stored"

        and: "Device A's original event still exists and is not marked as deleted"
        def deviceAEvents = findEventsForDevice(DEVICE_A)
        deviceAEvents.size() == 1
        deviceAEvents[0].eventType() == "StepsBucketedRecorded.v1"
    }

    def "Scenario 2: Device B cannot correct Device A's events"() {
        given: "Device A creates a sleep event"
        def sleepKey = "security-sleep-" + UUID.randomUUID()
        def sleepBody = """
        {
            "deviceId": "${DEVICE_A}",
            "events": [
                {
                    "idempotencyKey": "${sleepKey}",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-12-11T08:00:00Z",
                    "payload": {
                        "sleepId": "sleep-security-test",
                        "sleepStart": "2025-12-10T23:00:00Z",
                        "sleepEnd": "2025-12-11T07:00:00Z",
                        "totalMinutes": 480,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        def sleepResponse = authenticatedPostRequestWithBody(DEVICE_A, SECRET_A, "/v1/health-events", sleepBody)
                .post("/v1/health-events")
                .then()
                .extract()

        assert sleepResponse.statusCode() == 200
        def eventId = sleepResponse.body().jsonPath().getString("events[0].eventId")

        when: "Device B attempts to correct Device A's sleep event"
        def correctKey = "correct-attack-" + UUID.randomUUID()
        def correctBody = """
        {
            "deviceId": "${DEVICE_B}",
            "events": [
                {
                    "idempotencyKey": "${correctKey}",
                    "type": "EventCorrected.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "${eventId}",
                        "correctedEventType": "SleepSessionRecorded.v1",
                        "correctedOccurredAt": "2025-12-11T08:00:00Z",
                        "correctedPayload": {
                            "sleepId": "hacked-sleep",
                            "sleepStart": "2025-12-10T23:00:00Z",
                            "sleepEnd": "2025-12-11T07:00:00Z",
                            "totalMinutes": 60,
                            "originPackage": "com.attacker"
                        },
                        "reason": "Malicious correction attempt"
                    }
                }
            ]
        }
        """

        def correctResponse = authenticatedPostRequestWithBody(DEVICE_B, SECRET_B, "/v1/health-events", correctBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "The EventCorrected event is stored but the target event is NOT superseded"
        correctResponse.statusCode() == 200
        correctResponse.body().jsonPath().getString("events[0].status") == "stored"

        and: "Device A's original event is unchanged"
        def deviceAEvents = findEventsForDevice(DEVICE_A)
        deviceAEvents.size() == 1
        deviceAEvents[0].eventType() == "SleepSessionRecorded.v1"
    }

    def "Scenario 3: Device A can delete its own events"() {
        given: "Device A creates a meal event"
        def mealKey = "security-meal-" + UUID.randomUUID()
        def mealBody = """
        {
            "deviceId": "${DEVICE_A}",
            "events": [
                {
                    "idempotencyKey": "${mealKey}",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-12T13:00:00Z",
                    "payload": {
                        "title": "Lunch",
                        "mealType": "LUNCH",
                        "eatenAt": "2025-12-12T12:30:00Z",
                        "caloriesKcal": 700,
                        "proteinGrams": 35.0,
                        "fatGrams": 25.0,
                        "carbohydratesGrams": 80.0,
                        "healthRating": "HEALTHY"
                    }
                }
            ]
        }
        """

        def mealResponse = authenticatedPostRequestWithBody(DEVICE_A, SECRET_A, "/v1/health-events", mealBody)
                .post("/v1/health-events")
                .then()
                .extract()

        assert mealResponse.statusCode() == 200
        def eventId = mealResponse.body().jsonPath().getString("events[0].eventId")

        when: "Device A deletes its own event"
        def deleteKey = "delete-own-" + UUID.randomUUID()
        def deleteBody = """
        {
            "deviceId": "${DEVICE_A}",
            "events": [
                {
                    "idempotencyKey": "${deleteKey}",
                    "type": "EventDeleted.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "${eventId}",
                        "reason": "Legitimate deletion"
                    }
                }
            ]
        }
        """

        def deleteResponse = authenticatedPostRequestWithBody(DEVICE_A, SECRET_A, "/v1/health-events", deleteBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "Deletion is successful"
        deleteResponse.statusCode() == 200
        deleteResponse.body().jsonPath().getString("events[0].status") == "stored"

        and: "Original meal event is deleted (only deletion event visible)"
        def deviceAEvents = findEventsForDevice(DEVICE_A)
        deviceAEvents.size() == 1
        deviceAEvents[0].eventType() == "EventDeleted.v1"
    }

    def "Scenario 4: Device A can correct its own events"() {
        given: "Device A creates a workout event"
        def workoutKey = "security-workout-" + UUID.randomUUID()
        def workoutBody = """
        {
            "deviceId": "${DEVICE_A}",
            "events": [
                {
                    "idempotencyKey": "${workoutKey}",
                    "type": "WorkoutRecorded.v1",
                    "occurredAt": "2025-12-13T18:00:00Z",
                    "payload": {
                        "workoutId": "workout-security-test",
                        "performedAt": "2025-12-13T17:00:00Z",
                        "exercises": [
                            {
                                "name": "Squat",
                                "orderInWorkout": 1,
                                "sets": [{"setNumber": 1, "reps": 8, "weightKg": 100.0, "isWarmup": false}]
                            }
                        ]
                    }
                }
            ]
        }
        """

        def workoutResponse = authenticatedPostRequestWithBody(DEVICE_A, SECRET_A, "/v1/health-events", workoutBody)
                .post("/v1/health-events")
                .then()
                .extract()

        assert workoutResponse.statusCode() == 200
        def eventId = workoutResponse.body().jsonPath().getString("events[0].eventId")

        when: "Device A corrects its own workout event"
        def correctKey = "correct-own-" + UUID.randomUUID()
        def correctBody = """
        {
            "deviceId": "${DEVICE_A}",
            "events": [
                {
                    "idempotencyKey": "${correctKey}",
                    "type": "EventCorrected.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "${eventId}",
                        "correctedEventType": "WorkoutRecorded.v1",
                        "correctedOccurredAt": "2025-12-13T18:00:00Z",
                        "correctedPayload": {
                            "workoutId": "workout-security-test-corrected",
                            "performedAt": "2025-12-13T17:00:00Z",
                            "exercises": [
                                {
                                    "name": "Squat",
                                    "orderInWorkout": 1,
                                    "sets": [{"setNumber": 1, "reps": 10, "weightKg": 120.0, "isWarmup": false}]
                                }
                            ]
                        },
                        "reason": "Fixed weight"
                    }
                }
            ]
        }
        """

        def correctResponse = authenticatedPostRequestWithBody(DEVICE_A, SECRET_A, "/v1/health-events", correctBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "Correction is successful"
        correctResponse.statusCode() == 200
        correctResponse.body().jsonPath().getString("events[0].status") == "stored"

        and: "Original event is superseded (only correction event visible)"
        def deviceAEvents = findEventsForDevice(DEVICE_A)
        deviceAEvents.size() == 1
        deviceAEvents[0].eventType() == "EventCorrected.v1"
    }

    def "Scenario 5: Batch attack - Device B tries to delete multiple Device A events"() {
        given: "Device A creates multiple events"
        def key1 = "batch-attack-1-" + UUID.randomUUID()
        def key2 = "batch-attack-2-" + UUID.randomUUID()
        def eventsBody = """
        {
            "deviceId": "${DEVICE_A}",
            "events": [
                {
                    "idempotencyKey": "${key1}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-12-14T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-14T09:00:00Z",
                        "bucketEnd": "2025-12-14T10:00:00Z",
                        "count": 3000,
                        "originPackage": "com.test"
                    }
                },
                {
                    "idempotencyKey": "${key2}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-12-14T11:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-14T10:00:00Z",
                        "bucketEnd": "2025-12-14T11:00:00Z",
                        "count": 4000,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        def eventsResponse = authenticatedPostRequestWithBody(DEVICE_A, SECRET_A, "/v1/health-events", eventsBody)
                .post("/v1/health-events")
                .then()
                .extract()

        assert eventsResponse.statusCode() == 200
        def eventId1 = eventsResponse.body().jsonPath().getString("events[0].eventId")
        def eventId2 = eventsResponse.body().jsonPath().getString("events[1].eventId")

        when: "Device B tries to delete both events in a batch"
        def attackBody = """
        {
            "deviceId": "${DEVICE_B}",
            "events": [
                {
                    "idempotencyKey": "attack-del-1-${UUID.randomUUID()}",
                    "type": "EventDeleted.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "${eventId1}",
                        "reason": "Batch attack 1"
                    }
                },
                {
                    "idempotencyKey": "attack-del-2-${UUID.randomUUID()}",
                    "type": "EventDeleted.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "${eventId2}",
                        "reason": "Batch attack 2"
                    }
                }
            ]
        }
        """

        def attackResponse = authenticatedPostRequestWithBody(DEVICE_B, SECRET_B, "/v1/health-events", attackBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "The deletion events are stored but target events are not affected"
        attackResponse.statusCode() == 200

        and: "Device A's events remain intact"
        def deviceAEvents = findEventsForDevice(DEVICE_A)
        deviceAEvents.size() == 2
        deviceAEvents.every { it.eventType() == "StepsBucketedRecorded.v1" }
    }

    def "Scenario 6: Cross-device deletion with non-existent eventId"() {
        when: "Device B tries to delete a non-existent event"
        def deleteKey = "delete-nonexistent-" + UUID.randomUUID()
        def deleteBody = """
        {
            "deviceId": "${DEVICE_B}",
            "events": [
                {
                    "idempotencyKey": "${deleteKey}",
                    "type": "EventDeleted.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "nonexistent-event-id-12345",
                        "reason": "Trying to delete non-existent"
                    }
                }
            ]
        }
        """

        def response = authenticatedPostRequestWithBody(DEVICE_B, SECRET_B, "/v1/health-events", deleteBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "The deletion event is stored (event log is append-only)"
        response.statusCode() == 200
        response.body().jsonPath().getString("events[0].status") == "stored"

        and: "No events were affected"
        def deviceBEvents = findEventsForDevice(DEVICE_B)
        deviceBEvents.size() == 1
        deviceBEvents[0].eventType() == "EventDeleted.v1"
    }
}
