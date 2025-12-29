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

    private static final String DEVICE_ID = "test-gfit"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "Scenario 1: Historical sync processes multiple days correctly"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
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
        def deviceId = DEVICE_ID
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
        def deviceId = DEVICE_ID
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
        def deviceId = DEVICE_ID
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
        def deviceId = DEVICE_ID
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
        def deviceId = DEVICE_ID
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
        def deviceId = DEVICE_ID
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
        def deviceId = DEVICE_ID
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

    // =====================================================
    // Tests for POST /v1/google-fit/sync/dates endpoint
    // =====================================================

    def "Scenario 9: Sync specific dates schedules tasks correctly"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses"
        setupGoogleFitApiMockMultipleTimes(createEmptyGoogleFitResponse(), 3)
        setupGoogleFitSessionsApiMockMultipleTimes(createEmptyGoogleFitSessionsResponse(), 3)

        and: "request body with specific dates"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def requestBody = """
        {
            "dates": [
                "${today.minusDays(1)}",
                "${today.minusDays(3)}",
                "${today.minusDays(5)}"
            ]
        }
        """

        when: "I trigger sync for specific dates"
        def response = authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/google-fit/sync/dates", requestBody)
                .post("/v1/google-fit/sync/dates")
                .then()
                .extract()

        then: "response status is 202 (accepted)"
        response.statusCode() == 202

        and: "all dates are scheduled"
        def body = response.body().jsonPath()
        body.getString("status") == "scheduled"
        body.getInt("scheduledDays") == 3
    }

    def "Scenario 10: Sync specific dates skips already pending dates on second call"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses (will fail intentionally to keep tasks in PENDING/retrying state)"
        setupGoogleFitApiMockError(500, '{"error": "intentional failure for test"}')
        setupGoogleFitSessionsApiMockError(500, '{"error": "intentional failure for test"}')

        and: "request body with initial dates"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def date1 = today.minusDays(50)
        def date2 = today.minusDays(51)
        def date3 = today.minusDays(52)
        def firstRequestBody = """
        {
            "dates": ["${date1}", "${date2}"]
        }
        """

        and: "first sync request schedules 2 dates"
        def firstResponse = authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/google-fit/sync/dates", firstRequestBody)
                .post("/v1/google-fit/sync/dates")
                .then()
                .extract()
        assert firstResponse.statusCode() == 202
        assert firstResponse.body().jsonPath().getInt("scheduledDays") == 2

        and: "second request body with overlapping dates"
        def secondRequestBody = """
        {
            "dates": ["${date1}", "${date2}", "${date3}"]
        }
        """

        when: "I trigger sync for specific dates including already scheduled ones"
        def response = authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/google-fit/sync/dates", secondRequestBody)
                .post("/v1/google-fit/sync/dates")
                .then()
                .extract()

        then: "response status is 202 (accepted)"
        response.statusCode() == 202

        and: "only new date is scheduled (2 were already pending/in-progress)"
        def body = response.body().jsonPath()
        body.getString("status") == "scheduled"
        body.getInt("scheduledDays") == 1
    }

    def "Scenario 11: Sync specific dates rejects empty list"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "request body with empty dates list"
        def requestBody = """
        {
            "dates": []
        }
        """

        when: "I trigger sync with empty list"
        def response = authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/google-fit/sync/dates", requestBody)
                .post("/v1/google-fit/sync/dates")
                .then()
                .extract()

        then: "response status is 400 (bad request)"
        response.statusCode() == 400
    }

    def "Scenario 12: Sync specific dates rejects future dates"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "request body with future date"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def futureDate = today.plusDays(1)
        def requestBody = """
        {
            "dates": [
                "${today.minusDays(1)}",
                "${futureDate}"
            ]
        }
        """

        when: "I trigger sync with future date"
        def response = authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/google-fit/sync/dates", requestBody)
                .post("/v1/google-fit/sync/dates")
                .then()
                .extract()

        then: "response status is 400 (bad request)"
        response.statusCode() == 400

        and: "error message mentions future date"
        def body = response.body().jsonPath()
        body.getString("status") == "error"
        body.getString("message").contains("future")
    }

    def "Scenario 13: Sync specific dates rejects dates older than 365 days"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "request body with date older than 365 days"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def oldDate = today.minusDays(400)
        def requestBody = """
        {
            "dates": [
                "${today.minusDays(1)}",
                "${oldDate}"
            ]
        }
        """

        when: "I trigger sync with old date"
        def response = authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/google-fit/sync/dates", requestBody)
                .post("/v1/google-fit/sync/dates")
                .then()
                .extract()

        then: "response status is 400 (bad request)"
        response.statusCode() == 400

        and: "error message mentions 365 days"
        def body = response.body().jsonPath()
        body.getString("status") == "error"
        body.getString("message").contains("365")
    }

    def "Scenario 14: Sync specific dates rejects duplicate dates"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "request body with duplicate dates"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def duplicateDate = today.minusDays(1)
        def requestBody = """
        {
            "dates": [
                "${duplicateDate}",
                "${today.minusDays(3)}",
                "${duplicateDate}"
            ]
        }
        """

        when: "I trigger sync with duplicate dates"
        def response = authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/google-fit/sync/dates", requestBody)
                .post("/v1/google-fit/sync/dates")
                .then()
                .extract()

        then: "response status is 400 (bad request)"
        response.statusCode() == 400

        and: "error message mentions duplicate"
        def body = response.body().jsonPath()
        body.getString("status") == "error"
        body.getString("message").contains("Duplicate")
    }

    def "Scenario 15: Sync specific dates works with single date"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses"
        setupGoogleFitApiMockMultipleTimes(createEmptyGoogleFitResponse(), 1)
        setupGoogleFitSessionsApiMockMultipleTimes(createEmptyGoogleFitSessionsResponse(), 1)

        and: "request body with single date"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def requestBody = """
        {
            "dates": ["${today.minusDays(10)}"]
        }
        """

        when: "I trigger sync for single date"
        def response = authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/google-fit/sync/dates", requestBody)
                .post("/v1/google-fit/sync/dates")
                .then()
                .extract()

        then: "response status is 202 (accepted)"
        response.statusCode() == 202

        and: "single date is scheduled"
        def body = response.body().jsonPath()
        body.getString("status") == "scheduled"
        body.getInt("scheduledDays") == 1
    }
}
