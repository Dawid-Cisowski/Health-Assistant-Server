package com.healthassistant

import com.healthassistant.activity.api.ActivityFacade
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

/**
 * Integration tests for Activity (Active Minutes) Projections
 */
@Title("Feature: Activity Projections and Query API")
class ActivityProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    @Autowired
    ActivityFacade activityFacade

    def setup() {
        activityFacade?.deleteAllProjections()
    }

    def "Scenario 1: ActiveMinutesRecorded event creates hourly and daily projections"() {
        given: "an activity event for a specific hour"
        def date = "2025-11-20"
        def bucketStart = "2025-11-20T09:00:00Z"
        def bucketEnd = "2025-11-20T09:01:00Z"
        def request = """
        {
            "events": [{
                "idempotencyKey": "test-device|activity|${bucketStart}",
                "type": "ActiveMinutesRecorded.v1",
                "occurredAt": "${bucketEnd}",
                "payload": {
                    "bucketStart": "${bucketStart}",
                    "bucketEnd": "${bucketEnd}",
                    "activeMinutes": 15,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit the activity event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections are created (verified via API)"
        def response = waitForApiResponse("/v1/activity/daily/${date}")
        response.getList("hourlyBreakdown")[10].activeMinutes == 15
        response.getInt("totalActiveMinutes") == 15
        response.getInt("mostActiveHour") == 10
        response.getInt("mostActiveHourMinutes") == 15
        response.getInt("activeHoursCount") == 1
    }

    def "Scenario 2: Multiple buckets same hour accumulate minutes"() {
        given: "multiple activity events in same hour"
        def date = "2025-11-20"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|activity|2025-11-20T13:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-20T13:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T13:00:00Z",
                        "bucketEnd": "2025-11-20T13:01:00Z",
                        "activeMinutes": 10,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|activity|2025-11-20T13:01:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-20T13:02:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T13:01:00Z",
                        "bucketEnd": "2025-11-20T13:02:00Z",
                        "activeMinutes": 8,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit multiple events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections accumulate minutes (verified via API)"
        def response = waitForApiResponse("/v1/activity/daily/${date}")
        response.getList("hourlyBreakdown")[14].activeMinutes == 18
        response.getInt("totalActiveMinutes") == 18
    }

    def "Scenario 3: Activity across multiple hours create separate hourly projections"() {
        given: "activity in different hours"
        def date = "2025-11-20"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|activity|2025-11-20T07:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-20T07:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T07:00:00Z",
                        "bucketEnd": "2025-11-20T07:01:00Z",
                        "activeMinutes": 5,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|activity|2025-11-20T11:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-20T11:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T11:00:00Z",
                        "bucketEnd": "2025-11-20T11:01:00Z",
                        "activeMinutes": 20,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|activity|2025-11-20T17:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-20T17:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T17:00:00Z",
                        "bucketEnd": "2025-11-20T17:01:00Z",
                        "activeMinutes": 30,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit activity for different hours"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "three hourly projections are created (verified via API)"
        def response = waitForApiResponse("/v1/activity/daily/${date}")
        def hourlyBreakdown = response.getList("hourlyBreakdown")
        hourlyBreakdown[8].activeMinutes == 5
        hourlyBreakdown[12].activeMinutes == 20
        hourlyBreakdown[18].activeMinutes == 30

        and: "daily projection shows most active hour and total"
        response.getInt("totalActiveMinutes") == 55
        response.getInt("mostActiveHour") == 18
        response.getInt("mostActiveHourMinutes") == 30
        response.getInt("activeHoursCount") == 3
    }

    def "Scenario 4: Query API returns daily breakdown with 24 hours"() {
        given: "activity in multiple hours"
        def date = "2025-11-21"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|activity|2025-11-21T08:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-21T08:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-21T08:00:00Z",
                        "bucketEnd": "2025-11-21T08:01:00Z",
                        "activeMinutes": 12,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|activity|2025-11-21T14:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-21T14:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-21T14:00:00Z",
                        "bucketEnd": "2025-11-21T14:01:00Z",
                        "activeMinutes": 25,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query daily breakdown"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/activity/daily/${date}")
                .get("/v1/activity/daily/${date}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response contains 24 hourly entries"
        response.getList("hourlyBreakdown").size() == 24

        and: "hours with activity show correct values"
        def hourlyBreakdown = response.getList("hourlyBreakdown")
        hourlyBreakdown[9].activeMinutes == 12
        hourlyBreakdown[15].activeMinutes == 25

        and: "hours without activity show 0"
        hourlyBreakdown[0].activeMinutes == 0
        hourlyBreakdown[23].activeMinutes == 0

        and: "daily totals are correct"
        response.getInt("totalActiveMinutes") == 37
        response.getInt("mostActiveHour") == 15
        response.getInt("mostActiveHourMinutes") == 25
        response.getInt("activeHoursCount") == 2
    }

    def "Scenario 5: Query API returns range summary with daily stats"() {
        given: "activity across multiple days"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|activity|2025-11-18T09:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-18T09:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-18T09:00:00Z",
                        "bucketEnd": "2025-11-18T09:01:00Z",
                        "activeMinutes": 30,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|activity|2025-11-19T10:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-19T10:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-19T10:00:00Z",
                        "bucketEnd": "2025-11-19T10:01:00Z",
                        "activeMinutes": 45,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|activity|2025-11-20T11:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-20T11:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T11:00:00Z",
                        "bucketEnd": "2025-11-20T11:01:00Z",
                        "activeMinutes": 60,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query range summary"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/activity/range?startDate=2025-11-18&endDate=2025-11-20")
                .get("/v1/activity/range?startDate=2025-11-18&endDate=2025-11-20")
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
        dailyStats[0].totalActiveMinutes == 30
        dailyStats[1].date == "2025-11-19"
        dailyStats[1].totalActiveMinutes == 45
        dailyStats[2].date == "2025-11-20"
        dailyStats[2].totalActiveMinutes == 60

        and: "totals are correct"
        response.getInt("totalActiveMinutes") == 135
        response.getInt("averageActiveMinutes") == 45
        response.getInt("daysWithData") == 3
    }

    def "Scenario 6: Zero-minute buckets are ignored"() {
        given: "a bucket with zero minutes"
        def request = """
        {
            "events": [{
                "idempotencyKey": "test-device|activity|2025-11-23T10:00:00Z",
                "type": "ActiveMinutesRecorded.v1",
                "occurredAt": "2025-11-23T10:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-23T10:00:00Z",
                    "bucketEnd": "2025-11-23T10:01:00Z",
                    "activeMinutes": 0,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit zero-minute event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "no projections are created (verified via API returning 404)"
        apiReturns404("/v1/activity/daily/2025-11-23")
    }

    def "Scenario 7: API returns 404 for date with no activity"() {
        given: "no activity data exists"

        when: "I query daily breakdown"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/activity/daily/2025-12-01")
                .get("/v1/activity/daily/2025-12-01")
                .then()
                .extract()

        then: "404 is returned"
        response.statusCode() == 404
    }

    def "Scenario 8: Range summary includes days with no data as zero"() {
        given: "activity only on first and last day of range"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|activity|2025-11-24T09:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-24T09:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-24T09:00:00Z",
                        "bucketEnd": "2025-11-24T09:01:00Z",
                        "activeMinutes": 20,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|activity|2025-11-26T09:00:00Z",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-26T09:01:00Z",
                    "payload": {
                        "bucketStart": "2025-11-26T09:00:00Z",
                        "bucketEnd": "2025-11-26T09:01:00Z",
                        "activeMinutes": 20,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query 3-day range"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/activity/range?startDate=2025-11-24&endDate=2025-11-26")
                .get("/v1/activity/range?startDate=2025-11-24&endDate=2025-11-26")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "all 3 days are included"
        response.getList("dailyStats").size() == 3

        and: "middle day shows 0 minutes"
        def dailyStats = response.getList("dailyStats")
        dailyStats[0].totalActiveMinutes == 20
        dailyStats[1].totalActiveMinutes == 0
        dailyStats[2].totalActiveMinutes == 20

        and: "daysWithData counts only non-zero days"
        response.getInt("daysWithData") == 2
    }

    def "Scenario 9: Range query with invalid dates returns 400"() {
        when: "I query range with endDate before startDate"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/activity/range?startDate=2025-11-30&endDate=2025-11-28")
                .get("/v1/activity/range?startDate=2025-11-30&endDate=2025-11-28")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }

    def "Scenario 10: Device isolation - different devices have separate projections"() {
        given: "activity from two different devices"
        def date = "2025-11-22"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "test-device|activity|2025-11-22T09:00:00Z",
                "type": "ActiveMinutesRecorded.v1",
                "occurredAt": "2025-11-22T09:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-22T09:00:00Z",
                    "bucketEnd": "2025-11-22T09:01:00Z",
                    "activeMinutes": 10,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """
        def request2 = """
        {
            "events": [{
                "idempotencyKey": "different-device-id|activity|2025-11-22T09:00:00Z",
                "type": "ActiveMinutesRecorded.v1",
                "occurredAt": "2025-11-22T09:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-22T09:00:00Z",
                    "bucketEnd": "2025-11-22T09:01:00Z",
                    "activeMinutes": 25,
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
        def response1 = waitForApiResponse("/v1/activity/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response1.getInt("totalActiveMinutes") == 10

        and: "different device has its own data"
        def response2 = waitForApiResponse("/v1/activity/daily/${date}", "different-device-id", DIFFERENT_DEVICE_SECRET_BASE64)
        response2.getInt("totalActiveMinutes") == 25
    }
}
