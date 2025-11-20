package com.healthassistant

import com.healthassistant.application.steps.projection.StepsDailyProjectionJpaRepository
import com.healthassistant.application.steps.projection.StepsHourlyProjectionJpaRepository
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.LocalDate
import java.time.ZoneId

/**
 * Integration tests for Steps Projections
 */
@Title("Feature: Steps Projections and Query API")
class StepsProjectionSpec extends BaseIntegrationSpec {

    @Autowired
    StepsDailyProjectionJpaRepository dailyProjectionRepository

    @Autowired
    StepsHourlyProjectionJpaRepository hourlyProjectionRepository

    def setup() {
        // Clean projection tables (in addition to base cleanup)
        hourlyProjectionRepository?.deleteAll()
        dailyProjectionRepository?.deleteAll()
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
                "idempotencyKey": "test-device|steps|${bucketStart}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "${bucketEnd}",
                "payload": {
                    "bucketStart": "${bucketStart}",
                    "bucketEnd": "${bucketEnd}",
                    "count": 150,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit the steps event"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "hourly projection is created"
        def hourlyData = hourlyProjectionRepository.findByDateAndHour(LocalDate.parse(date), 10)
        hourlyData.isPresent()
        hourlyData.get().stepCount == 150
        hourlyData.get().bucketCount == 1

        and: "daily projection is created with totals"
        def dailyData = dailyProjectionRepository.findByDate(LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalSteps == 150
        dailyData.get().mostActiveHour == 10
        dailyData.get().mostActiveHourSteps == 150
        dailyData.get().activeHoursCount == 1
    }

    def "Scenario 2: Multiple buckets same hour accumulate steps"() {
        given: "multiple step events in same hour"
        def date = "2025-11-20"
        // 14:00 Warsaw time (CET = UTC+1) = 13:00 UTC
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|steps|2025-11-20T13:00:00Z",
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
                    "idempotencyKey": "test-device|steps|2025-11-20T13:01:00Z",
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
            "deviceId": "test-device"
        }
        """

        when: "I submit multiple events"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "hourly projection accumulates steps"
        def hourlyData = hourlyProjectionRepository.findByDateAndHour(LocalDate.parse(date), 14)
        hourlyData.isPresent()
        hourlyData.get().stepCount == 220
        hourlyData.get().bucketCount == 2

        and: "daily projection reflects total"
        def dailyData = dailyProjectionRepository.findByDate(LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalSteps == 220
    }

    def "Scenario 3: Steps across multiple hours create separate hourly projections"() {
        given: "steps in different hours"
        def date = "2025-11-20"
        // Warsaw times: 08:00, 12:00, 18:00 (CET = UTC+1) => UTC: 07:00, 11:00, 17:00
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|steps|2025-11-20T07:00:00Z",
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
                    "idempotencyKey": "test-device|steps|2025-11-20T11:00:00Z",
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
                    "idempotencyKey": "test-device|steps|2025-11-20T17:00:00Z",
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
            "deviceId": "test-device"
        }
        """

        when: "I submit steps for different hours"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "three hourly projections are created"
        def hourlyData = hourlyProjectionRepository.findByDateOrderByHourAsc(LocalDate.parse(date))
        hourlyData.size() == 3
        hourlyData[0].hour == 8
        hourlyData[0].stepCount == 200
        hourlyData[1].hour == 12
        hourlyData[1].stepCount == 300
        hourlyData[2].hour == 18
        hourlyData[2].stepCount == 400

        and: "daily projection shows most active hour and total"
        def dailyData = dailyProjectionRepository.findByDate(LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalSteps == 900
        dailyData.get().mostActiveHour == 18
        dailyData.get().mostActiveHourSteps == 400
        dailyData.get().activeHoursCount == 3
    }

    def "Scenario 4: Query API returns daily breakdown with 24 hours"() {
        given: "steps in multiple hours"
        def date = "2025-11-21"
        // Warsaw times: 09:00, 15:00 (CET = UTC+1) => UTC: 08:00, 14:00
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|steps|2025-11-21T08:00:00Z",
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
                    "idempotencyKey": "test-device|steps|2025-11-21T14:00:00Z",
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
            "deviceId": "test-device"
        }
        """

        and: "events are submitted"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query daily breakdown"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/steps/daily/${date}")
                .get("/v1/steps/daily/${date}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

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
                    "idempotencyKey": "test-device|steps|2025-11-18T09:00:00Z",
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
                    "idempotencyKey": "test-device|steps|2025-11-19T10:00:00Z",
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
                    "idempotencyKey": "test-device|steps|2025-11-20T11:00:00Z",
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
            "deviceId": "test-device"
        }
        """

        and: "events are submitted"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query range summary"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/steps/range?startDate=2025-11-18&endDate=2025-11-20")
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
                "idempotencyKey": "test-device|steps|2025-11-23T10:00:00Z",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-23T10:01:00Z",
                "payload": {
                    "bucketStart": "2025-11-23T10:00:00Z",
                    "bucketEnd": "2025-11-23T10:01:00Z",
                    "count": 0,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit zero-step event"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "no projections are created"
        hourlyProjectionRepository.findAll().isEmpty()
        dailyProjectionRepository.findAll().isEmpty()
    }

    def "Scenario 7: API returns 404 for date with no steps"() {
        given: "no step data exists"

        when: "I query daily breakdown"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/steps/daily/2025-12-01")
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
                    "idempotencyKey": "test-device|steps|2025-11-24T09:00:00Z",
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
                    "idempotencyKey": "test-device|steps|2025-11-26T09:00:00Z",
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
            "deviceId": "test-device"
        }
        """

        and: "events are submitted"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query 3-day range"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/steps/range?startDate=2025-11-24&endDate=2025-11-26")
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
}
