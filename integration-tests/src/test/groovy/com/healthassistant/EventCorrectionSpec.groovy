package com.healthassistant

import spock.lang.Title

import java.time.Instant

@Title("Feature: Event Correction via EventCorrected.v1 and duplicate idempotency key handling")
class EventCorrectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-correction"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
    }

    // ===========================================
    // Duplicate Idempotency Key Handling
    // ===========================================

    def "Scenario 1: Duplicate idempotency key returns error suggesting EventCorrected.v1"() {
        given: "an event with specific idempotency key exists"
        def idempotencyKey = "dup-key-" + UUID.randomUUID()
        def firstBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-12-10T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-10T09:00:00Z",
                        "bucketEnd": "2025-12-10T10:00:00Z",
                        "count": 1000,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        def firstResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", firstBody)
                .post("/v1/health-events")
                .then()
                .extract()

        assert firstResponse.statusCode() == 200
        assert firstResponse.body().jsonPath().getString("events[0].status") == "stored"

        when: "I submit another event with the same idempotency key"
        def secondBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-12-10T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-10T09:00:00Z",
                        "bucketEnd": "2025-12-10T10:00:00Z",
                        "count": 2000,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        def secondResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", secondBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates duplicate with suggestion to use EventCorrected.v1"
        secondResponse.statusCode() == 200
        secondResponse.body().jsonPath().getString("events[0].status") == "duplicate"
        secondResponse.body().jsonPath().getString("events[0].error.message").contains("EventCorrected.v1")
        secondResponse.body().jsonPath().getInt("summary.duplicate") == 1
    }

    def "Scenario 2: Duplicate idempotency key does NOT update the original event"() {
        given: "a steps event with 1000 steps exists"
        def idempotencyKey = "no-update-" + UUID.randomUUID()
        def firstBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-12-11T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-11T09:00:00Z",
                        "bucketEnd": "2025-12-11T10:00:00Z",
                        "count": 1000,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", firstBody)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I try to update to 5000 steps using same idempotency key"
        def updateBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-12-11T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-11T09:00:00Z",
                        "bucketEnd": "2025-12-11T10:00:00Z",
                        "count": 5000,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", updateBody)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "original value (1000) is preserved - event was NOT updated"
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1
        events[0].payload().count() == 1000
    }

    // ===========================================
    // EventCorrected.v1 Functionality
    // ===========================================

    def "Scenario 3: EventCorrected.v1 supersedes target event"() {
        given: "a steps event exists"
        def stepsKey = "to-correct-" + UUID.randomUUID()
        def stepsBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${stepsKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-12-12T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-12-12T09:00:00Z",
                        "bucketEnd": "2025-12-12T10:00:00Z",
                        "count": 1000,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        def stepsResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", stepsBody)
                .post("/v1/health-events")
                .then()
                .extract()

        assert stepsResponse.statusCode() == 200
        def eventId = stepsResponse.body().jsonPath().getString("events[0].eventId")

        when: "I submit EventCorrected.v1 with corrected data"
        def correctionKey = "correction-" + UUID.randomUUID()
        def correctionBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${correctionKey}",
                    "type": "EventCorrected.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "${eventId}",
                        "correctedEventType": "StepsBucketedRecorded.v1",
                        "correctedOccurredAt": "2025-12-12T10:00:00Z",
                        "correctedPayload": {
                            "bucketStart": "2025-12-12T09:00:00Z",
                            "bucketEnd": "2025-12-12T10:00:00Z",
                            "count": 2500,
                            "originPackage": "com.test"
                        },
                        "reason": "Wrong step count"
                    }
                }
            ]
        }
        """

        def correctionResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", correctionBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "correction event is stored successfully"
        correctionResponse.statusCode() == 200
        correctionResponse.body().jsonPath().getString("events[0].status") == "stored"

        and: "original event is superseded (not visible in queries)"
        def events = findEventsForDevice(DEVICE_ID)
        // Only EventCorrected.v1 should be visible
        events.size() == 1
        events[0].eventType() == "EventCorrected.v1"
    }

    def "Scenario 4: EventCorrected.v1 can correct workout data"() {
        given: "a workout event exists with wrong weight"
        def workoutKey = "workout-to-correct-" + UUID.randomUUID()
        def workoutBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${workoutKey}",
                    "type": "WorkoutRecorded.v1",
                    "occurredAt": "2025-12-13T18:00:00Z",
                    "payload": {
                        "workoutId": "workout-correct-test",
                        "performedAt": "2025-12-13T17:00:00Z",
                        "exercises": [
                            {
                                "name": "Squat",
                                "sets": [{"reps": 5, "weightKg": 100.0}]
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
                .extract()

        assert workoutResponse.statusCode() == 200
        def eventId = workoutResponse.body().jsonPath().getString("events[0].eventId")

        when: "I correct the weight from 100kg to 120kg"
        def correctionKey = "correct-workout-" + UUID.randomUUID()
        def correctionBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "${correctionKey}",
                    "type": "EventCorrected.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "${eventId}",
                        "correctedEventType": "WorkoutRecorded.v1",
                        "correctedOccurredAt": "2025-12-13T18:00:00Z",
                        "correctedPayload": {
                            "workoutId": "workout-correct-test",
                            "performedAt": "2025-12-13T17:00:00Z",
                            "exercises": [
                                {
                                    "name": "Squat",
                                    "sets": [{"reps": 5, "weightKg": 120.0}]
                                }
                            ]
                        },
                        "reason": "Entered wrong weight"
                    }
                }
            ]
        }
        """

        def correctionResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", correctionBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "correction is successful"
        correctionResponse.statusCode() == 200
        correctionResponse.body().jsonPath().getString("events[0].status") == "stored"
    }

    // ===========================================
    // Validation
    // ===========================================

    def "Scenario 5: EventCorrected.v1 requires targetEventId"() {
        when: "I submit EventCorrected.v1 without targetEventId"
        def correctionBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "invalid-correction-${UUID.randomUUID()}",
                    "type": "EventCorrected.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "correctedEventType": "StepsBucketedRecorded.v1",
                        "correctedOccurredAt": "2025-12-14T10:00:00Z",
                        "correctedPayload": {"count": 100}
                    }
                }
            ]
        }
        """

        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", correctionBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "validation fails"
        response.statusCode() == 200
        response.body().jsonPath().getString("events[0].status") == "invalid"
        response.body().jsonPath().getString("events[0].error.message").contains("targetEventId")
    }

    def "Scenario 6: EventCorrected.v1 requires correctedEventType"() {
        when: "I submit EventCorrected.v1 without correctedEventType"
        def correctionBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "no-type-${UUID.randomUUID()}",
                    "type": "EventCorrected.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "some-event-id",
                        "correctedOccurredAt": "2025-12-14T10:00:00Z",
                        "correctedPayload": {"count": 100}
                    }
                }
            ]
        }
        """

        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", correctionBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "validation fails"
        response.statusCode() == 200
        response.body().jsonPath().getString("events[0].status") == "invalid"
        response.body().jsonPath().getString("events[0].error.message").contains("correctedEventType")
    }

    def "Scenario 7: EventCorrected.v1 validates correctedEventType is valid"() {
        when: "I submit EventCorrected.v1 with invalid correctedEventType"
        def correctionBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "invalid-type-${UUID.randomUUID()}",
                    "type": "EventCorrected.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "some-event-id",
                        "correctedEventType": "InvalidType.v99",
                        "correctedOccurredAt": "2025-12-14T10:00:00Z",
                        "correctedPayload": {"count": 100}
                    }
                }
            ]
        }
        """

        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", correctionBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "validation fails with invalid event type error"
        response.statusCode() == 200
        response.body().jsonPath().getString("events[0].status") == "invalid"
        response.body().jsonPath().getString("events[0].error.message").contains("invalid event type")
    }

    def "Scenario 8: EventCorrected.v1 requires correctedPayload"() {
        when: "I submit EventCorrected.v1 without correctedPayload"
        def correctionBody = """
        {
            "deviceId": "${DEVICE_ID}",
            "events": [
                {
                    "idempotencyKey": "no-payload-${UUID.randomUUID()}",
                    "type": "EventCorrected.v1",
                    "occurredAt": "${Instant.now()}",
                    "payload": {
                        "targetEventId": "some-event-id",
                        "correctedEventType": "StepsBucketedRecorded.v1",
                        "correctedOccurredAt": "2025-12-14T10:00:00Z"
                    }
                }
            ]
        }
        """

        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", correctionBody)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "validation fails"
        response.statusCode() == 200
        response.body().jsonPath().getString("events[0].status") == "invalid"
        response.body().jsonPath().getString("events[0].error.message").contains("correctedPayload")
    }
}
