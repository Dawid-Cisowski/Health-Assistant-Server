package com.healthassistant

import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Active Calories Burned event validation via generic health events endpoint
 */
@Title("Feature: Active Calories Burned Event Validation via Health Events API")
class ActiveCaloriesEventSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "Scenario 1: Submit valid active calories event returns success"() {
        given: "a valid active calories event request"
        def request = createHealthEventsRequest(createActiveCaloriesEvent())

        when: "I submit the active calories event"
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

    def "Scenario 2: Active calories event is stored in database with correct type and payload"() {
        given: "a valid active calories event request"
        def request = createHealthEventsRequest(createActiveCaloriesEvent(350.5))

        when: "I submit the active calories event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = eventRepository.findAll()
        events.size() == 1

        and: "event has correct type"
        def caloriesEvent = events.first()
        caloriesEvent.eventType == "ActiveCaloriesBurnedRecorded.v1"

        and: "event has correct device ID"
        caloriesEvent.deviceId == "mobile-app"

        and: "event has correct payload"
        def payload = caloriesEvent.payload
        payload.get("bucketStart") == "2025-11-21T10:00:00Z"
        payload.get("bucketEnd") == "2025-11-21T11:00:00Z"
        payload.get("energyKcal") == 350.5
        payload.get("originPackage") == "com.google.android.apps.fitness"
    }

    def "Scenario 3: Active calories event with missing bucketStart returns validation error"() {
        given: "an active calories event without bucketStart"
        def event = """
        {
            "type": "ActiveCaloriesBurnedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketEnd": "2025-11-21T11:00:00Z",
                "energyKcal": 200.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active calories event"
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

    def "Scenario 4: Active calories event with missing bucketEnd returns validation error"() {
        given: "an active calories event without bucketEnd"
        def event = """
        {
            "type": "ActiveCaloriesBurnedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "energyKcal": 200.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active calories event"
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

    def "Scenario 5: Active calories event with missing energyKcal returns validation error"() {
        given: "an active calories event without energyKcal"
        def event = """
        {
            "type": "ActiveCaloriesBurnedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active calories event"
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

    def "Scenario 6: Active calories event with missing originPackage returns validation error"() {
        given: "an active calories event without originPackage"
        def event = """
        {
            "type": "ActiveCaloriesBurnedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "energyKcal": 200.0
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active calories event"
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

    def "Scenario 7: Active calories event with negative energyKcal returns validation error"() {
        given: "an active calories event with negative energyKcal"
        def event = """
        {
            "type": "ActiveCaloriesBurnedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "energyKcal": -100.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active calories event"
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

    def "Scenario 8: Active calories event with zero energyKcal is valid (boundary case)"() {
        given: "an active calories event with zero energyKcal"
        def event = """
        {
            "type": "ActiveCaloriesBurnedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "energyKcal": 0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active calories event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored"
        def dbEvents = eventRepository.findAll()
        dbEvents.size() == 1
        def caloriesEvent = dbEvents.first()
        caloriesEvent.payload.get("energyKcal") == 0
    }

    def "Scenario 9: Active calories event occurredAt timestamp is preserved"() {
        given: "an active calories event with specific timestamp"
        def occurredAt = "2025-11-21T11:30:45Z"
        def event = """
        {
            "type": "ActiveCaloriesBurnedRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "energyKcal": 450.0,
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the active calories event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches the submitted timestamp"
        def dbEvents = eventRepository.findAll()
        dbEvents.size() == 1
        def caloriesEvent = dbEvents.first()
        def storedOccurredAt = Instant.parse(caloriesEvent.occurredAt.toString())
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

    String createActiveCaloriesEvent(double energyKcal = 250.0) {
        return """
        {
            "type": "ActiveCaloriesBurnedRecorded.v1",
            "occurredAt": "2025-11-21T11:00:00Z",
            "payload": {
                "bucketStart": "2025-11-21T10:00:00Z",
                "bucketEnd": "2025-11-21T11:00:00Z",
                "energyKcal": ${energyKcal},
                "originPackage": "com.google.android.apps.fitness"
            }
        }
        """.stripIndent().trim()
    }
}
