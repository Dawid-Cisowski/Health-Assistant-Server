package com.healthassistant

import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Calories Projections
 */
@Title("Feature: Calories Projections and Query API")
class CaloriesProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-calories"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        cleanupProjectionsForDateRange(DEVICE_ID, LocalDate.of(2025, 11, 1), LocalDate.of(2025, 12, 31))
    }

    def "Scenario 1: ActiveCaloriesBurned event creates hourly and daily projections"() {
        given: "a calories event for a specific hour"
        def date = "2025-11-20"
        // 10:00 Warsaw time (CET = UTC+1) = 09:00 UTC
        def bucketStart = "2025-11-20T09:00:00Z"
        def bucketEnd = "2025-11-20T09:01:00Z"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|calories|${bucketStart}",
                "type": "ActiveCaloriesBurnedRecorded.v1",
                "occurredAt": "${bucketEnd}",
                "payload": {
                    "bucketStart": "${bucketStart}",
                    "bucketEnd": "${bucketEnd}",
                    "energyKcal": 125.5,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the calories event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections are created (verified via API)"
        def response = waitForApiResponse("/v1/calories/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getList("hourlyBreakdown")[10].calories == 125.5

        and: "daily projection is created with totals"
        response.getDouble("totalCalories") == 125.5
        response.getInt("mostActiveHour") == 10
        response.getDouble("mostActiveHourCalories") == 125.5
        response.getInt("activeHoursCount") == 1
    }

    def "Scenario 2: Multiple buckets same hour accumulate calories"() {
        given: "multiple calorie events in same hour"
        def date = "2025-11-20"
        // 14:00 Warsaw time (CET = UTC+1) = 13:00 UTC
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-20T13:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-20T13:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T13:00:00Z",
                        "bucketEnd": "2025-11-20T13:01:00Z",
                        "energyKcal": 100.0,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-20T13:01:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-20T13:02:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T13:01:00Z",
                        "bucketEnd": "2025-11-20T13:02:00Z",
                        "energyKcal": 75.5,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit multiple events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections accumulate calories (verified via API)"
        def response = waitForApiResponse("/v1/calories/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getList("hourlyBreakdown")[14].calories == 175.5
        response.getDouble("totalCalories") == 175.5
    }

    def "Scenario 3: Calories across multiple hours create separate hourly projections"() {
        given: "calories in different hours"
        def date = "2025-11-20"
        // Warsaw times: 08:00, 12:00, 18:00 (CET = UTC+1) => UTC: 07:00, 11:00, 17:00
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-20T07:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-20T07:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T07:00:00Z",
                        "bucketEnd": "2025-11-20T07:01:00Z",
                        "energyKcal": 50.0,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-20T11:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-20T11:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T11:00:00Z",
                        "bucketEnd": "2025-11-20T11:01:00Z",
                        "energyKcal": 150.0,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-20T17:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-20T17:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T17:00:00Z",
                        "bucketEnd": "2025-11-20T17:01:00Z",
                        "energyKcal": 200.0,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit calories for different hours"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "three hourly projections are created (verified via API)"
        def response = waitForApiResponse("/v1/calories/daily/${date}", DEVICE_ID, SECRET_BASE64)
        def hourlyBreakdown = response.getList("hourlyBreakdown")
        hourlyBreakdown[8].calories == 50.0
        hourlyBreakdown[12].calories == 150.0
        hourlyBreakdown[18].calories == 200.0

        and: "daily projection shows most active hour and total"
        response.getDouble("totalCalories") == 400.0
        response.getInt("mostActiveHour") == 18
        response.getDouble("mostActiveHourCalories") == 200.0
        response.getInt("activeHoursCount") == 3
    }

    def "Scenario 4: Query API returns daily breakdown with 24 hours"() {
        given: "calories in multiple hours"
        def date = "2025-11-21"
        // Warsaw times: 09:00, 15:00 (CET = UTC+1) => UTC: 08:00, 14:00
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-21T08:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-21T08:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-21T08:00:00Z",
                        "bucketEnd": "2025-11-21T08:01:00Z",
                        "energyKcal": 85.5,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-21T14:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-21T14:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-21T14:00:00Z",
                        "bucketEnd": "2025-11-21T14:01:00Z",
                        "energyKcal": 125.0,
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

        when: "I query daily breakdown"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/calories/daily/${date}")
                .get("/v1/calories/daily/${date}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response contains 24 hourly entries"
        response.getList("hourlyBreakdown").size() == 24

        and: "hours with calories show correct values"
        def hourlyBreakdown = response.getList("hourlyBreakdown")
        hourlyBreakdown[9].calories == 85.5  // hour 9
        hourlyBreakdown[15].calories == 125.0 // hour 15

        and: "hours without calories show 0"
        hourlyBreakdown[0].calories == 0.0
        hourlyBreakdown[23].calories == 0.0

        and: "daily totals are correct"
        response.getDouble("totalCalories") == 210.5
        response.getInt("mostActiveHour") == 15
        response.getDouble("mostActiveHourCalories") == 125.0
        response.getInt("activeHoursCount") == 2
    }

    def "Scenario 5: Query API returns range summary with daily stats"() {
        given: "calories across multiple days"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-18T09:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-18T09:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-18T09:00:00Z",
                        "bucketEnd": "2025-11-18T09:01:00Z",
                        "energyKcal": 500.0,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-19T10:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-19T10:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-19T10:00:00Z",
                        "bucketEnd": "2025-11-19T10:01:00Z",
                        "energyKcal": 750.0,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-20T11:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-20T11:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T11:00:00Z",
                        "bucketEnd": "2025-11-20T11:01:00Z",
                        "energyKcal": 1000.0,
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

        when: "I query range summary"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/calories/range?startDate=2025-11-18&endDate=2025-11-20")
                .get("/v1/calories/range?startDate=2025-11-18&endDate=2025-11-20")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response contains 3 days"
        response.getList("dailyStats").size() == 3

        and: "daily stats are correct"
        def dailyStats = response.getList("dailyStats")
        dailyStats[0].date == "2025-11-18"
        dailyStats[0].totalCalories == 500.0
        dailyStats[1].date == "2025-11-19"
        dailyStats[1].totalCalories == 750.0
        dailyStats[2].date == "2025-11-20"
        dailyStats[2].totalCalories == 1000.0

        and: "totals are correct"
        response.getDouble("totalCalories") == 2250.0
        response.getDouble("averageCalories") == 750.0
        response.getInt("daysWithData") == 3
    }

    def "Scenario 6: Zero-calorie buckets are ignored"() {
        given: "a bucket with zero calories"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|calories|2025-11-23T10:00:00Z",
                "type": "ActiveCaloriesBurnedRecorded.v1",
                "occurredAt": "2025-11-23T10:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-23T10:00:00Z",
                    "bucketEnd": "2025-11-23T10:01:00Z",
                    "energyKcal": 0,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit zero-calorie event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "no projections are created (verified via API returning 404)"
        apiReturns404("/v1/calories/daily/2025-11-23", DEVICE_ID, SECRET_BASE64)
    }

    def "Scenario 7: API returns 404 for date with no calories"() {
        given: "no calorie data exists"

        when: "I query daily breakdown"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/calories/daily/2025-12-01")
                .get("/v1/calories/daily/2025-12-01")
                .then()
                .extract()

        then: "404 is returned"
        response.statusCode() == 404
    }

    def "Scenario 8: Range summary includes days with no data as zero"() {
        given: "calories only on first and last day of range"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-24T09:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-24T09:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-24T09:00:00Z",
                        "bucketEnd": "2025-11-24T09:01:00Z",
                        "energyKcal": 500.0,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|calories|2025-11-26T09:00:00Z",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-26T09:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-26T09:00:00Z",
                        "bucketEnd": "2025-11-26T09:01:00Z",
                        "energyKcal": 500.0,
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

        when: "I query 3-day range"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/calories/range?startDate=2025-11-24&endDate=2025-11-26")
                .get("/v1/calories/range?startDate=2025-11-24&endDate=2025-11-26")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "all 3 days are included"
        response.getList("dailyStats").size() == 3

        and: "middle day shows 0 calories"
        def dailyStats = response.getList("dailyStats")
        dailyStats[0].totalCalories == 500.0
        dailyStats[1].totalCalories == 0.0  // 2025-11-25 has no data
        dailyStats[2].totalCalories == 500.0

        and: "daysWithData counts only non-zero days"
        response.getInt("daysWithData") == 2
    }

    def "Scenario 9: Range query with invalid dates returns 400"() {
        when: "I query range with endDate before startDate"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/calories/range?startDate=2025-11-30&endDate=2025-11-28")
                .get("/v1/calories/range?startDate=2025-11-30&endDate=2025-11-28")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }

    def "Scenario 10: Device isolation - different devices have separate projections"() {
        given: "calories from two different devices"
        def date = "2025-11-22"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|calories|2025-11-22T09:00:00Z",
                "type": "ActiveCaloriesBurnedRecorded.v1",
                "occurredAt": "2025-11-22T09:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-22T09:00:00Z",
                    "bucketEnd": "2025-11-22T09:01:00Z",
                    "energyKcal": 100.0,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        def request2 = """
        {
            "events": [{
                "idempotencyKey": "different-device-id|calories|2025-11-22T09:00:00Z",
                "type": "ActiveCaloriesBurnedRecorded.v1",
                "occurredAt": "2025-11-22T09:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-22T09:00:00Z",
                    "bucketEnd": "2025-11-22T09:01:00Z",
                    "energyKcal": 200.0,
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

        then: "each device has its own projection (verified via API)"
        def response1 = waitForApiResponse("/v1/calories/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response1.getDouble("totalCalories") == 100.0

        and: "different device has its own data"
        def response2 = waitForApiResponse("/v1/calories/daily/${date}", "different-device-id", DIFFERENT_DEVICE_SECRET_BASE64)
        response2.getDouble("totalCalories") == 200.0
    }
}
