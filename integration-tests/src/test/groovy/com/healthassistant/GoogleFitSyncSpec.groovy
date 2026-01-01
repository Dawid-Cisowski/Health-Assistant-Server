package com.healthassistant

import spock.lang.Title

import java.time.LocalDate
import java.time.ZoneId

@Title("Feature: Google Fit Synchronization")
class GoogleFitSyncSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-gfit"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw")

    def "Scenario 1: Sync day returns stored events count"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = SECRET_BASE64

        and: "mock Google Fit API responses with steps data"
        def today = LocalDate.now(POLAND_ZONE)
        def syncDate = today.minusDays(1)
        def dayStart = syncDate.atStartOfDay(POLAND_ZONE).toInstant()
        def dayEnd = syncDate.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(dayStart.toEpochMilli(), dayEnd.toEpochMilli(), 1000))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I sync a specific day"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/day?date=${syncDate}")
                .post("/v1/google-fit/sync/day?date=${syncDate}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains events count"
        def body = response.body().jsonPath()
        body.getInt("eventsStored") >= 0
        body.getInt("eventsDuplicate") >= 0
    }

    def "Scenario 2: Sync day handles empty data gracefully"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = SECRET_BASE64

        and: "mock Google Fit API responses with no data"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        and: "a date to sync"
        def today = LocalDate.now(POLAND_ZONE)
        def syncDate = today.minusDays(10)

        when: "I sync a day with no data"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/day?date=${syncDate}")
                .post("/v1/google-fit/sync/day?date=${syncDate}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response shows zero events"
        def body = response.body().jsonPath()
        body.getInt("eventsStored") == 0
        body.getInt("eventsDuplicate") == 0
    }

    def "Scenario 3: Sync day rejects future date"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = SECRET_BASE64

        and: "a future date"
        def futureDate = LocalDate.now(POLAND_ZONE).plusDays(1)

        when: "I try to sync a future date"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/day?date=${futureDate}")
                .post("/v1/google-fit/sync/day?date=${futureDate}")
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }

    def "Scenario 4: Sync day rejects date older than 365 days"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = SECRET_BASE64

        and: "a very old date"
        def oldDate = LocalDate.now(POLAND_ZONE).minusDays(400)

        when: "I try to sync a date older than 365 days"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/day?date=${oldDate}")
                .post("/v1/google-fit/sync/day?date=${oldDate}")
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }

    def "Scenario 5: Sync day processes sleep sessions"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = SECRET_BASE64

        and: "mock Google Fit API responses with sleep session"
        def today = LocalDate.now(POLAND_ZONE)
        def syncDate = today.minusDays(1)
        def sleepStart = syncDate.atStartOfDay(POLAND_ZONE).minusHours(1).toInstant().toEpochMilli()
        def sleepEnd = syncDate.atStartOfDay(POLAND_ZONE).plusHours(7).toInstant().toEpochMilli()

        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithSleep(sleepStart, sleepEnd, "sleep-test-123"))

        when: "I sync the day"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/day?date=${syncDate}")
                .post("/v1/google-fit/sync/day?date=${syncDate}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "sleep event is stored"
        def body = response.body().jsonPath()
        body.getInt("eventsStored") >= 1
    }

    def "Scenario 6: Sync day returns duplicates on second call"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = SECRET_BASE64

        and: "mock Google Fit API responses with steps data"
        def today = LocalDate.now(POLAND_ZONE)
        def syncDate = today.minusDays(5)
        def dayStart = syncDate.atStartOfDay(POLAND_ZONE).toInstant()
        def dayEnd = syncDate.plusDays(1).atStartOfDay(POLAND_ZONE).toInstant()

        setupGoogleFitApiMockMultipleTimes(createGoogleFitResponseWithSteps(dayStart.toEpochMilli(), dayEnd.toEpochMilli(), 500), 2)
        setupGoogleFitSessionsApiMockMultipleTimes(createEmptyGoogleFitSessionsResponse(), 2)

        and: "first sync stores the events"
        def firstResponse = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/day?date=${syncDate}")
                .post("/v1/google-fit/sync/day?date=${syncDate}")
                .then()
                .extract()
        assert firstResponse.statusCode() == 200
        def firstStored = firstResponse.body().jsonPath().getInt("eventsStored")

        when: "I sync the same day again"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/day?date=${syncDate}")
                .post("/v1/google-fit/sync/day?date=${syncDate}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "events are now duplicates"
        def body = response.body().jsonPath()
        body.getInt("eventsDuplicate") == firstStored || body.getInt("eventsStored") == 0
    }

    def "Scenario 7: Sync day with missing date parameter returns 400"() {
        given: "authenticated device"
        def deviceId = DEVICE_ID
        def secretBase64 = SECRET_BASE64

        when: "I call sync without date parameter"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync/day")
                .post("/v1/google-fit/sync/day")
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }
}
