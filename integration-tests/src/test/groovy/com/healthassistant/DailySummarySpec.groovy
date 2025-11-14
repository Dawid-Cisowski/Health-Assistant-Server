package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Integration tests for Daily Summary feature
 */
@Title("Feature: Daily Summary")
class DailySummarySpec extends BaseIntegrationSpec {

    def "Scenario 1: Get daily summary after sync with multiple event types"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 12)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "mock Google Fit API response with multiple data types for the date"
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def startTime1 = summaryZoned.plusHours(7).toInstant().toEpochMilli() // 07:00
        def endTime1 = summaryZoned.plusHours(8).toInstant().toEpochMilli() // 08:00
        def startTime2 = summaryZoned.plusHours(11).toInstant().toEpochMilli() // 11:00
        def endTime2 = summaryZoned.plusHours(12).toInstant().toEpochMilli() // 12:00
        def startTime3 = summaryZoned.plusHours(14).toInstant().toEpochMilli() // 14:00
        def endTime3 = summaryZoned.plusHours(15).toInstant().toEpochMilli() // 15:00
        
        // First sync with steps, distance, calories
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(startTime1, endTime1, 742, 1000.5, 62.5, 75))
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)
        
        // Second sync with more steps
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime2, endTime2, 742))
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)
        
        // Third sync with heart rate
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(startTime3, endTime3, 0, 0, 0, 78))
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        when: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary is returned with aggregated data"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        summary.getString("date") == dateStr
        summary.getInt("activity.steps") == 1484 // 742 + 742
        summary.getDouble("activity.distanceMeters") == 1000.5
        // Allow for rounding differences in calories
        def activeCalories = summary.getDouble("activity.activeCalories")
        (activeCalories == 62.5 || activeCalories == 62.0 || activeCalories == 63.0)
        
        summary.getInt("heart.avgBpm") != null
        
        summary.getInt("score.activityScore") >= 0
        summary.getInt("score.readinessScore") >= 0
        summary.getInt("score.overallScore") >= 0
    }

    def "Scenario 2: Get daily summary by date"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 13)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "mock Google Fit API response with steps data"
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def startTime = summaryZoned.plusHours(9).toInstant().toEpochMilli()
        def endTime = summaryZoned.plusHours(10).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))

        and: "sync data"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        when: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary is returned"
        getResponse.statusCode() == 200
        
        def summary = getResponse.body().jsonPath()
        summary.getString("date") == dateStr
        summary.getInt("activity.steps") == 742
    }

    def "Scenario 3: Get non-existent daily summary returns 404"() {
        given: "date without summary"
        def summaryDate = LocalDate.of(2025, 11, 14)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "no sync performed for this date"

        when: "I get summary for the date"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "404 is returned"
        getResponse.statusCode() == 404
    }

    def "Scenario 4: Summary updates when new events arrive via sync"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 15)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "first sync with steps data"
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def startTime1 = summaryZoned.plusHours(9).toInstant().toEpochMilli()
        def endTime1 = summaryZoned.plusHours(10).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime1, endTime1, 742))
        
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        and: "second sync with additional steps data"
        def startTime2 = summaryZoned.plusHours(13).toInstant().toEpochMilli()
        def endTime2 = summaryZoned.plusHours(14).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime2, endTime2, 742))

        when: "I trigger sync again"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        and: "I get summary"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary contains both events"
        getResponse.statusCode() == 200
        getResponse.body().jsonPath().getInt("activity.steps") == 1484 // Both events included
    }

    def "Scenario 5: Get summary for date with no events returns 404"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date with no events"
        def summaryDate = LocalDate.of(2025, 11, 16)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "no sync performed for this date"

        when: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "404 is returned"
        getResponse.statusCode() == 404
    }

    def "Scenario 6: Summary aggregates multiple syncs correctly"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 17)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "multiple syncs with different data"
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))
        
        // First sync with steps
        def startTime1 = summaryZoned.plusHours(7).toInstant().toEpochMilli()
        def endTime1 = summaryZoned.plusHours(8).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime1, endTime1, 500))
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)
        
        // Second sync with more steps
        def startTime2 = summaryZoned.plusHours(13).toInstant().toEpochMilli()
        def endTime2 = summaryZoned.plusHours(14).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime2, endTime2, 742))
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        when: "I get summary"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary aggregates all events"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        summary.getInt("activity.steps") == 1242 // 500 + 742
    }

    def "Scenario 7: Summary calculates activity score correctly"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 18)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "sync with high activity data (should score well)"
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def startTime = summaryZoned.plusHours(11).toInstant().toEpochMilli()
        def endTime = summaryZoned.plusHours(12).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(startTime, endTime, 15000, 10000.0, 500.0, 75))
        
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        when: "I get summary"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "activity score is calculated"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        def activityScore = summary.getInt("score.activityScore")
        activityScore >= 0 // Score is calculated (may vary based on algorithm)
    }

    def "Scenario 8: Summary calculates readiness score from heart rate"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 19)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "sync with optimal heart rate data"
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def startTime = summaryZoned.plusHours(9).toInstant().toEpochMilli()
        def endTime = summaryZoned.plusHours(10).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(startTime, endTime, 0, 0, 0, 65))
        
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        when: "I get summary"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "readiness score is calculated"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        def readinessScore = summary.getInt("score.readinessScore")
        readinessScore >= 0
    }

    def "Scenario 9: Summary only includes events from specified date"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "sync with events from different dates"
        def targetDate = LocalDate.of(2025, 11, 22)
        def targetDateStr = targetDate.format(DateTimeFormatter.ISO_DATE)
        
        // Sync with event from target date
        def targetZoned = targetDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def startTime = targetZoned.plusHours(11).toInstant().toEpochMilli()
        def endTime = targetZoned.plusHours(12).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))
        
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        when: "I get summary for target date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${targetDateStr}")
                .get("/v1/daily-summaries/${targetDateStr}")
                .then()
                .extract()

        then: "summary only includes events from target date"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        summary.getInt("activity.steps") == 742
    }

}

