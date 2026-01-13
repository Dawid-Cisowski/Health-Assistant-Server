package com.healthassistant

import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Weight Measurement event validation via generic health events endpoint
 */
@Title("Feature: Weight Measurement Event Validation via Health Events API")
class WeightEventValidationSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-weight-valid"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
    }

    def "Scenario 1: Submit valid weight measurement event returns success"() {
        given: "a valid weight measurement event request"
        def request = createHealthEventsRequest(createWeightMeasurementEvent())

        when: "I submit the weight measurement event"
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

    def "Scenario 2: Weight measurement event is stored in database with correct type and payload"() {
        given: "a valid weight measurement event request"
        def request = createHealthEventsRequest(createWeightMeasurementEvent())

        when: "I submit the weight measurement event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1

        and: "event has correct type"
        def weightEvent = events.first()
        weightEvent.eventType() == "WeightMeasurementRecorded.v1"

        and: "event has correct device ID"
        weightEvent.deviceId() == DEVICE_ID

        and: "event has correct payload"
        def payload = weightEvent.payload()
        payload.measurementId() == "weight-test-001"
        payload.weightKg() == new BigDecimal("72.6")
        payload.bmi() == new BigDecimal("23.4")
        payload.bodyFatPercent() == new BigDecimal("21.0")
        payload.score() == 92
    }

    def "Scenario 3: Weight measurement event with missing measurementId returns validation error"() {
        given: "a weight measurement event without measurementId"
        def event = """
        {
            "type": "WeightMeasurementRecorded.v1",
            "occurredAt": "2025-01-12T08:00:00Z",
            "payload": {
                "measuredAt": "2025-01-12T07:30:00Z",
                "weightKg": 72.6,
                "source": "SCALE_SCREENSHOT"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the weight measurement event"
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

    def "Scenario 4: Weight measurement event with missing weightKg returns validation error"() {
        given: "a weight measurement event without weightKg"
        def event = """
        {
            "type": "WeightMeasurementRecorded.v1",
            "occurredAt": "2025-01-12T08:00:00Z",
            "payload": {
                "measurementId": "weight-no-weight",
                "measuredAt": "2025-01-12T07:30:00Z",
                "bmi": 23.4,
                "source": "SCALE_SCREENSHOT"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the weight measurement event"
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

    def "Scenario 5: Weight measurement event with negative weight returns validation error"() {
        given: "a weight measurement event with negative weight"
        def event = """
        {
            "type": "WeightMeasurementRecorded.v1",
            "occurredAt": "2025-01-12T08:00:00Z",
            "payload": {
                "measurementId": "weight-negative",
                "measuredAt": "2025-01-12T07:30:00Z",
                "weightKg": -72.6,
                "source": "SCALE_SCREENSHOT"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the weight measurement event"
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
        events[0].error.message.toString().toLowerCase().contains("weight")
    }

    def "Scenario 6: Weight measurement event with weight exceeding max returns validation error"() {
        given: "a weight measurement event with weight exceeding 700kg"
        def event = """
        {
            "type": "WeightMeasurementRecorded.v1",
            "occurredAt": "2025-01-12T08:00:00Z",
            "payload": {
                "measurementId": "weight-too-heavy",
                "measuredAt": "2025-01-12T07:30:00Z",
                "weightKg": 750.0,
                "source": "SCALE_SCREENSHOT"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the weight measurement event"
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

    def "Scenario 7: Weight measurement event with invalid BMI returns validation error"() {
        given: "a weight measurement event with BMI < 1 (min allowed)"
        def event = """
        {
            "type": "WeightMeasurementRecorded.v1",
            "occurredAt": "2025-01-12T08:00:00Z",
            "payload": {
                "measurementId": "weight-invalid-bmi",
                "measuredAt": "2025-01-12T07:30:00Z",
                "weightKg": 72.6,
                "bmi": 0.5,
                "source": "SCALE_SCREENSHOT"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the weight measurement event"
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

    def "Scenario 8: Weight measurement event with invalid body fat percent returns validation error"() {
        given: "a weight measurement event with body fat > 100%"
        def event = """
        {
            "type": "WeightMeasurementRecorded.v1",
            "occurredAt": "2025-01-12T08:00:00Z",
            "payload": {
                "measurementId": "weight-invalid-bodyfat",
                "measuredAt": "2025-01-12T07:30:00Z",
                "weightKg": 72.6,
                "bodyFatPercent": 120.0,
                "source": "SCALE_SCREENSHOT"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the weight measurement event"
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

    def "Scenario 9: Weight measurement event with all 19 metrics is stored correctly"() {
        given: "a weight measurement event with all metrics"
        def event = """
        {
            "type": "WeightMeasurementRecorded.v1",
            "occurredAt": "2025-01-12T08:00:00Z",
            "payload": {
                "measurementId": "weight-full-metrics",
                "measuredAt": "2025-01-12T07:30:00Z",
                "score": 92,
                "weightKg": 72.6,
                "bmi": 23.4,
                "bodyFatPercent": 21.0,
                "musclePercent": 52.1,
                "hydrationPercent": 57.8,
                "boneMassKg": 2.9,
                "bmrKcal": 1555,
                "visceralFatLevel": 8,
                "subcutaneousFatPercent": 18.5,
                "proteinPercent": 17.2,
                "metabolicAge": 31,
                "idealWeightKg": 68.0,
                "weightControlKg": 4.6,
                "fatMassKg": 15.2,
                "leanBodyMassKg": 57.4,
                "muscleMassKg": 37.8,
                "proteinMassKg": 12.5,
                "bodyType": "Standard",
                "source": "SCALE_SCREENSHOT"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the weight measurement event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored with all metrics"
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1
        def payload = events.first().payload()
        payload.score() == 92
        payload.weightKg() == new BigDecimal("72.6")
        payload.bmi() == new BigDecimal("23.4")
        payload.bodyFatPercent() == new BigDecimal("21.0")
        payload.musclePercent() == new BigDecimal("52.1")
        payload.hydrationPercent() == new BigDecimal("57.8")
        payload.boneMassKg() == new BigDecimal("2.9")
        payload.bmrKcal() == 1555
        payload.visceralFatLevel() == 8
        payload.subcutaneousFatPercent() == new BigDecimal("18.5")
        payload.proteinPercent() == new BigDecimal("17.2")
        payload.metabolicAge() == 31
        payload.idealWeightKg() == new BigDecimal("68.0")
        payload.weightControlKg() == new BigDecimal("4.6")
        payload.fatMassKg() == new BigDecimal("15.2")
        payload.leanBodyMassKg() == new BigDecimal("57.4")
        payload.muscleMassKg() == new BigDecimal("37.8")
        payload.proteinMassKg() == new BigDecimal("12.5")
        payload.bodyType() == "Standard"
    }

    def "Scenario 10: Weight measurement event occurredAt timestamp is preserved"() {
        given: "a weight measurement event with specific timestamp"
        def occurredAt = "2025-01-12T08:15:30Z"
        def event = """
        {
            "type": "WeightMeasurementRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "measurementId": "weight-timestamp-test",
                "measuredAt": "2025-01-12T07:30:00Z",
                "weightKg": 72.6,
                "source": "SCALE_SCREENSHOT"
            }
        }
        """
        def request = createHealthEventsRequest(event)

        when: "I submit the weight measurement event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches the submitted timestamp"
        def dbEvents = findEventsForDevice(DEVICE_ID)
        dbEvents.size() == 1
        def weightEvent = dbEvents.first()
        def storedOccurredAt = Instant.parse(weightEvent.occurredAt().toString())
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

    String createWeightMeasurementEvent() {
        return """
        {
            "type": "WeightMeasurementRecorded.v1",
            "occurredAt": "2025-01-12T08:00:00Z",
            "payload": {
                "measurementId": "weight-test-001",
                "measuredAt": "2025-01-12T07:30:00Z",
                "score": 92,
                "weightKg": 72.6,
                "bmi": 23.4,
                "bodyFatPercent": 21.0,
                "musclePercent": 52.1,
                "hydrationPercent": 57.8,
                "boneMassKg": 2.9,
                "bmrKcal": 1555,
                "visceralFatLevel": 8,
                "metabolicAge": 31,
                "source": "SCALE_SCREENSHOT"
            }
        }
        """.stripIndent().trim()
    }
}
