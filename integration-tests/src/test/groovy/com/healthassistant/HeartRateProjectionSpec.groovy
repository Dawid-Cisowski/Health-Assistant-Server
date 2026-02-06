package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Heart Rate Projections and Query API
 */
@Title("Feature: Heart Rate Projections and Query API")
class HeartRateProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-hr-proj"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        cleanupProjectionsForDateRange(DEVICE_ID, LocalDate.of(2025, 11, 1), LocalDate.of(2025, 12, 31))
    }

    def "Scenario 1: HeartRateSummaryRecorded event creates HR projection"() {
        given: "a heart rate summary event"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|hr|2025-11-20T09:00:00Z",
                "type": "HeartRateSummaryRecorded.v1",
                "occurredAt": "2025-11-20T09:15:00Z",
                "payload": {
                    "bucketStart": "2025-11-20T09:00:00Z",
                    "bucketEnd": "2025-11-20T09:15:00Z",
                    "avg": 72.0,
                    "min": 58,
                    "max": 95,
                    "samples": 30,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the HR event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projection is created with correct aggregates"
        def response = waitForApiResponse("/v1/heart-rate/range?startDate=2025-11-20&endDate=2025-11-20", DEVICE_ID, SECRET_BASE64)
        response.getInt("totalDataPoints") == 1
        response.getInt("avgBpm") == 72
        response.getInt("minBpm") == 58
        response.getInt("maxBpm") == 95

        and: "data points contain the measurement"
        response.getList("dataPoints").size() == 1
        response.getList("dataPoints")[0].avgBpm == 72
        response.getList("dataPoints")[0].minBpm == 58
        response.getList("dataPoints")[0].maxBpm == 95
        response.getList("dataPoints")[0].samples == 30
    }

    def "Scenario 2: Multiple HR events use weighted average by samples"() {
        given: "two HR events with different sample counts"
        // Weighted avg = (70*10 + 80*30) / (10+30) = (700+2400)/40 = 3100/40 = 77
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|hr|2025-11-21T08:00:00Z",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-21T08:15:00Z",
                    "payload": {
                        "bucketStart": "2025-11-21T08:00:00Z",
                        "bucketEnd": "2025-11-21T08:15:00Z",
                        "avg": 70.0,
                        "min": 55,
                        "max": 85,
                        "samples": 10,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|hr|2025-11-21T09:00:00Z",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-21T09:15:00Z",
                    "payload": {
                        "bucketStart": "2025-11-21T09:00:00Z",
                        "bucketEnd": "2025-11-21T09:15:00Z",
                        "avg": 80.0,
                        "min": 65,
                        "max": 110,
                        "samples": 30,
                        "originPackage": "com.google.android.apps.fitness"
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

        then: "weighted average is calculated correctly"
        def response = waitForApiResponse("/v1/heart-rate/range?startDate=2025-11-21&endDate=2025-11-21", DEVICE_ID, SECRET_BASE64)
        response.getInt("totalDataPoints") == 2
        // (70*10 + 80*30) / 40 = 77
        response.getInt("avgBpm") == 77
    }

    def "Scenario 3: Min/max BPM aggregated across all data points"() {
        given: "two HR events with different min/max"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|hr|2025-11-22T10:00:00Z",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-22T10:15:00Z",
                    "payload": {
                        "bucketStart": "2025-11-22T10:00:00Z",
                        "bucketEnd": "2025-11-22T10:15:00Z",
                        "avg": 75.0,
                        "min": 60,
                        "max": 90,
                        "samples": 20,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|hr|2025-11-22T11:00:00Z",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-22T11:15:00Z",
                    "payload": {
                        "bucketStart": "2025-11-22T11:00:00Z",
                        "bucketEnd": "2025-11-22T11:15:00Z",
                        "avg": 85.0,
                        "min": 52,
                        "max": 130,
                        "samples": 20,
                        "originPackage": "com.google.android.apps.fitness"
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

        then: "global min/max are used"
        def response = waitForApiResponse("/v1/heart-rate/range?startDate=2025-11-22&endDate=2025-11-22", DEVICE_ID, SECRET_BASE64)
        response.getInt("minBpm") == 52
        response.getInt("maxBpm") == 130
    }

    def "Scenario 4: RestingHeartRateRecorded event creates resting HR projection"() {
        given: "a resting HR event"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|rhr|2025-11-20",
                "type": "RestingHeartRateRecorded.v1",
                "occurredAt": "2025-11-20T07:00:00Z",
                "payload": {
                    "measuredAt": "2025-11-20T07:00:00Z",
                    "restingBpm": 58,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the resting HR event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "resting HR projection is created"
        def response = waitForApiResponse("/v1/heart-rate/resting/range?startDate=2025-11-20&endDate=2025-11-20", DEVICE_ID, SECRET_BASE64)
        response.getList("").size() == 1
        response.getList("")[0].restingBpm == 58
        response.getList("")[0].date == "2025-11-20"
    }

    def "Scenario 5: Resting HR same day updates existing projection"() {
        given: "two resting HR events for the same day"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|rhr|2025-11-23-morning",
                "type": "RestingHeartRateRecorded.v1",
                "occurredAt": "2025-11-23T06:00:00Z",
                "payload": {
                    "measuredAt": "2025-11-23T06:00:00Z",
                    "restingBpm": 62,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        def request2 = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|rhr|2025-11-23-evening",
                "type": "RestingHeartRateRecorded.v1",
                "occurredAt": "2025-11-23T20:00:00Z",
                "payload": {
                    "measuredAt": "2025-11-23T20:00:00Z",
                    "restingBpm": 55,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit both events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request2)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "only 1 projection exists for the day (updated, not duplicated)"
        waitForEventProcessing {
            def resp = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/heart-rate/resting/range?startDate=2025-11-23&endDate=2025-11-23")
                    .get("/v1/heart-rate/resting/range?startDate=2025-11-23&endDate=2025-11-23")
                    .then()
                    .extract()
            resp.statusCode() == 200 && resp.body().jsonPath().getList("").size() == 1 && resp.body().jsonPath().getList("")[0].restingBpm == 55
        }

        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/heart-rate/resting/range?startDate=2025-11-23&endDate=2025-11-23")
                .get("/v1/heart-rate/resting/range?startDate=2025-11-23&endDate=2025-11-23")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
        response.getList("").size() == 1
        response.getList("")[0].restingBpm == 55
    }

    def "Scenario 6: Resting HR different days create separate projections"() {
        given: "resting HR events on two different days"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|rhr|2025-11-24",
                    "type": "RestingHeartRateRecorded.v1",
                    "occurredAt": "2025-11-24T07:00:00Z",
                    "payload": {
                        "measuredAt": "2025-11-24T07:00:00Z",
                        "restingBpm": 60,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|rhr|2025-11-25",
                    "type": "RestingHeartRateRecorded.v1",
                    "occurredAt": "2025-11-25T07:00:00Z",
                    "payload": {
                        "measuredAt": "2025-11-25T07:00:00Z",
                        "restingBpm": 57,
                        "originPackage": "com.google.android.apps.fitness"
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

        then: "two separate projections are created"
        waitForEventProcessing {
            def resp = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/heart-rate/resting/range?startDate=2025-11-24&endDate=2025-11-25")
                    .get("/v1/heart-rate/resting/range?startDate=2025-11-24&endDate=2025-11-25")
                    .then()
                    .extract()
            resp.statusCode() == 200 && resp.body().jsonPath().getList("").size() == 2
        }

        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/heart-rate/resting/range?startDate=2025-11-24&endDate=2025-11-25")
                .get("/v1/heart-rate/resting/range?startDate=2025-11-24&endDate=2025-11-25")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
        response.getList("").size() == 2
    }

    def "Scenario 7: HR range query filters by date range"() {
        given: "HR events across multiple days"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|hr|2025-11-18T10:00:00Z",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-18T10:15:00Z",
                    "payload": {
                        "bucketStart": "2025-11-18T10:00:00Z",
                        "bucketEnd": "2025-11-18T10:15:00Z",
                        "avg": 70.0,
                        "min": 55,
                        "max": 85,
                        "samples": 15,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|hr|2025-11-19T10:00:00Z",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-19T10:15:00Z",
                    "payload": {
                        "bucketStart": "2025-11-19T10:00:00Z",
                        "bucketEnd": "2025-11-19T10:15:00Z",
                        "avg": 90.0,
                        "min": 70,
                        "max": 120,
                        "samples": 15,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|hr|2025-11-20T10:00:00Z",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-20T10:15:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T10:00:00Z",
                        "bucketEnd": "2025-11-20T10:15:00Z",
                        "avg": 65.0,
                        "min": 50,
                        "max": 80,
                        "samples": 15,
                        "originPackage": "com.google.android.apps.fitness"
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

        when: "I query only 2025-11-19"
        waitForEventProcessing {
            def resp = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/heart-rate/range?startDate=2025-11-18&endDate=2025-11-20")
                    .get("/v1/heart-rate/range?startDate=2025-11-18&endDate=2025-11-20")
                    .then()
                    .extract()
            resp.statusCode() == 200 && resp.body().jsonPath().getInt("totalDataPoints") == 3
        }

        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/heart-rate/range?startDate=2025-11-19&endDate=2025-11-19")
                .get("/v1/heart-rate/range?startDate=2025-11-19&endDate=2025-11-19")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "only Nov 19 data is returned"
        response.getInt("totalDataPoints") == 1
        response.getInt("avgBpm") == 90
        response.getInt("minBpm") == 70
        response.getInt("maxBpm") == 120
    }

    def "Scenario 8: HR range query with invalid dates returns 400"() {
        when: "I query with startDate after endDate"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/heart-rate/range?startDate=2025-11-30&endDate=2025-11-28")
                .get("/v1/heart-rate/range?startDate=2025-11-30&endDate=2025-11-28")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }

    def "Scenario 9: Empty range returns zero data points and null aggregates"() {
        when: "I query a range with no data"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/heart-rate/range?startDate=2025-12-01&endDate=2025-12-01")
                .get("/v1/heart-rate/range?startDate=2025-12-01&endDate=2025-12-01")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response has zero data points and null aggregates"
        response.getInt("totalDataPoints") == 0
        response.get("avgBpm") == null
        response.get("minBpm") == null
        response.get("maxBpm") == null
        response.getList("dataPoints").isEmpty()
    }

    def "Scenario 10: Device isolation - different devices see only their own data"() {
        given: "HR events from two different devices"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|hr|2025-11-26T10:00:00Z",
                "type": "HeartRateSummaryRecorded.v1",
                "occurredAt": "2025-11-26T10:15:00Z",
                "payload": {
                    "bucketStart": "2025-11-26T10:00:00Z",
                    "bucketEnd": "2025-11-26T10:15:00Z",
                    "avg": 72.0,
                    "min": 60,
                    "max": 88,
                    "samples": 20,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        def request2 = """
        {
            "events": [{
                "idempotencyKey": "different-device-id|hr|2025-11-26T10:00:00Z",
                "type": "HeartRateSummaryRecorded.v1",
                "occurredAt": "2025-11-26T10:15:00Z",
                "payload": {
                    "bucketStart": "2025-11-26T10:00:00Z",
                    "bucketEnd": "2025-11-26T10:15:00Z",
                    "avg": 95.0,
                    "min": 80,
                    "max": 140,
                    "samples": 20,
                    "originPackage": "com.google.android.apps.fitness"
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

        then: "device 1 sees only its own data"
        def response1 = waitForApiResponse("/v1/heart-rate/range?startDate=2025-11-26&endDate=2025-11-26", DEVICE_ID, SECRET_BASE64)
        response1.getInt("totalDataPoints") == 1
        response1.getInt("avgBpm") == 72

        and: "device 2 sees only its own data"
        def response2 = waitForApiResponse("/v1/heart-rate/range?startDate=2025-11-26&endDate=2025-11-26", "different-device-id", DIFFERENT_DEVICE_SECRET_BASE64)
        response2.getInt("totalDataPoints") == 1
        response2.getInt("avgBpm") == 95
    }

    def "Scenario 11: Idempotent HR event submission does not create duplicate projections"() {
        given: "an HR event"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|hr|2025-11-27T10:00:00Z",
                "type": "HeartRateSummaryRecorded.v1",
                "occurredAt": "2025-11-27T10:15:00Z",
                "payload": {
                    "bucketStart": "2025-11-27T10:00:00Z",
                    "bucketEnd": "2025-11-27T10:15:00Z",
                    "avg": 74.0,
                    "min": 60,
                    "max": 92,
                    "samples": 25,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the same event twice"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "only 1 data point exists (not 2)"
        def response = waitForApiResponse("/v1/heart-rate/range?startDate=2025-11-27&endDate=2025-11-27", DEVICE_ID, SECRET_BASE64)
        response.getInt("totalDataPoints") == 1
    }

    def "Scenario 12: Resting HR range query with invalid dates returns 400"() {
        when: "I query resting HR with startDate after endDate"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/heart-rate/resting/range?startDate=2025-11-30&endDate=2025-11-28")
                .get("/v1/heart-rate/resting/range?startDate=2025-11-30&endDate=2025-11-28")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }
}
