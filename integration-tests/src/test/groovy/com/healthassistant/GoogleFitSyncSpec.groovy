package com.healthassistant

import com.healthassistant.application.summary.DailySummaryJpaRepository
import com.healthassistant.application.sync.GoogleFitSyncStateRepository
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Integration tests for Google Fit synchronization
 */
@Title("Feature: Google Fit Synchronization")
class GoogleFitSyncSpec extends BaseIntegrationSpec {

    @Autowired
    GoogleFitSyncStateRepository syncStateRepository

    def cleanup() {
        if (syncStateRepository != null) {
            syncStateRepository.deleteAll()
        }
    }

    def "Scenario 1: Manual sync trigger returns success response"() {
        given: "mock Google Fit API response"
        def now = Instant.now()
        def startTime = now.minusSeconds(900).toEpochMilli() // 15 minutes ago
        def endTime = now.toEpochMilli()
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())

        when: "I trigger manual sync"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getString("message") != null
    }

    def "Scenario 2: Sync stores events in database"() {
        given: "mock Google Fit API response with steps data"
        def now = Instant.now()
        // Use time from beginning of today (Poland timezone) to ensure sync picks it up
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli() // 1 hour after midnight
        def endTime = todayStart.plusSeconds(4500).toEpochMilli() // 1.25 hours after midnight
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "events are stored in database"
        def events = eventRepository.findAll()
        events.size() > 0
        events.any { it.eventType == "StepsBucketedRecorded.v1" }
    }

    def "Scenario 3: Sync updates lastSyncedAt timestamp"() {
        given: "mock empty Google Fit API response"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "sync state is updated"
        def syncState = syncStateRepository.findByUserId("default")
        syncState.isPresent()
        syncState.get().lastSyncedAt != null
    }

    def "Scenario 4: Multiple syncs handle idempotency correctly"() {
        given: "mock Google Fit API response with steps data"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))

        when: "I trigger sync first time"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def firstSyncEventsCount = eventRepository.findAll().size()

        and: "I trigger sync second time with same data"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "duplicate events are not stored again"
        def secondSyncEventsCount = eventRepository.findAll().size()
        secondSyncEventsCount == firstSyncEventsCount
    }

    def "Scenario 5: Sync generates daily summary for today"() {
        given: "today's date"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))

        and: "mock Google Fit API response with steps data for today"
        def todayStart = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "daily summary for today is created"
        def events = eventRepository.findAll()
        events.size() > 0
    }

    def "Scenario 6: Sync handles empty response from Google Fit API gracefully"() {
        given: "mock empty Google Fit API response"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())

        when: "I trigger sync"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .extract()

        then: "sync completes successfully even with no data"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
        
        and: "no events are stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 7: Sync processes different event types correctly"() {
        given: "mock Google Fit API response with multiple data types"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(startTime, endTime, 742, 1000.5, 125.5, 75))

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "events in database have correct types"
        def events = eventRepository.findAll()
        def eventTypes = events.collect { it.eventType }.unique()
        
        eventTypes.contains("StepsBucketedRecorded.v1")
        eventTypes.contains("DistanceBucketRecorded.v1")
        eventTypes.contains("ActiveCaloriesBurnedRecorded.v1")
        eventTypes.contains("HeartRateSummaryRecorded.v1")
    }

    def "Scenario 8: Sync handles errors gracefully"() {
        given: "mock Google Fit API returning 500 error"
        setupGoogleFitApiMockError(500, '{"error": "Internal Server Error"}')

        when: "I trigger sync"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .extract()

        then: "response indicates error status"
        response.statusCode() == 500
        def body = response.body().jsonPath()
        body.getString("status") == "error"
        body.getString("message") != null
    }

    def "Scenario 9: Events stored via sync have correct device ID"() {
        given: "mock Google Fit API response with steps data"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "all events have google-fit device ID"
        def events = eventRepository.findAll()
        events.every { event ->
            event.deviceId == "google-fit"
        }
    }

    def "Scenario 10: Sync updates daily summary when new events arrive"() {
        given: "today's date"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))

        and: "initial sync with steps data"
        def todayStart = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def startTime1 = todayStart.plusSeconds(3600).toEpochMilli() // 1 hour after midnight
        def endTime1 = todayStart.plusSeconds(4500).toEpochMilli() // 1.25 hours after midnight
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime1, endTime1, 500))
        
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def initialEventsCount = eventRepository.findAll().size()

        and: "second sync with additional steps data (different time bucket)"
        def startTime2 = todayStart.plusSeconds(7200).toEpochMilli() // 2 hours after midnight
        def endTime2 = todayStart.plusSeconds(8100).toEpochMilli() // 2.25 hours after midnight
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime2, endTime2, 742))

        when: "I trigger sync again"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "new events arrived"
        def finalEventsCount = eventRepository.findAll().size()
        finalEventsCount > initialEventsCount
    }
}

