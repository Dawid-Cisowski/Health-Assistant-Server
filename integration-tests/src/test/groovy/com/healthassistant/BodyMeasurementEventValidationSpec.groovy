package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Body Measurement Event Validation")
class BodyMeasurementEventValidationSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-body-meas-valid"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupProjectionsForDateRange(DEVICE_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31))
    }

    def "Scenario 1: Valid body measurement event is accepted"() {
        given: "a valid body measurement event"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|valid-test",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "body-valid-test",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 38.5,
                    "chestCm": 102.0
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is accepted"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "stored"
    }

    def "Scenario 2: Missing measurementId is rejected"() {
        given: "a body measurement event without measurementId"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|missing-id",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 38.5
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is rejected with validation error"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "invalid"
    }

    def "Scenario 3: Missing measuredAt is rejected"() {
        given: "a body measurement event without measuredAt"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|missing-time",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "body-missing-time",
                    "bicepsLeftCm": 38.5
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is rejected with validation error"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "invalid"
    }

    def "Scenario 4: Event with no measurements is rejected"() {
        given: "a body measurement event with no actual measurements"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|no-measurements",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "body-no-measurements",
                    "measuredAt": "2025-01-15T08:00:00Z"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is rejected with validation error"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "invalid"
    }

    def "Scenario 5: Biceps measurement outside valid range is rejected"() {
        given: "a body measurement with biceps value too small"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|invalid-biceps",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "body-invalid-biceps",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 5.0
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is rejected with validation error"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "invalid"
    }

    def "Scenario 6: Waist measurement outside valid range is rejected"() {
        given: "a body measurement with waist value too large"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|invalid-waist",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "body-invalid-waist",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "waistCm": 350.0
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is rejected with validation error"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "invalid"
    }

    def "Scenario 7: Future measuredAt is rejected"() {
        given: "a body measurement with future timestamp"
        def futureTime = java.time.Instant.now().plusSeconds(600).toString()
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|future-time",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "${futureTime}",
                "payload": {
                    "measurementId": "body-future-time",
                    "measuredAt": "${futureTime}",
                    "bicepsLeftCm": 38.5
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is rejected with validation error"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "invalid"
    }

    def "Scenario 8: Minimum valid values are accepted"() {
        given: "a body measurement with minimum valid values"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|min-values",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "body-min-values",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 10.0,
                    "forearmLeftCm": 10.0,
                    "chestCm": 40.0,
                    "waistCm": 40.0,
                    "neckCm": 20.0,
                    "shouldersCm": 70.0,
                    "thighLeftCm": 20.0,
                    "calfLeftCm": 15.0
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is accepted"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "stored"
    }

    def "Scenario 9: Maximum valid values are accepted"() {
        given: "a body measurement with maximum valid values"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|max-values",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "body-max-values",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 100.0,
                    "forearmLeftCm": 80.0,
                    "chestCm": 300.0,
                    "waistCm": 300.0,
                    "neckCm": 80.0,
                    "shouldersCm": 200.0,
                    "thighLeftCm": 150.0,
                    "calfLeftCm": 80.0
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is accepted"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "stored"
    }

    def "Scenario 10: Notes field with 500 characters is accepted"() {
        given: "a body measurement with maximum notes length"
        def longNotes = "x" * 500
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|long-notes",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "body-long-notes",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 38.5,
                    "notes": "${longNotes}"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "event is accepted"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "stored"
    }
}
