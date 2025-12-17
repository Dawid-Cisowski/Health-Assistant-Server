package com.healthassistant

import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Steps event validation via generic health events endpoint
 */
@Title("Feature: Steps Event Validation via Health Events API")
class StepsEventValidationSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "Scenario 1: Submit valid steps event returns success"() {
        given: "a valid steps event request"
        def request = createHealthEventsRequest(createStepsEvent())

        when: "I submit the steps event"
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

    def "Scenario 2: Steps event is stored in database with correct type and payload"() {
        given: "a valid steps event request"
        def request = createHealthEventsRequest(createStepsEvent(5000))

        when: "I submit the steps event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = findAllEvents()
        events.size() == 1

        and: "event has correct type"
        def stepsEvent = events.first()
        stepsEvent.eventType() == "StepsBucketedRecorded.v1"

        and: "event has correct device ID"
        stepsEvent.deviceId() == "mobile-app"

        and: "event has correct payload"
        def payload = stepsEvent.payload()
        payload.get("bucketStart") == "2025-11-21T10:00:00Z"
        payload.get("bucketEnd") == "2025-11-21T11:00:00Z"
        payload.get("count") == 5000
        payload.get("originPackage") == "com.google.android.apps.fitness"
    }

    def "Scenario 3: Duplicate steps event returns duplicate status"() {
        given: "a valid steps event request with explicit idempotency key"
        def event = """
        {
            "idempotencyKey": "steps-test-2025-11-21-1",
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "count": 3000,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        and: "I submit the steps event first time"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I submit the same steps event again"
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
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
    }

    def "Scenario 4: Steps event with missing bucketStart returns validation error"() {
        given: "a steps event without bucketStart"
        def event = """
        {
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketEnd": "2025-11-21T11:00:00Z",
                "count": 1000,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the steps event"
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

    def "Scenario 5: Steps event with missing bucketEnd returns validation error"() {
        given: "a steps event without bucketEnd"
        def event = """
        {
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "count": 1000,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the steps event"
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

    def "Scenario 6: Steps event with missing count returns validation error"() {
        given: "a steps event without count"
        def event = """
        {
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the steps event"
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

    def "Scenario 7: Steps event with missing originPackage returns validation error"() {
        given: "a steps event without originPackage"
        def event = """
        {
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "count": 1000
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the steps event"
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

    def "Scenario 8: Steps event with negative count returns validation error"() {
        given: "a steps event with negative count"
        def event = """
        {
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "count": -500,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the steps event"
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

    def "Scenario 9: Steps event with zero count is valid (boundary case)"() {
        given: "a steps event with zero count"
        def event = """
        {
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "count": 0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the steps event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored"
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
        def stepsEvent = dbEvents.first()
        stepsEvent.payload().get("count") == 0
    }

    def "Scenario 10: Steps event occurredAt timestamp is preserved"() {
        given: "a steps event with specific timestamp"
        def occurredAt = "2025-11-21T11:30:45Z"
        def event = """
        {
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "count": 7500,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the steps event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches the submitted timestamp"
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
        def stepsEvent = dbEvents.first()
        def storedOccurredAt = Instant.parse(stepsEvent.occurredAt().toString())
        storedOccurredAt == Instant.parse(occurredAt)
    }

    // Helper methods

    String createHealthEventsRequest(String event) {
        return """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """.stripIndent().trim()
    }

    String createStepsEvent(int count = 1000) {
        return """
        {
            "type": "StepsBucketedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "count": ${count},
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """.stripIndent().trim()
    }
}
