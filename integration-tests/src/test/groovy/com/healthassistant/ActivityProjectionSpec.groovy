package com.healthassistant

import com.healthassistant.activity.ActivityDailyProjectionJpaRepository
import com.healthassistant.activity.ActivityHourlyProjectionJpaRepository
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Activity (Active Minutes) Projections
 */
@Title("Feature: Activity Projections and Query API")
class ActivityProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    @Autowired
    ActivityDailyProjectionJpaRepository dailyProjectionRepository

    @Autowired
    ActivityHourlyProjectionJpaRepository hourlyProjectionRepository

    def setup() {
        hourlyProjectionRepository?.deleteAll()
        dailyProjectionRepository?.deleteAll()
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

        then: "hourly projection is created"
        def hourlyData = hourlyProjectionRepository.findByDeviceIdAndDateAndHour(DEVICE_ID, LocalDate.parse(date), 10)
        hourlyData.isPresent()
        hourlyData.get().activeMinutes == 15
        hourlyData.get().bucketCount == 1

        and: "daily projection is created with totals"
        def dailyData = dailyProjectionRepository.findByDeviceIdAndDate(DEVICE_ID, LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalActiveMinutes == 15
        dailyData.get().mostActiveHour == 10
        dailyData.get().mostActiveHourMinutes == 15
        dailyData.get().activeHoursCount == 1
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

        then: "hourly projection accumulates minutes"
        def hourlyData = hourlyProjectionRepository.findByDeviceIdAndDateAndHour(DEVICE_ID, LocalDate.parse(date), 14)
        hourlyData.isPresent()
        hourlyData.get().activeMinutes == 18
        hourlyData.get().bucketCount == 2

        and: "daily projection reflects total"
        def dailyData = dailyProjectionRepository.findByDeviceIdAndDate(DEVICE_ID, LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalActiveMinutes == 18
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

        then: "three hourly projections are created"
        def hourlyData = hourlyProjectionRepository.findByDeviceIdAndDateOrderByHourAsc(DEVICE_ID, LocalDate.parse(date))
        hourlyData.size() == 3
        hourlyData[0].hour == 8
        hourlyData[0].activeMinutes == 5
        hourlyData[1].hour == 12
        hourlyData[1].activeMinutes == 20
        hourlyData[2].hour == 18
        hourlyData[2].activeMinutes == 30

        and: "daily projection shows most active hour and total"
        def dailyData = dailyProjectionRepository.findByDeviceIdAndDate(DEVICE_ID, LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalActiveMinutes == 55
        dailyData.get().mostActiveHour == 18
        dailyData.get().mostActiveHourMinutes == 30
        dailyData.get().activeHoursCount == 3
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

        then: "no projections are created"
        hourlyProjectionRepository.findAll().isEmpty()
        dailyProjectionRepository.findAll().isEmpty()
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

        then: "each device has its own projection"
        def device1Data = dailyProjectionRepository.findByDeviceIdAndDate(DEVICE_ID, LocalDate.parse(date))
        device1Data.isPresent()
        device1Data.get().totalActiveMinutes == 10

        def device2Data = dailyProjectionRepository.findByDeviceIdAndDate("different-device-id", LocalDate.parse(date))
        device2Data.isPresent()
        device2Data.get().totalActiveMinutes == 25

        when: "I query API for test-device"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/activity/daily/${date}")
                .get("/v1/activity/daily/${date}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "only test-device data is returned"
        response.getInt("totalActiveMinutes") == 10
    }
}
