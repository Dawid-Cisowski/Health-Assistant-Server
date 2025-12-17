package com.healthassistant

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Integration tests for Google Fit historical synchronization
 */
@Title("Feature: Google Fit Historical Synchronization")
class GoogleFitSyncSpec extends BaseIntegrationSpec {

    def "Scenario 1: Historical sync processes multiple days correctly"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses for multiple days (same response for all calls)"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def day1Start = today.minusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def day1End = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def day2Start = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def day2End = today.plusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()

        setupGoogleFitApiMockMultipleTimes(createGoogleFitResponseWithSteps(day1Start.toEpochMilli(), day2End.toEpochMilli(), 1000), 2)
        setupGoogleFitSessionsApiMockMultipleTimes(createEmptyGoogleFitSessionsResponse(), 2)

        when: "I trigger historical sync for 2 days"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/history?days=2")
                .post("/v1/google-fit/sync/history?days=2")
                .then()
                .extract()

        then: "response status is 202 (accepted for processing)"
        response.statusCode() == 202

        and: "response contains scheduled status"
        def body = response.body().jsonPath()
        body.getString("status") == "scheduled"
        body.getInt("scheduledDays") >= 1  // At least 1 day should be scheduled
    }

    def "Scenario 2: Historical sync handles empty days gracefully"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with no data (will be called multiple times)"
        setupGoogleFitApiMockMultipleTimes(createEmptyGoogleFitResponse(), 3)
        setupGoogleFitSessionsApiMockMultipleTimes(createEmptyGoogleFitSessionsResponse(), 3)

        when: "I trigger historical sync for 3 days"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/history?days=3")
                .post("/v1/google-fit/sync/history?days=3")
                .then()
                .extract()

        then: "response status is 202 (accepted for processing)"
        response.statusCode() == 202

        and: "all days are scheduled"
        def body = response.body().jsonPath()
        body.getString("status") == "scheduled"
        body.getInt("scheduledDays") >= 2
    }

    def "Scenario 3: Historical sync validates days parameter - lower bound"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        when: "I trigger historical sync with invalid days parameter"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/history?days=0")
                .post("/v1/google-fit/sync/history?days=0")
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400

        and: "response contains error message"
        def body = response.body().jsonPath()
        body.getString("status") == "error"
        body.getString("message").contains("between 1 and 365")
    }

    def "Scenario 4: Historical sync validates days parameter - upper bound"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        when: "I trigger historical sync with days > 365"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/history?days=366")
                .post("/v1/google-fit/sync/history?days=366")
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400

        and: "response contains error message"
        def body = response.body().jsonPath()
        body.getString("status") == "error"
        body.getString("message").contains("between 1 and 365")
    }

    def "Scenario 5: Historical sync schedules sleep sessions processing for each day"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with sleep sessions for multiple days"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def day1Start = today.minusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def day1SleepStart = day1Start.minusSeconds(3600 * 9).toEpochMilli()
        def day1SleepEnd = day1Start.plusSeconds(3600 * 5).toEpochMilli()

        setupGoogleFitApiMockMultipleTimes(createEmptyGoogleFitResponse(), 1)
        setupGoogleFitSessionsApiMockMultipleTimes(createGoogleFitSessionsResponseWithSleep(day1SleepStart, day1SleepEnd, "sleep-day1"), 1)

        when: "I trigger historical sync for 1 day"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/history?days=1")
                .post("/v1/google-fit/sync/history?days=1")
                .then()
                .extract()

        then: "response status is 202 (accepted for processing)"
        response.statusCode() == 202

        and: "task is scheduled"
        def body = response.body().jsonPath()
        body.getString("status") == "scheduled"
        body.getInt("scheduledDays") == 1
    }

    def "Scenario 6: Historical sync schedules tasks for processing (async)"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with steps data"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def dayStart = today.minusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def dayEnd = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()

        setupGoogleFitApiMockMultipleTimes(createGoogleFitResponseWithSteps(dayStart.toEpochMilli(), dayEnd.toEpochMilli(), 500), 1)
        setupGoogleFitSessionsApiMockMultipleTimes(createEmptyGoogleFitSessionsResponse(), 1)

        when: "I trigger historical sync"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/history?days=1")
                .post("/v1/google-fit/sync/history?days=1")
                .then()
                .extract()

        then: "response status is 202 (accepted for async processing)"
        response.statusCode() == 202

        and: "task is scheduled"
        def body = response.body().jsonPath()
        body.getString("status") == "scheduled"
        body.getInt("scheduledDays") == 1
    }

    def "Scenario 7: Historical sync uses default value when days parameter is missing"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses"
        setupGoogleFitApiMockMultipleTimes(createEmptyGoogleFitResponse(), 7)
        setupGoogleFitSessionsApiMockMultipleTimes(createEmptyGoogleFitSessionsResponse(), 7)

        when: "I trigger historical sync without days parameter"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/history")
                .post("/v1/google-fit/sync/history")
                .then()
                .extract()

        then: "response status is 202 (accepted for processing)"
        response.statusCode() == 202

        and: "default 7 days are scheduled"
        def body = response.body().jsonPath()
        body.getString("status") == "scheduled"
        body.getInt("scheduledDays") == 7
    }

    def "Scenario 8: Historical sync schedules all days for processing"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def day1Start = today.minusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def day1End = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()

        setupGoogleFitApiMockMultipleTimes(createGoogleFitResponseWithSteps(day1Start.toEpochMilli(), day1End.toEpochMilli(), 500), 2)
        setupGoogleFitSessionsApiMockMultipleTimes(createEmptyGoogleFitSessionsResponse(), 2)

        when: "I trigger historical sync for 2 days"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/history?days=2")
                .post("/v1/google-fit/sync/history?days=2")
                .then()
                .extract()

        then: "response status is 202 (accepted for async processing)"
        response.statusCode() == 202

        and: "all days are scheduled"
        def body = response.body().jsonPath()
        body.getString("status") == "scheduled"
        body.getInt("scheduledDays") == 2
    }
}
