package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Title("Feature: AI Daily Summary Caching")
class AiDailySummaryCacheSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    def "should return cached AI summary when no new events"() {
        given: "health data exists for a date"
        def summaryDate = LocalDate.of(2025, 12, 20)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))

        def stepsEvent = [
            idempotencyKey: "steps-cache-test-1",
            type: "StepsBucketedRecorded.v1",
            occurredAt: summaryZoned.plusHours(10).toInstant().toString(),
            payload: [
                bucketStart: summaryZoned.plusHours(9).toInstant().toString(),
                bucketEnd: summaryZoned.plusHours(10).toInstant().toString(),
                count: 5000,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: [stepsEvent],
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        and: "AI is configured with first response"
        TestChatModelConfiguration.setResponse("pierwsze podsumowanie AI")
        TestChatModelConfiguration.resetCallCount()

        when: "first request is made"
        def response1 = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        then: "AI was called once"
        response1.statusCode() == 200
        response1.body().jsonPath().getString("summary") == "pierwsze podsumowanie AI"
        TestChatModelConfiguration.getCallCount() == 1

        when: "AI response is changed and second request is made (without new events)"
        TestChatModelConfiguration.setResponse("drugie podsumowanie AI - NIE POWINNO SIE POJAWIC")

        def response2 = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        then: "cached response is returned and AI was not called again"
        response2.statusCode() == 200
        response2.body().jsonPath().getString("summary") == "pierwsze podsumowanie AI"
        TestChatModelConfiguration.getCallCount() == 1
    }

    def "should regenerate AI summary when new events arrive"() {
        given: "health data exists for a date"
        def summaryDate = LocalDate.of(2025, 12, 21)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))

        def stepsEvent1 = [
            idempotencyKey: "steps-cache-test-2a",
            type: "StepsBucketedRecorded.v1",
            occurredAt: summaryZoned.plusHours(10).toInstant().toString(),
            payload: [
                bucketStart: summaryZoned.plusHours(9).toInstant().toString(),
                bucketEnd: summaryZoned.plusHours(10).toInstant().toString(),
                count: 3000,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: [stepsEvent1],
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        and: "AI generates first summary"
        TestChatModelConfiguration.setResponse("stare podsumowanie - 3000 krokow")
        TestChatModelConfiguration.resetCallCount()

        def response1 = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        expect: "first summary is generated"
        response1.statusCode() == 200
        response1.body().jsonPath().getString("summary") == "stare podsumowanie - 3000 krokow"
        TestChatModelConfiguration.getCallCount() == 1

        when: "new events are added for the same date"
        def stepsEvent2 = [
            idempotencyKey: "steps-cache-test-2b",
            type: "StepsBucketedRecorded.v1",
            occurredAt: summaryZoned.plusHours(15).toInstant().toString(),
            payload: [
                bucketStart: summaryZoned.plusHours(14).toInstant().toString(),
                bucketEnd: summaryZoned.plusHours(15).toInstant().toString(),
                count: 5000,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: [stepsEvent2],
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        and: "AI response is updated"
        TestChatModelConfiguration.setResponse("nowe podsumowanie - 8000 krokow")

        and: "request is made after new events"
        def response2 = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        then: "new summary is generated (AI called again)"
        response2.statusCode() == 200
        response2.body().jsonPath().getString("summary") == "nowe podsumowanie - 8000 krokow"
        TestChatModelConfiguration.getCallCount() == 2
    }

    def "should generate AI summary on first request"() {
        given: "health data exists for a new date"
        def summaryDate = LocalDate.of(2025, 12, 22)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))

        def stepsEvent = [
            idempotencyKey: "steps-cache-test-3",
            type: "StepsBucketedRecorded.v1",
            occurredAt: summaryZoned.plusHours(10).toInstant().toString(),
            payload: [
                bucketStart: summaryZoned.plusHours(9).toInstant().toString(),
                bucketEnd: summaryZoned.plusHours(10).toInstant().toString(),
                count: 7500,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: [stepsEvent],
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        and: "AI is configured"
        TestChatModelConfiguration.setResponse("pierwsza generacja dla tego dnia")
        TestChatModelConfiguration.resetCallCount()

        when: "first request is made"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        then: "AI is called and summary is returned"
        response.statusCode() == 200
        response.body().jsonPath().getBoolean("dataAvailable") == true
        response.body().jsonPath().getString("summary") == "pierwsza generacja dla tego dnia"
        TestChatModelConfiguration.getCallCount() == 1
    }

    def "events for different dates should not invalidate cache for other dates"() {
        given: "data exists for two dates"
        def date1 = LocalDate.of(2025, 12, 23)
        def date2 = LocalDate.of(2025, 12, 24)
        def dateStr1 = date1.format(DateTimeFormatter.ISO_DATE)
        def dateStr2 = date2.format(DateTimeFormatter.ISO_DATE)
        def zoned1 = date1.atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def zoned2 = date2.atStartOfDay(ZoneId.of("Europe/Warsaw"))

        // Create data for date1
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: [[
                idempotencyKey: "steps-cache-test-4a",
                type: "StepsBucketedRecorded.v1",
                occurredAt: zoned1.plusHours(10).toInstant().toString(),
                payload: [
                    bucketStart: zoned1.plusHours(9).toInstant().toString(),
                    bucketEnd: zoned1.plusHours(10).toInstant().toString(),
                    count: 4000,
                    originPackage: "com.google.android.apps.fitness"
                ]
            ]],
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        and: "AI summary is generated for date1"
        TestChatModelConfiguration.setResponse("podsumowanie dla 23 grudnia")
        TestChatModelConfiguration.resetCallCount()

        authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr1}/ai-text")
                .get("/v1/daily-summaries/${dateStr1}/ai-text")
                .then()
                .statusCode(200)

        expect: "AI was called once for date1"
        TestChatModelConfiguration.getCallCount() == 1

        when: "new events are added for date2 (different date)"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: [[
                idempotencyKey: "steps-cache-test-4b",
                type: "StepsBucketedRecorded.v1",
                occurredAt: zoned2.plusHours(12).toInstant().toString(),
                payload: [
                    bucketStart: zoned2.plusHours(11).toInstant().toString(),
                    bucketEnd: zoned2.plusHours(12).toInstant().toString(),
                    count: 6000,
                    originPackage: "com.google.android.apps.fitness"
                ]
            ]],
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        and: "AI response changes"
        TestChatModelConfiguration.setResponse("nowe podsumowanie - nie powinno sie pojawic dla date1")

        and: "request for date1 is made again"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr1}/ai-text")
                .get("/v1/daily-summaries/${dateStr1}/ai-text")
                .then()
                .extract()

        then: "cached summary for date1 is returned (AI not called again)"
        response.statusCode() == 200
        response.body().jsonPath().getString("summary") == "podsumowanie dla 23 grudnia"
        TestChatModelConfiguration.getCallCount() == 1
    }
}
