package com.healthassistant

import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Active Minutes event validation via generic health events endpoint
 */
@Title("Feature: Active Minutes Event Validation via Health Events API")
class ActiveMinutesEventSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "Scenario 1: Submit valid active minutes event returns success"() {
        given: "a valid active minutes event request"
        def request = createHealthEventsRequest(createActiveMinutesEvent())

        when: "I submit the active minutes event"
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

    def "Scenario 2: Active minutes event is stored in database with correct type and payload"() {
        given: "a valid active minutes event request"
        def request = createHealthEventsRequest(createActiveMinutesEvent(45))

        when: "I submit the active minutes event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = findAllEvents()
        events.size() == 1

        and: "event has correct type"
        def activeMinutesEvent = events.first()
        activeMinutesEvent.eventType() == "ActiveMinutesRecorded.v1"

        and: "event has correct device ID"
        activeMinutesEvent.deviceId() == "mobile-app"

        and: "event has correct payload"
        def payload = activeMinutesEvent.payload()
        payload.get("bucketStart") == "2025-11-21T10:00:00Z"
        payload.get("bucketEnd") == "2025-11-21T11:00:00Z"
        payload.get("activeMinutes") == 45
        payload.get("originPackage") == "com.google.android.apps.fitness"
    }

    def "Scenario 3: Active minutes event with missing bucketStart returns validation error"() {
        given: "an active minutes event without bucketStart"
        def event = """
        {
            "type": "ActiveMinutesRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketEnd": "2025-11-21T11:00:00Z",
                "activeMinutes": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active minutes event"
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

    def "Scenario 4: Active minutes event with missing bucketEnd returns validation error"() {
        given: "an active minutes event without bucketEnd"
        def event = """
        {
            "type": "ActiveMinutesRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "activeMinutes": 30,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active minutes event"
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

    def "Scenario 5: Active minutes event with missing activeMinutes returns validation error"() {
        given: "an active minutes event without activeMinutes"
        def event = """
        {
            "type": "ActiveMinutesRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active minutes event"
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

    def "Scenario 6: Active minutes event with missing originPackage returns validation error"() {
        given: "an active minutes event without originPackage"
        def event = """
        {
            "type": "ActiveMinutesRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "activeMinutes": 30
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active minutes event"
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

    def "Scenario 7: Active minutes event with negative activeMinutes returns validation error"() {
        given: "an active minutes event with negative activeMinutes"
        def event = """
        {
            "type": "ActiveMinutesRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "activeMinutes": -15,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active minutes event"
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

    def "Scenario 8: Active minutes event with zero activeMinutes is valid (boundary case)"() {
        given: "an active minutes event with zero activeMinutes"
        def event = """
        {
            "type": "ActiveMinutesRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "activeMinutes": 0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active minutes event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored"
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
        def activeMinutesEvent = dbEvents.first()
        activeMinutesEvent.payload().get("activeMinutes") == 0
    }

    def "Scenario 9: Active minutes event occurredAt timestamp is preserved"() {
        given: "an active minutes event with specific timestamp"
        def occurredAt = "2025-11-21T11:30:45Z"
        def event = """
        {
            "type": "ActiveMinutesRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "activeMinutes": 55,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active minutes event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches the submitted timestamp"
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
        def activeMinutesEvent = dbEvents.first()
        def storedOccurredAt = Instant.parse(activeMinutesEvent.occurredAt().toString())
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

    String createActiveMinutesEvent(int activeMinutes = 30) {
        return """
        {
            "type": "ActiveMinutesRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "activeMinutes": ${activeMinutes},
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """.stripIndent().trim()
    }
}
