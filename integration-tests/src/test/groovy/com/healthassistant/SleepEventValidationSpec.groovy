package com.healthassistant

import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Sleep Session event validation via generic health events endpoint
 */
@Title("Feature: Sleep Session Event Validation via Health Events API")
class SleepEventValidationSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "Scenario 1: Submit valid sleep session event returns success"() {
        given: "a valid sleep session event request"
        def request = createHealthEventsRequest(createSleepSessionEvent())

        when: "I submit the sleep session event"
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

    def "Scenario 2: Sleep session event is stored in database with correct type and payload"() {
        given: "a valid sleep session event request"
        def request = createHealthEventsRequest(createSleepSessionEvent(480))

        when: "I submit the sleep session event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = findAllEvents()
        events.size() == 1

        and: "event has correct type"
        def sleepEvent = events.first()
        sleepEvent.eventType() == "SleepSessionRecorded.v1"

        and: "event has correct device ID"
        sleepEvent.deviceId() == "mobile-app"

        and: "event has correct payload"
        def payload = sleepEvent.payload()
        payload.get("sleepId") == "sleep-session-validation-test"
        payload.get("sleepStart") == "2025-11-20T22:00:00Z"
        payload.get("sleepEnd") == "2025-11-21T06:00:00Z"
        payload.get("totalMinutes") == 480
        payload.get("originPackage") == "com.google.android.apps.fitness"
    }

    def "Scenario 3: Sleep session event with missing sleepId returns validation error"() {
        given: "a sleep session event without sleepId"
        def event = """
        {
            "type": "SleepSessionRecorded.v1",
            "occurredAt": "2025-11-21T06:00:00Z",
            "payload": {
                "sleepStart": "2025-11-20T22:00:00Z",
                "sleepEnd": "2025-11-21T06:00:00Z",
                "totalMinutes": 480,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the sleep session event"
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

    def "Scenario 4: Sleep session event with missing sleepStart returns validation error"() {
        given: "a sleep session event without sleepStart"
        def event = """
        {
            "type": "SleepSessionRecorded.v1",
            "occurredAt": "2025-11-21T06:00:00Z",
            "payload": {
                "sleepId": "sleep-no-start",
                "sleepEnd": "2025-11-21T06:00:00Z",
                "totalMinutes": 480,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the sleep session event"
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

    def "Scenario 5: Sleep session event with missing sleepEnd returns validation error"() {
        given: "a sleep session event without sleepEnd"
        def event = """
        {
            "type": "SleepSessionRecorded.v1",
            "occurredAt": "2025-11-21T06:00:00Z",
            "payload": {
                "sleepId": "sleep-no-end",
                "sleepStart": "2025-11-20T22:00:00Z",
                "totalMinutes": 480,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the sleep session event"
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

    def "Scenario 6: Sleep session event with missing totalMinutes returns validation error"() {
        given: "a sleep session event without totalMinutes"
        def event = """
        {
            "type": "SleepSessionRecorded.v1",
            "occurredAt": "2025-11-21T06:00:00Z",
            "payload": {
                "sleepId": "sleep-no-duration",
                "sleepStart": "2025-11-20T22:00:00Z",
                "sleepEnd": "2025-11-21T06:00:00Z",
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the sleep session event"
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

    def "Scenario 7: Sleep session event with missing originPackage returns validation error"() {
        given: "a sleep session event without originPackage"
        def event = """
        {
            "type": "SleepSessionRecorded.v1",
            "occurredAt": "2025-11-21T06:00:00Z",
            "payload": {
                "sleepId": "sleep-no-origin",
                "sleepStart": "2025-11-20T22:00:00Z",
                "sleepEnd": "2025-11-21T06:00:00Z",
                "totalMinutes": 480
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the sleep session event"
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

    def "Scenario 8: Sleep session event with negative totalMinutes returns validation error"() {
        given: "a sleep session event with negative totalMinutes"
        def event = """
        {
            "type": "SleepSessionRecorded.v1",
            "occurredAt": "2025-11-21T06:00:00Z",
            "payload": {
                "sleepId": "sleep-negative-duration",
                "sleepStart": "2025-11-20T22:00:00Z",
                "sleepEnd": "2025-11-21T06:00:00Z",
                "totalMinutes": -60,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the sleep session event"
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

    def "Scenario 9: Sleep session event occurredAt timestamp is preserved"() {
        given: "a sleep session event with specific timestamp"
        def occurredAt = "2025-11-21T06:15:30Z"
        def event = """
        {
            "type": "SleepSessionRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "sleepId": "sleep-timestamp-test",
                "sleepStart": "2025-11-20T22:00:00Z",
                "sleepEnd": "2025-11-21T06:00:00Z",
                "totalMinutes": 480,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the sleep session event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches the submitted timestamp"
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
        def sleepEvent = dbEvents.first()
        def storedOccurredAt = Instant.parse(sleepEvent.occurredAt().toString())
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

    String createSleepSessionEvent(int totalMinutes = 450) {
        return """
        {
            "type": "SleepSessionRecorded.v1",
            "occurredAt": "2025-11-21T06:00:00Z",
            "payload": {
                "sleepId": "sleep-session-validation-test",
                "sleepStart": "2025-11-20T22:00:00Z",
                "sleepEnd": "2025-11-21T06:00:00Z",
                "totalMinutes": ${totalMinutes},
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """.stripIndent().trim()
    }
}
