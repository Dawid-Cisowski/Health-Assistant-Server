package com.healthassistant

import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Heart Rate Summary event ingestion via generic health events endpoint
 */
@Title("Feature: Heart Rate Summary Event Ingestion via Health Events API")
class HeartRateEventSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-heartrate"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
    }

    def "Scenario 1: Submit valid heart rate summary event returns success"() {
        given: "a valid heart rate summary event request"
        def request = createHealthEventsRequest(createHeartRateSummaryEvent())

        when: "I submit the heart rate event"
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

    def "Scenario 2: Heart rate event is stored in database with correct type and payload"() {
        given: "a valid heart rate summary event request"
        def request = createHealthEventsRequest(createHeartRateSummaryEvent(78.5, 62, 115, 46))

        when: "I submit the heart rate event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1

        and: "event has correct type"
        def hrEvent = events.first()
        hrEvent.eventType() == "HeartRateSummaryRecorded.v1"

        and: "event has correct device ID"
        hrEvent.deviceId() == DEVICE_ID

        and: "event has correct payload"
        def payload = hrEvent.payload()
        payload.bucketStart() == Instant.parse("2025-11-21T10:00:00Z")
        payload.bucketEnd() == Instant.parse("2025-11-21T10:15:00Z")
        payload.avg() == 78.5
        payload.min() == 62
        payload.max() == 115
        payload.samples() == 46
        payload.originPackage() == "com.google.android.apps.fitness"
    }

    def "Scenario 3: Duplicate heart rate event returns duplicate status"() {
        given: "a valid heart rate event request with explicit idempotency key"
        def event = """
        {
            "idempotencyKey": "hr-test-2025-11-21-1",
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 75.0,
                "min": 60,
                "max": 95,
                "samples": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        and: "I submit the heart rate event first time"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I submit the same heart rate event again"
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
        events[0].get("status") == "duplicate"

        and: "only one event is stored in database"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
    }

    def "Scenario 4: Heart rate event with missing bucketStart returns validation error"() {
        given: "a heart rate event without bucketStart"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 75.0,
                "min": 60,
                "max": 95,
                "samples": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 5: Heart rate event with missing bucketEnd returns validation error"() {
        given: "a heart rate event without bucketEnd"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "avg": 75.0,
                "min": 60,
                "max": 95,
                "samples": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 6: Heart rate event with missing avg returns validation error"() {
        given: "a heart rate event without avg"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "min": 60,
                "max": 95,
                "samples": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 7: Heart rate event with missing min returns validation error"() {
        given: "a heart rate event without min"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 75.0,
                "max": 95,
                "samples": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 8: Heart rate event with missing max returns validation error"() {
        given: "a heart rate event without max"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 75.0,
                "min": 60,
                "samples": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 9: Heart rate event with missing samples returns validation error"() {
        given: "a heart rate event without samples"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 75.0,
                "min": 60,
                "max": 95,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 10: Heart rate event with missing originPackage returns validation error"() {
        given: "a heart rate event without originPackage"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 75.0,
                "min": 60,
                "max": 95,
                "samples": 30
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 11: Heart rate event with negative avg returns validation error"() {
        given: "a heart rate event with negative avg"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": -75.0,
                "min": 60,
                "max": 95,
                "samples": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 12: Heart rate event with negative min returns validation error"() {
        given: "a heart rate event with negative min"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 75.0,
                "min": -10,
                "max": 95,
                "samples": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 13: Heart rate event with negative max returns validation error"() {
        given: "a heart rate event with negative max"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 75.0,
                "min": 60,
                "max": -95,
                "samples": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 14: Heart rate event with zero samples returns validation error"() {
        given: "a heart rate event with zero samples"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 75.0,
                "min": 60,
                "max": 95,
                "samples": 0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
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

    def "Scenario 15: Heart rate event with samples=1 is valid (boundary case)"() {
        given: "a heart rate event with samples=1"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 72.0,
                "min": 72,
                "max": 72,
                "samples": 1,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
        def hrEvent = dbEvents.first()
        hrEvent.payload().samples() == 1
    }

    def "Scenario 16: Heart rate event occurredAt timestamp is preserved"() {
        given: "a heart rate event with specific timestamp"
        def occurredAt = "2025-11-21T10:15:30Z"
        def event = """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": 80.0,
                "min": 65,
                "max": 100,
                "samples": 50,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the heart rate event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches the submitted timestamp"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
        def hrEvent = dbEvents.first()
        def storedOccurredAt = Instant.parse(hrEvent.occurredAt().toString())
        storedOccurredAt == Instant.parse(occurredAt)
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

    String createHeartRateSummaryEvent(double avg = 75.0, int min = 60, int max = 95, int samples = 30) {
        return """
        {
            "type": "HeartRateSummaryRecorded.v1",
            "occurredAt": "2025-11-21T10:15:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T10:15:00Z",
                "avg": ${avg},
                "min": ${min},
                "max": ${max},
                "samples": ${samples},
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """.stripIndent().trim()
    }
}
