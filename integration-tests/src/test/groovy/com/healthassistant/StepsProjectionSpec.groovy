package com.healthassistant

import com.healthassistant.steps.api.StepsFacade
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Steps Projections
 */
@Title("Feature: Steps Projections and Query API")
class StepsProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-steps"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    @Autowired
    StepsFacade stepsFacade

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        stepsFacade.deleteProjectionsByDeviceId(DEVICE_ID)
    }

    def "Scenario 1: StepsBucketed event creates hourly and daily projections"() {
        given: "a steps event for a specific hour"
        def date = "2025-11-20"
        // 10:00 Warsaw time (CET = UTC+1) = 09:00 UTC
        def bucketStart = "2025-11-20T09:00:00Z"
        def bucketEnd = "2025-11-20T09:01:00Z"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|${bucketStart}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "${bucketEnd}",
                "payload": {
                    "bucketStart": "${bucketStart}",
                    "bucketEnd": "${bucketEnd}",
                    "count": 150,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the steps event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections are created (verified via API)"
        def response = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getInt("totalSteps") == 150
        response.getInt("mostActiveHour") == 10
        response.getInt("mostActiveHourSteps") == 150
        response.getInt("activeHoursCount") == 1
        response.getList("hourlyBreakdown")[10].steps == 150
    }

    def "Scenario 2: Multiple buckets same hour accumulate steps"() {
        given: "multiple step events in same hour"
        def date = "2025-11-20"
        // 14:00 Warsaw time (CET = UTC+1) = 13:00 UTC
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-20T13:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-20T13:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T13:00:00Z",
                        "bucketEnd": "2025-11-20T13:01:00Z",
                        "count": 100,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-20T13:01:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-20T13:02:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T13:01:00Z",
                        "bucketEnd": "2025-11-20T13:02:00Z",
                        "count": 120,
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

        then: "projections accumulate steps (verified via API)"
        def response = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getList("hourlyBreakdown")[14].steps == 220
        response.getInt("totalSteps") == 220
    }

    def "Scenario 3: Steps across multiple hours create separate hourly projections"() {
        given: "steps in different hours"
        def date = "2025-11-20"
        // Warsaw times: 08:00, 12:00, 18:00 (CET = UTC+1) => UTC: 07:00, 11:00, 17:00
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-20T07:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-20T07:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T07:00:00Z",
                        "bucketEnd": "2025-11-20T07:01:00Z",
                        "count": 200,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-20T11:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-20T11:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T11:00:00Z",
                        "bucketEnd": "2025-11-20T11:01:00Z",
                        "count": 300,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-20T17:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-20T17:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T17:00:00Z",
                        "bucketEnd": "2025-11-20T17:01:00Z",
                        "count": 400,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit steps for different hours"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections are created (verified via API)"
        def response = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getList("hourlyBreakdown")[8].steps == 200
        response.getList("hourlyBreakdown")[12].steps == 300
        response.getList("hourlyBreakdown")[18].steps == 400
        response.getInt("totalSteps") == 900
        response.getInt("mostActiveHour") == 18
        response.getInt("mostActiveHourSteps") == 400
        response.getInt("activeHoursCount") == 3
    }

    def "Scenario 4: Query API returns daily breakdown with 24 hours"() {
        given: "steps in multiple hours"
        def date = "2025-11-21"
        // Warsaw times: 09:00, 15:00 (CET = UTC+1) => UTC: 08:00, 14:00
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-21T08:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-21T08:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-21T08:00:00Z",
                        "bucketEnd": "2025-11-21T08:01:00Z",
                        "count": 500,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-21T14:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-21T14:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-21T14:00:00Z",
                        "bucketEnd": "2025-11-21T14:01:00Z",
                        "count": 800,
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

        when: "I query daily breakdown (with wait)"
        def response = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)

        then: "response contains 24 hourly entries"
        response.getList("hourlyBreakdown").size() == 24

        and: "hours with steps show correct counts"
        def hourlyBreakdown = response.getList("hourlyBreakdown")
        hourlyBreakdown[9].steps == 500  // hour 9
        hourlyBreakdown[15].steps == 800 // hour 15

        and: "hours without steps show 0"
        hourlyBreakdown[0].steps == 0
        hourlyBreakdown[23].steps == 0

        and: "daily totals are correct"
        response.getInt("totalSteps") == 1300
        response.getInt("mostActiveHour") == 15
        response.getInt("mostActiveHourSteps") == 800
        response.getInt("activeHoursCount") == 2
    }

    def "Scenario 5: Query API returns range summary with daily stats"() {
        given: "steps across multiple days"
        // All times adjusted for Poland timezone (CET = UTC+1)
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-18T09:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-18T09:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-18T09:00:00Z",
                        "bucketEnd": "2025-11-18T09:01:00Z",
                        "count": 1000,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-19T10:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-19T10:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-19T10:00:00Z",
                        "bucketEnd": "2025-11-19T10:01:00Z",
                        "count": 1500,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-20T11:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-20T11:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T11:00:00Z",
                        "bucketEnd": "2025-11-20T11:01:00Z",
                        "count": 2000,
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

        and: "wait for async projections"
        waitForApiResponse("/v1/steps/daily/2025-11-20", DEVICE_ID, SECRET_BASE64)

        when: "I query range summary"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/steps/range?startDate=2025-11-18&endDate=2025-11-20")
                .get("/v1/steps/range?startDate=2025-11-18&endDate=2025-11-20")
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
        dailyStats[0].totalSteps == 1000
        dailyStats[1].date == "2025-11-19"
        dailyStats[1].totalSteps == 1500
        dailyStats[2].date == "2025-11-20"
        dailyStats[2].totalSteps == 2000

        and: "totals are correct"
        response.getInt("totalSteps") == 4500
        response.getInt("averageSteps") == 1500
        response.getInt("daysWithData") == 3
    }

    def "Scenario 6: Zero-step buckets are ignored"() {
        given: "a bucket with zero steps"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|2025-11-23T10:00:00Z",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-23T10:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-23T10:00:00Z",
                    "bucketEnd": "2025-11-23T10:01:00Z",
                    "count": 0,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit zero-step event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "no projections are created (API returns 404)"
        apiReturns404("/v1/steps/daily/2025-11-23", DEVICE_ID, SECRET_BASE64)
    }

    def "Scenario 7: API returns 404 for date with no steps"() {
        given: "no step data exists"

        when: "I query daily breakdown"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/steps/daily/2025-12-01")
                .get("/v1/steps/daily/2025-12-01")
                .then()
                .extract()

        then: "404 is returned"
        response.statusCode() == 404
    }

    def "Scenario 8: Range summary includes days with no data as zero"() {
        given: "steps only on first and last day of range"
        // 10:00 Warsaw time (CET = UTC+1) = 09:00 UTC
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-24T09:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-24T09:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-24T09:00:00Z",
                        "bucketEnd": "2025-11-24T09:01:00Z",
                        "count": 1000,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|2025-11-26T09:00:00Z",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-26T09:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-26T09:00:00Z",
                        "bucketEnd": "2025-11-26T09:01:00Z",
                        "count": 1000,
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

        and: "wait for async projections"
        waitForApiResponse("/v1/steps/daily/2025-11-26", DEVICE_ID, SECRET_BASE64)

        when: "I query 3-day range"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/steps/range?startDate=2025-11-24&endDate=2025-11-26")
                .get("/v1/steps/range?startDate=2025-11-24&endDate=2025-11-26")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "all 3 days are included"
        response.getList("dailyStats").size() == 3

        and: "middle day shows 0 steps"
        def dailyStats = response.getList("dailyStats")
        dailyStats[0].totalSteps == 1000
        dailyStats[1].totalSteps == 0  // 2025-11-25 has no data
        dailyStats[2].totalSteps == 1000

        and: "daysWithData counts only non-zero days"
        response.getInt("daysWithData") == 2
    }

    def "Scenario 9: Idempotent events don't duplicate projections"() {
        given: "a steps event"
        def date = "2025-11-27"
        def bucketStart = "2025-11-27T09:00:00Z"
        def bucketEnd = "2025-11-27T09:01:00Z"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|duplicate-test",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "${bucketEnd}",
                "payload": {
                    "bucketStart": "${bucketStart}",
                    "bucketEnd": "${bucketEnd}",
                    "count": 500,
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

        // Wait for first event processing
        waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projection is not duplicated (verified via API)"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/steps/daily/${date}")
                .get("/v1/steps/daily/${date}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
        response.getList("hourlyBreakdown")[10].steps == 500  // Still 500, not 1000
        response.getInt("totalSteps") == 500  // Still 500, not 1000
    }

    def "Scenario 10: Multiple buckets update time range correctly"() {
        given: "two buckets in same hour at different times"
        def date = "2025-11-28"
        // First bucket at 14:00
        def firstBucketStart = "2025-11-28T13:00:00Z"  // 14:00 Warsaw
        def firstBucketEnd = "2025-11-28T13:01:00Z"
        // Second bucket at 14:45
        def secondBucketStart = "2025-11-28T13:45:00Z"  // 14:45 Warsaw
        def secondBucketEnd = "2025-11-28T13:46:00Z"

        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|time-range-1",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "${firstBucketEnd}",
                    "payload": {
                        "bucketStart": "${firstBucketStart}",
                        "bucketEnd": "${firstBucketEnd}",
                        "count": 100,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "${DEVICE_ID}|steps|time-range-2",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "${secondBucketEnd}",
                    "payload": {
                        "bucketStart": "${secondBucketStart}",
                        "bucketEnd": "${secondBucketEnd}",
                        "count": 150,
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

        then: "projection has correct totals (verified via API)"
        def response = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getList("hourlyBreakdown")[14].steps == 250
        response.getInt("totalSteps") == 250
    }

    def "Scenario 11: Most active hour updates when different hour has more steps"() {
        given: "steps in two different hours, with different amounts"
        def date = "2025-11-29"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|hour10",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-29T09:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-29T09:00:00Z",
                    "bucketEnd": "2025-11-29T09:01:00Z",
                    "count": 300,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit first event for hour 10"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "hour 10 is most active"
        def response1 = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response1.getInt("mostActiveHour") == 10
        response1.getInt("mostActiveHourSteps") == 300

        when: "I submit event for hour 15 with more steps"
        def request2 = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|hour15",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-29T14:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-29T14:00:00Z",
                    "bucketEnd": "2025-11-29T14:01:00Z",
                    "count": 500,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request2)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "hour 15 becomes most active"
        waitForEventProcessing {
            def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/steps/daily/${date}")
                    .get("/v1/steps/daily/${date}")
                    .then()
                    .extract()
            response.statusCode() == 200 && response.body().jsonPath().getInt("mostActiveHour") == 15
        }
        def response2 = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/steps/daily/${date}")
                .get("/v1/steps/daily/${date}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
        response2.getInt("mostActiveHour") == 15
        response2.getInt("mostActiveHourSteps") == 500
        response2.getInt("totalSteps") == 800
        response2.getInt("activeHoursCount") == 2
    }

    def "Scenario 12: Range query with invalid dates returns 400"() {
        when: "I query range with endDate before startDate"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/steps/range?startDate=2025-11-30&endDate=2025-11-28")
                .get("/v1/steps/range?startDate=2025-11-30&endDate=2025-11-28")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }

    def "Scenario 13: Event without bucketStart is ignored in projection"() {
        given: "an event missing bucketStart"
        def date = "2025-12-01"
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|missing-bucket-start",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-12-01T10:01:00Z",
                "payload": {
                    "bucketEnd": "2025-12-01T10:01:00Z",
                    "count": 500,
                    "originPackage": "com.google.android.apps.fitness"
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

        then: "no projections are created (API returns 404)"
        apiReturns404("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
    }

    def "Scenario 14: Device isolation - different devices have separate projections"() {
        given: "steps from two different devices"
        def date = "2025-11-22"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|steps|2025-11-22T09:00:00Z",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-22T09:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-22T09:00:00Z",
                    "bucketEnd": "2025-11-22T09:01:00Z",
                    "count": 1000,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        def request2 = """
        {
            "events": [{
                "idempotencyKey": "different-device-id|steps|2025-11-22T09:00:00Z",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-22T09:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-22T09:00:00Z",
                    "bucketEnd": "2025-11-22T09:01:00Z",
                    "count": 2000,
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
        def response1 = waitForApiResponse("/v1/steps/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response1.getInt("totalSteps") == 1000

        and: "different device has its own data"
        def response2 = waitForApiResponse("/v1/steps/daily/${date}", "different-device-id", DIFFERENT_DEVICE_SECRET_BASE64)
        response2.getInt("totalSteps") == 2000
    }
}
