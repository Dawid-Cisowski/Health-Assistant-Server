package com.healthassistant

import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Distance event ingestion via generic health events endpoint
 */
@Title("Feature: Distance Event Ingestion via Health Events API")
class DistanceEventSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "Scenario 1: Submit valid distance event returns success"() {
        given: "a valid distance event request"
        def request = createHealthEventsRequest(createDistanceEvent())

        when: "I submit the distance event"
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

    def "Scenario 2: Distance event is stored in database with correct type and payload"() {
        given: "a valid distance event request"
        def request = createHealthEventsRequest(createDistanceEvent(1500.5))

        when: "I submit the distance event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = findAllEvents()
        events.size() == 1

        and: "event has correct type"
        def distanceEvent = events.first()
        distanceEvent.eventType() == "DistanceBucketRecorded.v1"

        and: "event has correct device ID"
        distanceEvent.deviceId() == "mobile-app"

        and: "event has correct payload"
        def payload = distanceEvent.payload()
        payload.get("bucketStart") == "2025-11-21T10:00:00Z"
        payload.get("bucketEnd") == "2025-11-21T11:00:00Z"
        payload.get("distanceMeters") == 1500.5
        payload.get("originPackage") == "com.google.android.apps.fitness"
    }

    def "Scenario 3: Duplicate distance event returns duplicate status"() {
        given: "a valid distance event request with explicit idempotency key"
        def event = """
        {
            "idempotencyKey": "distance-test-2025-11-21-1",
            "type": "DistanceBucketRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "distanceMeters": 1200.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        and: "I submit the distance event first time"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I submit the same distance event again"
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

    def "Scenario 4: Distance event with missing bucketStart returns validation error"() {
        given: "a distance event without bucketStart"
        def event = """
        {
            "type": "DistanceBucketRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketEnd": "2025-11-21T11:00:00Z",
                "distanceMeters": 1000.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the distance event"
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

    def "Scenario 5: Distance event with missing bucketEnd returns validation error"() {
        given: "a distance event without bucketEnd"
        def event = """
        {
            "type": "DistanceBucketRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "distanceMeters": 1000.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the distance event"
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

    def "Scenario 6: Distance event with missing distanceMeters returns validation error"() {
        given: "a distance event without distanceMeters"
        def event = """
        {
            "type": "DistanceBucketRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the distance event"
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

    def "Scenario 7: Distance event with missing originPackage returns validation error"() {
        given: "a distance event without originPackage"
        def event = """
        {
            "type": "DistanceBucketRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "distanceMeters": 1000.0
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the distance event"
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

    def "Scenario 8: Distance event with negative distanceMeters returns validation error"() {
        given: "a distance event with negative distanceMeters"
        def event = """
        {
            "type": "DistanceBucketRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "distanceMeters": -500.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the distance event"
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

    def "Scenario 9: Distance event with zero distanceMeters is valid"() {
        given: "a distance event with zero distanceMeters"
        def event = """
        {
            "type": "DistanceBucketRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "distanceMeters": 0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the distance event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored"
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
        def distanceEvent = dbEvents.first()
        distanceEvent.payload().get("distanceMeters") == 0
    }

    def "Scenario 10: Multiple distance events are stored correctly"() {
        given: "multiple valid distance events"
        def event1 = createDistanceEvent(1000.0, "2025-11-21T09:00:00Z", "2025-11-21T10:00:00Z")
        def event2 = createDistanceEvent(1500.0, "2025-11-21T10:00:00Z", "2025-11-21T11:00:00Z")
        def event3 = createDistanceEvent(800.0, "2025-11-21T11:00:00Z", "2025-11-21T12:00:00Z")

        def request = """
        {
            "events": [${event1}, ${event2}, ${event3}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit all distance events"
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
        def dbEvents = findAllEvents()
        dbEvents.size() == 3
        dbEvents.every { it.eventType() == "DistanceBucketRecorded.v1" }
        dbEvents.every { it.deviceId() == "mobile-app" }
    }

    def "Scenario 11: Distance event occurredAt timestamp is preserved"() {
        given: "a distance event with specific timestamp"
        def occurredAt = "2025-11-21T11:30:45Z"
        def event = """
        {
            "type": "DistanceBucketRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "distanceMeters": 2500.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the distance event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches the submitted timestamp"
        def dbEvents = findAllEvents()
        dbEvents.size() == 1
        def distanceEvent = dbEvents.first()
        def storedOccurredAt = Instant.parse(distanceEvent.occurredAt().toString())
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

    String createDistanceEvent(double distanceMeters = 1000.0, String bucketStart = "2025-11-21T10:00:00Z", String bucketEnd = "2025-11-21T11:00:00Z") {
        return """
        {
            "type": "DistanceBucketRecorded.v1",
            "occurredAt": "${bucketEnd}",
            "payload": {
                "bucketStart": "${bucketStart}",
                "bucketEnd": "${bucketEnd}",
                "distanceMeters": ${distanceMeters},
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """.stripIndent().trim()
    }
}
