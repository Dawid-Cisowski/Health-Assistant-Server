package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Body Measurements Projections and Query API")
class BodyMeasurementProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-body-meas"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupProjectionsForDateRange(DEVICE_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31))
    }

    def "Scenario 1: Body measurement event creates projection"() {
        given: "a body measurement event"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|2025-01-15T08:00:00Z",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:30:00Z",
                "payload": {
                    "measurementId": "body-import-2025-01-15-abc123",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 38.5,
                    "bicepsRightCm": 39.0,
                    "chestCm": 102.0,
                    "waistCm": 82.0
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the body measurement event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projection is created (verified via API)"
        def response = waitForApiResponse("/v1/body-measurements/latest", DEVICE_ID, SECRET_BASE64)
        response.get("measurement.bicepsLeftCm") == 38.5f
        response.get("measurement.bicepsRightCm") == 39.0f
        response.get("measurement.chestCm") == 102.0f
        response.get("measurement.waistCm") == 82.0f
    }

    def "Scenario 2: Query latest body measurement returns most recent measurement with trends"() {
        given: "multiple body measurements on different days"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|body|trend-old-${uuid}",
                    "type": "BodyMeasurementRecorded.v1",
                    "occurredAt": "2025-01-05T08:00:00Z",
                    "payload": {
                        "measurementId": "body-import-2025-01-05-trend-${uuid}",
                        "measuredAt": "2025-01-05T08:00:00Z",
                        "bicepsLeftCm": 37.0,
                        "bicepsRightCm": 37.5,
                        "waistCm": 84.0
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|body|trend-new-${uuid}",
                    "type": "BodyMeasurementRecorded.v1",
                    "occurredAt": "2025-01-12T08:00:00Z",
                    "payload": {
                        "measurementId": "body-import-2025-01-12-trend-${uuid}",
                        "measuredAt": "2025-01-12T08:00:00Z",
                        "bicepsLeftCm": 38.5,
                        "bicepsRightCm": 39.0,
                        "waistCm": 82.0
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit both events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "latest returns most recent measurement"
        def response = waitForApiResponse("/v1/body-measurements/latest", DEVICE_ID, SECRET_BASE64)
        response.get("measurement.bicepsLeftCm") == 38.5f
        response.get("measurement.date") == "2025-01-12"

        and: "trends show change vs previous"
        response.get("trends.bicepsLeftChangeVsPrevious") == 1.5f
        response.get("trends.waistChangeVsPrevious") == -2.0f
    }

    def "Scenario 3: Query body measurement range returns measurements in date range"() {
        given: "multiple body measurements"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|body|range-05-${uuid}",
                    "type": "BodyMeasurementRecorded.v1",
                    "occurredAt": "2025-01-05T08:00:00Z",
                    "payload": {
                        "measurementId": "body-import-2025-01-05-${uuid}",
                        "measuredAt": "2025-01-05T08:00:00Z",
                        "bicepsLeftCm": 37.0,
                        "chestCm": 100.0
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|body|range-08-${uuid}",
                    "type": "BodyMeasurementRecorded.v1",
                    "occurredAt": "2025-01-08T08:00:00Z",
                    "payload": {
                        "measurementId": "body-import-2025-01-08-${uuid}",
                        "measuredAt": "2025-01-08T08:00:00Z",
                        "bicepsLeftCm": 37.5,
                        "chestCm": 101.0
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|body|range-12-${uuid}",
                    "type": "BodyMeasurementRecorded.v1",
                    "occurredAt": "2025-01-12T08:00:00Z",
                    "payload": {
                        "measurementId": "body-import-2025-01-12-${uuid}",
                        "measuredAt": "2025-01-12T08:00:00Z",
                        "bicepsLeftCm": 38.5,
                        "chestCm": 102.0
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query range summary"
        Thread.sleep(500)
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/body-measurements/range?startDate=2025-01-05&endDate=2025-01-12")
                .get("/v1/body-measurements/range?startDate=2025-01-05&endDate=2025-01-12")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response contains measurements from this test (at least 3)"
        response.getInt("measurementCount") >= 3

        and: "days with data calculated"
        response.getInt("daysWithData") >= 3

        and: "measurements are returned"
        response.getList("measurements").size() >= 3
    }

    def "Scenario 4: Query specific measurement by ID"() {
        given: "a body measurement"
        def measurementId = "body-import-2025-01-15-specific"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|specific-test",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "${measurementId}",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 38.5,
                    "bicepsRightCm": 39.0,
                    "chestCm": 102.0,
                    "waistCm": 82.0,
                    "thighLeftCm": 58.0,
                    "thighRightCm": 58.5
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "event is submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query specific measurement"
        def response = waitForApiResponse("/v1/body-measurements/${measurementId}", DEVICE_ID, SECRET_BASE64)

        then: "measurement details are returned"
        response.get("measurementId") == measurementId
        response.get("bicepsLeftCm") == 38.5f
        response.get("chestCm") == 102.0f
        response.get("thighLeftCm") == 58.0f
    }

    def "Scenario 5: API returns 404 for non-existent measurement ID"() {
        when: "I query non-existent measurement"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/body-measurements/non-existent-id")
                .get("/v1/body-measurements/non-existent-id")
                .then()
                .extract()

        then: "404 is returned"
        response.statusCode() == 404
    }

    def "Scenario 6: Idempotent events don't create duplicate projections"() {
        given: "a body measurement event"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def idempotencyKey = "${DEVICE_ID}|body|idem-${uuid}"
        def measurementId = "body-import-2025-01-15-idem-${uuid}"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${idempotencyKey}",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "${measurementId}",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 38.5,
                    "chestCm": 102.0
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "first event is submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        Thread.sleep(500)

        and: "projection is created with original measurements"
        def firstResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/body-measurements/${measurementId}")
                .get("/v1/body-measurements/${measurementId}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
        firstResponse.get("bicepsLeftCm") == 38.5f

        when: "I submit same idempotency key again"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates duplicate"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "duplicate"
    }

    def "Scenario 7: Range query with invalid dates returns 400"() {
        when: "I query range with endDate before startDate"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/body-measurements/range?startDate=2025-01-15&endDate=2025-01-10")
                .get("/v1/body-measurements/range?startDate=2025-01-15&endDate=2025-01-10")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }

    def "Scenario 8: Summary endpoint returns latest values with changes"() {
        given: "multiple body measurements"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|body|summary-old-${uuid}",
                    "type": "BodyMeasurementRecorded.v1",
                    "occurredAt": "2025-01-05T08:00:00Z",
                    "payload": {
                        "measurementId": "body-summary-old-${uuid}",
                        "measuredAt": "2025-01-05T08:00:00Z",
                        "bicepsLeftCm": 37.0,
                        "waistCm": 84.0,
                        "chestCm": 100.0
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|body|summary-new-${uuid}",
                    "type": "BodyMeasurementRecorded.v1",
                    "occurredAt": "2025-01-12T08:00:00Z",
                    "payload": {
                        "measurementId": "body-summary-new-${uuid}",
                        "measuredAt": "2025-01-12T08:00:00Z",
                        "bicepsLeftCm": 38.5,
                        "waistCm": 82.0,
                        "chestCm": 102.0
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query summary"
        def response = waitForApiResponse("/v1/body-measurements/summary", DEVICE_ID, SECRET_BASE64)

        then: "summary contains latest measurements with changes"
        response.get("measurements.bicepsLeft.value") == 38.5f
        response.get("measurements.bicepsLeft.change") == 1.5f
        response.get("measurements.waist.value") == 82.0f
        response.get("measurements.waist.change") == -2.0f
        response.get("measurements.chest.value") == 102.0f
        response.get("measurements.chest.change") == 2.0f
    }

    def "Scenario 9: Body part history endpoint returns data points for charting"() {
        given: "multiple body measurements"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|body|history-01-${uuid}",
                    "type": "BodyMeasurementRecorded.v1",
                    "occurredAt": "2025-01-01T08:00:00Z",
                    "payload": {
                        "measurementId": "body-history-01-${uuid}",
                        "measuredAt": "2025-01-01T08:00:00Z",
                        "bicepsLeftCm": 36.0
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|body|history-15-${uuid}",
                    "type": "BodyMeasurementRecorded.v1",
                    "occurredAt": "2025-01-15T08:00:00Z",
                    "payload": {
                        "measurementId": "body-history-15-${uuid}",
                        "measuredAt": "2025-01-15T08:00:00Z",
                        "bicepsLeftCm": 38.5
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query biceps-left history"
        Thread.sleep(500)
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/body-measurements/history/biceps-left?from=2025-01-01&to=2025-01-31")
                .get("/v1/body-measurements/history/biceps-left?from=2025-01-01&to=2025-01-31")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "history contains data points"
        response.get("bodyPart") == "biceps-left"
        response.get("unit") == "cm"
        response.getList("dataPoints").size() >= 2

        and: "statistics are calculated"
        response.get("statistics.min") == 36.0f
        response.get("statistics.max") == 38.5f
        response.get("statistics.change") == 2.5f
    }

    def "Scenario 10: All 14 body parts are projected correctly"() {
        given: "a body measurement event with all measurements"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|body|full-metrics-test",
                "type": "BodyMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "body-import-2025-01-15-full",
                    "measuredAt": "2025-01-15T08:00:00Z",
                    "bicepsLeftCm": 38.5,
                    "bicepsRightCm": 39.0,
                    "forearmLeftCm": 30.0,
                    "forearmRightCm": 30.5,
                    "chestCm": 102.0,
                    "waistCm": 82.0,
                    "abdomenCm": 85.0,
                    "hipsCm": 98.0,
                    "neckCm": 40.0,
                    "shouldersCm": 120.0,
                    "thighLeftCm": 58.0,
                    "thighRightCm": 58.5,
                    "calfLeftCm": 38.0,
                    "calfRightCm": 38.5,
                    "notes": "Morning measurement"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "all metrics are projected"
        def response = waitForApiResponse("/v1/body-measurements/body-import-2025-01-15-full", DEVICE_ID, SECRET_BASE64)
        response.get("bicepsLeftCm") == 38.5f
        response.get("bicepsRightCm") == 39.0f
        response.get("forearmLeftCm") == 30.0f
        response.get("forearmRightCm") == 30.5f
        response.get("chestCm") == 102.0f
        response.get("waistCm") == 82.0f
        response.get("abdomenCm") == 85.0f
        response.get("hipsCm") == 98.0f
        response.get("neckCm") == 40.0f
        response.get("shouldersCm") == 120.0f
        response.get("thighLeftCm") == 58.0f
        response.get("thighRightCm") == 58.5f
        response.get("calfLeftCm") == 38.0f
        response.get("calfRightCm") == 38.5f
        response.get("notes") == "Morning measurement"
    }

    def "Scenario 11: Empty range returns zero measurements"() {
        when: "I query range with no data"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/body-measurements/range?startDate=2024-01-01&endDate=2024-01-31")
                .get("/v1/body-measurements/range?startDate=2024-01-01&endDate=2024-01-31")
                .then()
                .extract()

        then: "response shows zero measurements"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getInt("measurementCount") == 0
        body.getInt("daysWithData") == 0
    }

    def "Scenario 12: Invalid body part returns 400"() {
        when: "I query history for invalid body part"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/body-measurements/history/invalid-part?from=2025-01-01&to=2025-01-31")
                .get("/v1/body-measurements/history/invalid-part?from=2025-01-01&to=2025-01-31")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }
}
