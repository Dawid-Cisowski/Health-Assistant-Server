package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Weight Projections and Query API
 */
@Title("Feature: Weight Projections and Query API")
class WeightProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-weight"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupProjectionsForDateRange(DEVICE_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31))
    }

    def "Scenario 1: Weight measurement event creates projection"() {
        given: "a weight measurement event"
        def date = "2025-01-12"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|weight|2025-01-12T07:30:00Z",
                "type": "WeightMeasurementRecorded.v1",
                "occurredAt": "2025-01-12T08:00:00Z",
                "payload": {
                    "measurementId": "scale-import-2025-01-12-abc123",
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
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the weight event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projection is created (verified via API)"
        def response = waitForApiResponse("/v1/weight/latest", DEVICE_ID, SECRET_BASE64)
        response.get("measurement.weightKg") == 72.6f
        response.get("measurement.bmi") == 23.4f
        response.get("measurement.bodyFatPercent") == 21.0f
        response.get("measurement.musclePercent") == 52.1f
        response.getInt("measurement.score") == 92
        response.getInt("measurement.bmrKcal") == 1555
        response.getInt("measurement.visceralFatLevel") == 8
        response.getInt("measurement.metabolicAge") == 31
    }

    def "Scenario 2: Query latest weight returns most recent measurement with trends"() {
        given: "multiple weight measurements on different days"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|weight|trend-old-${uuid}",
                    "type": "WeightMeasurementRecorded.v1",
                    "occurredAt": "2025-01-05T08:00:00Z",
                    "payload": {
                        "measurementId": "scale-import-2025-01-05-trend-${uuid}",
                        "measuredAt": "2025-01-05T07:30:00Z",
                        "weightKg": 74.0,
                        "bmi": 23.8,
                        "bodyFatPercent": 22.0,
                        "musclePercent": 51.0,
                        "source": "SCALE_SCREENSHOT"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|weight|trend-new-${uuid}",
                    "type": "WeightMeasurementRecorded.v1",
                    "occurredAt": "2025-01-12T08:00:00Z",
                    "payload": {
                        "measurementId": "scale-import-2025-01-12-trend-${uuid}",
                        "measuredAt": "2025-01-12T07:30:00Z",
                        "weightKg": 72.6,
                        "bmi": 23.4,
                        "bodyFatPercent": 21.0,
                        "musclePercent": 52.1,
                        "source": "SCALE_SCREENSHOT"
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
        def response = waitForApiResponse("/v1/weight/latest", DEVICE_ID, SECRET_BASE64)
        response.get("measurement.weightKg") == 72.6f
        response.get("measurement.date") == "2025-01-12"

        and: "trends show weight change vs previous"
        response.get("trends.weightChangeVsPrevious") == -1.4f
    }

    def "Scenario 3: Query weight range returns measurements in date range"() {
        given: "multiple weight measurements"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|weight|range-05-${uuid}",
                    "type": "WeightMeasurementRecorded.v1",
                    "occurredAt": "2025-01-05T08:00:00Z",
                    "payload": {
                        "measurementId": "scale-import-2025-01-05-${uuid}",
                        "measuredAt": "2025-01-05T07:30:00Z",
                        "weightKg": 74.0,
                        "bodyFatPercent": 22.0,
                        "musclePercent": 51.0,
                        "source": "SCALE_SCREENSHOT"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|weight|range-08-${uuid}",
                    "type": "WeightMeasurementRecorded.v1",
                    "occurredAt": "2025-01-08T08:00:00Z",
                    "payload": {
                        "measurementId": "scale-import-2025-01-08-${uuid}",
                        "measuredAt": "2025-01-08T07:30:00Z",
                        "weightKg": 73.2,
                        "bodyFatPercent": 21.5,
                        "musclePercent": 51.5,
                        "source": "SCALE_SCREENSHOT"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|weight|range-12-${uuid}",
                    "type": "WeightMeasurementRecorded.v1",
                    "occurredAt": "2025-01-12T08:00:00Z",
                    "payload": {
                        "measurementId": "scale-import-2025-01-12-${uuid}",
                        "measuredAt": "2025-01-12T07:30:00Z",
                        "weightKg": 72.6,
                        "bodyFatPercent": 21.0,
                        "musclePercent": 52.1,
                        "source": "SCALE_SCREENSHOT"
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
        Thread.sleep(500) // Wait for async projection
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/weight/range?startDate=2025-01-05&endDate=2025-01-12")
                .get("/v1/weight/range?startDate=2025-01-05&endDate=2025-01-12")
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

        and: "average weight is calculated"
        def avgWeight = response.get("averageWeight")
        avgWeight != null
    }

    def "Scenario 4: Query specific measurement by ID"() {
        given: "a weight measurement"
        def measurementId = "scale-import-2025-01-12-specific"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|weight|specific-test",
                "type": "WeightMeasurementRecorded.v1",
                "occurredAt": "2025-01-12T08:00:00Z",
                "payload": {
                    "measurementId": "${measurementId}",
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
        def response = waitForApiResponse("/v1/weight/${measurementId}", DEVICE_ID, SECRET_BASE64)

        then: "measurement details are returned"
        response.get("measurementId") == measurementId
        response.get("weightKg") == 72.6f
        response.get("bmi") == 23.4f
        response.getInt("score") == 92
        response.getInt("bmrKcal") == 1555
    }

    def "Scenario 5: API returns 404 for non-existent measurement ID"() {
        given: "no measurement with this ID exists"

        when: "I query non-existent measurement"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/weight/non-existent-id")
                .get("/v1/weight/non-existent-id")
                .then()
                .extract()

        then: "404 is returned"
        response.statusCode() == 404
    }

    def "Scenario 6: Idempotent events don't create duplicate projections"() {
        given: "a weight measurement event"
        def uuid = UUID.randomUUID().toString().substring(0, 8)
        def idempotencyKey = "${DEVICE_ID}|weight|idem-${uuid}"
        def measurementId = "scale-import-2025-01-15-idem-${uuid}"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${idempotencyKey}",
                "type": "WeightMeasurementRecorded.v1",
                "occurredAt": "2025-01-15T08:00:00Z",
                "payload": {
                    "measurementId": "${measurementId}",
                    "measuredAt": "2025-01-15T07:30:00Z",
                    "weightKg": 72.6,
                    "bodyFatPercent": 21.0,
                    "source": "SCALE_SCREENSHOT"
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

        Thread.sleep(500) // Wait for async projection

        and: "projection is created with original weight"
        def firstResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/weight/${measurementId}")
                .get("/v1/weight/${measurementId}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
        firstResponse.get("weightKg") == 72.6f

        when: "I submit same idempotency key again"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates duplicate"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getList("events")[0].status == "duplicate"

        and: "projection is NOT duplicated - original data preserved"
        def secondResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/weight/${measurementId}")
                .get("/v1/weight/${measurementId}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
        secondResponse.get("weightKg") == 72.6f
    }

    def "Scenario 7: Range query with invalid dates returns 400"() {
        when: "I query range with endDate before startDate"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/weight/range?startDate=2025-01-15&endDate=2025-01-10")
                .get("/v1/weight/range?startDate=2025-01-15&endDate=2025-01-10")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }

    def "Scenario 8: Device isolation - different devices have separate projections"() {
        given: "weight from two different devices"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|weight|device1-2025-01-12",
                "type": "WeightMeasurementRecorded.v1",
                "occurredAt": "2025-01-12T08:00:00Z",
                "payload": {
                    "measurementId": "device1-weight-2025-01-12",
                    "measuredAt": "2025-01-12T07:30:00Z",
                    "weightKg": 72.6,
                    "source": "SCALE_SCREENSHOT"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        def request2 = """
        {
            "events": [{
                "idempotencyKey": "different-device-id|weight|device2-2025-01-12",
                "type": "WeightMeasurementRecorded.v1",
                "occurredAt": "2025-01-12T08:00:00Z",
                "payload": {
                    "measurementId": "device2-weight-2025-01-12",
                    "measuredAt": "2025-01-12T07:30:00Z",
                    "weightKg": 85.0,
                    "source": "SCALE_SCREENSHOT"
                }
            }],
            "deviceId": "different-device-id"
        }
        """

        when: "I submit events from both devices"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
        authenticatedPostRequestWithBody("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, "/v1/health-events", request2)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "each device has its own projection"
        def response1 = waitForApiResponse("/v1/weight/latest", DEVICE_ID, SECRET_BASE64)
        response1.get("measurement.weightKg") == 72.6f

        and: "different device has its own weight data"
        def response2 = waitForApiResponse("/v1/weight/latest", "different-device-id", DIFFERENT_DEVICE_SECRET_BASE64)
        response2.get("measurement.weightKg") == 85.0f
    }

    def "Scenario 9: All 19 metrics are projected correctly"() {
        given: "a weight measurement event with all metrics"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|weight|full-metrics-test",
                "type": "WeightMeasurementRecorded.v1",
                "occurredAt": "2025-01-12T08:00:00Z",
                "payload": {
                    "measurementId": "scale-import-2025-01-12-full",
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
        def response = waitForApiResponse("/v1/weight/scale-import-2025-01-12-full", DEVICE_ID, SECRET_BASE64)
        response.getInt("score") == 92
        response.get("weightKg") == 72.6f
        response.get("bmi") == 23.4f
        response.get("bodyFatPercent") == 21.0f
        response.get("musclePercent") == 52.1f
        response.get("hydrationPercent") == 57.8f
        response.get("boneMassKg") == 2.9f
        response.getInt("bmrKcal") == 1555
        response.getInt("visceralFatLevel") == 8
        response.get("subcutaneousFatPercent") == 18.5f
        response.get("proteinPercent") == 17.2f
        response.getInt("metabolicAge") == 31
        response.get("idealWeightKg") == 68.0f
        response.get("weightControlKg") == 4.6f
        response.get("fatMassKg") == 15.2f
        response.get("leanBodyMassKg") == 57.4f
        response.get("muscleMassKg") == 37.8f
        response.get("proteinMassKg") == 12.5f
        response.get("bodyType") == "Standard"
    }

    def "Scenario 10: Empty range returns zero measurements"() {
        given: "no weight data exists in range"

        when: "I query range with no data"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/weight/range?startDate=2024-01-01&endDate=2024-01-31")
                .get("/v1/weight/range?startDate=2024-01-01&endDate=2024-01-31")
                .then()
                .extract()

        then: "response shows zero measurements"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getInt("measurementCount") == 0
        body.getInt("daysWithData") == 0
    }
}
