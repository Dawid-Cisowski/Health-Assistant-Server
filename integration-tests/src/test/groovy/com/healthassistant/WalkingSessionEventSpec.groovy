package com.healthassistant

import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Walking Session event validation via generic health events endpoint
 */
@Title("Feature: Walking Session Event Validation via Health Events API")
class WalkingSessionEventSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "Scenario 1: Submit valid walking session event returns success"() {
        given: "a valid walking session event request"
        def request = createHealthEventsRequest(createWalkingSessionEvent())

        when: "I submit the walking session event"
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

    def "Scenario 2: Walking session event is stored in database with correct type and payload"() {
        given: "a valid walking session event request with all fields"
        def request = createHealthEventsRequest(createWalkingSessionEventWithAllFields())

        when: "I submit the walking session event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = findAllEvents()
        events.size() == 1

        and: "event has correct type"
        def walkEvent = events.first()
        walkEvent.eventType() == "WalkingSessionRecorded.v1"

        and: "event has correct device ID"
        walkEvent.deviceId() == "mobile-app"

        and: "event has correct payload"
        def payload = walkEvent.payload()
        payload.sessionId() == "walk-session-123"
        payload.start() == Instant.parse("2025-11-21T09:00:00Z")
        payload.end() == Instant.parse("2025-11-21T10:00:00Z")
        payload.durationMinutes() == 60
        payload.totalSteps() == 6500
        payload.totalDistanceMeters() == 5200L
        payload.totalCalories() == 280
        payload.avgHeartRate() == 95
        payload.maxHeartRate() == 125
        payload.originPackage() == "com.heytap.health.international"
    }

    def "Scenario 3: Duplicate walking session event returns duplicate status"() {
        given: "a valid walking session event request with explicit idempotency key"
        def event = """
        {
            "idempotencyKey": "walk-test-2025-11-21-1",
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-dup",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 60,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        and: "I submit the walking session event first time"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I submit the same walking session event again"
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

    def "Scenario 4: Walking session event with missing sessionId returns validation error"() {
        given: "a walking session event without sessionId"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 60,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
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

    def "Scenario 5: Walking session event with missing start returns validation error"() {
        given: "a walking session event without start"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-no-start",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 60,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
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

    def "Scenario 6: Walking session event with missing end returns validation error"() {
        given: "a walking session event without end"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-no-end",
                "start": "2025-11-21T09:00:00Z",
                "durationMinutes": 60,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
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

    def "Scenario 7: Walking session event with missing durationMinutes returns validation error"() {
        given: "a walking session event without durationMinutes"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-no-duration",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
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

    def "Scenario 8: Walking session event with missing originPackage returns validation error"() {
        given: "a walking session event without originPackage"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-no-origin",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 60
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
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

    def "Scenario 9: Walking session event with negative optional totalSteps returns validation error"() {
        given: "a walking session event with negative totalSteps"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-neg-steps",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 60,
                "totalSteps": -100,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
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

    def "Scenario 10: Walking session event with negative optional totalDistanceMeters returns validation error"() {
        given: "a walking session event with negative totalDistanceMeters"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-neg-distance",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 60,
                "totalDistanceMeters": -500.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
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

    def "Scenario 11: Walking session event with zero optional fields is valid (boundary case)"() {
        given: "a walking session event with zero optional fields"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-zero-values",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 0,
                "totalSteps": 0,
                "totalDistanceMeters": 0,
                "totalCalories": 0,
                "avgHeartRate": 0,
                "maxHeartRate": 0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored"
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
        def walkEvent = dbEvents.first()
        walkEvent.payload().durationMinutes() == 0
        walkEvent.payload().totalSteps() == 0
    }

    def "Scenario 12: Walking session event with only required fields is valid"() {
        given: "a walking session event with only required fields"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-minimal",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 45,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "stored"

        def dbEvents = findAllEvents()
        dbEvents.size() == 1
    }

    def "Scenario 13: Walking session event occurredAt timestamp is preserved"() {
        given: "a walking session event with specific timestamp"
        def occurredAt = "2025-11-21T10:05:30Z"
        def event = """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "sessionId": "walk-session-timestamp",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 60,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the walking session event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches the submitted timestamp"
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
        def walkEvent = dbEvents.first()
        def storedOccurredAt = Instant.parse(walkEvent.occurredAt().toString())
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

    String createWalkingSessionEvent() {
        return """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-basic",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 60,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """.stripIndent().trim()
    }

    String createWalkingSessionEventWithAllFields() {
        return """
        {
            "type": "WalkingSessionRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "sessionId": "walk-session-123",
                "name": "Morning Walk",
                "start": "2025-11-21T09:00:00Z",
                "end": "2025-11-21T10:00:00Z",
                "durationMinutes": 60,
                "totalSteps": 6500,
                "totalDistanceMeters": 5200.0,
                "totalCalories": 280.0,
                "avgHeartRate": 95,
                "maxHeartRate": 125,
                "originPackage": "com.heytap.health.international"
            }
        }
        """.stripIndent().trim()
    }
}
