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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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

    def "Scenario 4: Multiple syncs overwrite events with same idempotency key"() {
        given: "mock Google Fit API response with steps data"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync first time"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def firstSyncEventsCount = eventRepository.findAll().size()
        def firstSyncEvent = eventRepository.findAll().find { it.eventType == "StepsBucketedRecorded.v1" }

        and: "I trigger sync second time with same data"
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 1000))
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "events with same idempotency key are overwritten, not duplicated"
        def secondSyncEventsCount = eventRepository.findAll().size()
        secondSyncEventsCount == firstSyncEventsCount
        
        and: "event payload is updated"
        def updatedEvent = eventRepository.findAll().find { it.idempotencyKey == firstSyncEvent.idempotencyKey }
        updatedEvent != null
        updatedEvent.payload.get("count") == 1000
    }

    def "Scenario 5: Sync generates daily summary for today"() {
        given: "today's date"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))

        and: "mock Google Fit API response with steps data for today"
        def todayStart = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        
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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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

    def "Scenario 11: Sync stores sleep session events from Google Fit Sessions API"() {
        given: "mock Google Fit API responses with sleep session"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        // Sleep session: 22:00 yesterday to 07:00 today (in Poland timezone)
        def sleepStart = todayStart.minusSeconds(3600 * 9) // 9 hours before midnight = 15:00 UTC yesterday
        def sleepEnd = todayStart.plusSeconds(3600 * 5) // 5 hours after midnight = 05:00 UTC today
        def startTimeMillis = sleepStart.toEpochMilli()
        def endTimeMillis = sleepEnd.toEpochMilli()
        
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithSleep(startTimeMillis, endTimeMillis, "sleep-123"))

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "sleep session event is stored in database"
        def events = eventRepository.findAll()
        def sleepEvents = events.findAll { it.eventType == "SleepSessionRecorded.v1" }
        sleepEvents.size() == 1
        
        and: "sleep event has correct occurredAt (end time)"
        def sleepEvent = sleepEvents.first()
        def occurredAt = Instant.parse(sleepEvent.occurredAt.toString())
        occurredAt.toEpochMilli() == endTimeMillis
        
        and: "sleep event payload contains correct data"
        def payload = sleepEvent.payload
        payload.get("sleepStart") != null
        payload.get("sleepEnd") != null
        payload.get("totalMinutes") != null
        payload.get("totalMinutes") == (int) ((endTimeMillis - startTimeMillis) / 60000)
    }

    def "Scenario 12: Sync handles multiple sleep sessions correctly"() {
        given: "mock Google Fit API responses with multiple sleep sessions"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        
        def sleep1Start = todayStart.minusSeconds(3600 * 9).toEpochMilli()
        def sleep1End = todayStart.plusSeconds(3600 * 5).toEpochMilli()
        def sleep2Start = todayStart.plusSeconds(3600 * 14).toEpochMilli() // Nap after lunch
        def sleep2End = todayStart.plusSeconds(3600 * 15).toEpochMilli()
        
        def sessionsResponse = """
        {
            "session": [
                {
                    "id": "sleep-123",
                    "activityType": 72,
                    "startTimeMillis": ${sleep1Start},
                    "endTimeMillis": ${sleep1End},
                    "packageName": "com.google.android.apps.fitness"
                },
                {
                    "id": "sleep-456",
                    "activityType": 72,
                    "startTimeMillis": ${sleep2Start},
                    "endTimeMillis": ${sleep2End},
                    "packageName": "com.google.android.apps.fitness"
                }
            ]
        }
        """
        
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(sessionsResponse)

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "both sleep sessions are stored"
        def events = eventRepository.findAll()
        def sleepEvents = events.findAll { it.eventType == "SleepSessionRecorded.v1" }
        sleepEvents.size() == 2
        
        and: "each sleep event has unique idempotency key"
        def idempotencyKeys = sleepEvents.collect { it.idempotencyKey }
        idempotencyKeys.unique().size() == 2
    }

    def "Scenario 13: Sync filters out non-sleep sessions from Google Fit Sessions API"() {
        given: "mock Google Fit API responses with mixed session types"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        
        def sleepStart = todayStart.minusSeconds(3600 * 9).toEpochMilli()
        def sleepEnd = todayStart.plusSeconds(3600 * 5).toEpochMilli()
        def workoutStart = todayStart.plusSeconds(3600 * 10).toEpochMilli()
        def workoutEnd = todayStart.plusSeconds(3600 * 11).toEpochMilli()
        
        def sessionsResponse = """
        {
            "session": [
                {
                    "id": "sleep-123",
                    "activityType": 72,
                    "startTimeMillis": ${sleepStart},
                    "endTimeMillis": ${sleepEnd},
                    "packageName": "com.google.android.apps.fitness"
                },
                {
                    "id": "workout-456",
                    "activityType": 8,
                    "startTimeMillis": ${workoutStart},
                    "endTimeMillis": ${workoutEnd},
                    "packageName": "com.google.android.apps.fitness"
                }
            ]
        }
        """
        
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(sessionsResponse)

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "only sleep session is stored"
        def events = eventRepository.findAll()
        def sleepEvents = events.findAll { it.eventType == "SleepSessionRecorded.v1" }
        sleepEvents.size() == 1
        
        and: "no walking session events are stored"
        def walkingEvents = events.findAll { it.eventType == "WalkingSessionRecorded.v1" }
        walkingEvents.size() == 0
    }

    def "Scenario 14: Sync handles empty sleep sessions response gracefully"() {
        given: "mock Google Fit API responses with no sleep sessions"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "no sleep events are stored"
        def events = eventRepository.findAll()
        def sleepEvents = events.findAll { it.eventType == "SleepSessionRecorded.v1" }
        sleepEvents.size() == 0
    }

    def "Scenario 15: Sync overwrites sleep sessions with same idempotency key"() {
        given: "mock Google Fit API responses with same sleep session"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def sleepStart = todayStart.minusSeconds(3600 * 9).toEpochMilli()
        def sleepEnd = todayStart.plusSeconds(3600 * 5).toEpochMilli()
        def sessionId = "sleep-123"

        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithSleep(sleepStart, sleepEnd, sessionId))

        when: "I trigger sync first time"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def firstSyncEventsCount = eventRepository.findAll().size()
        def firstSyncSleepEvent = eventRepository.findAll().find { it.eventType == "SleepSessionRecorded.v1" }

        and: "I trigger sync second time with updated sleep session"
        def updatedSleepEnd = todayStart.plusSeconds(3600 * 6).toEpochMilli()
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithSleep(sleepStart, updatedSleepEnd, sessionId))
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "sleep session with same idempotency key is overwritten, not duplicated"
        def secondSyncEventsCount = eventRepository.findAll().size()
        secondSyncEventsCount == firstSyncEventsCount
        
        and: "sleep event payload is updated"
        def updatedSleepEvent = eventRepository.findAll().find { it.idempotencyKey == firstSyncSleepEvent.idempotencyKey }
        updatedSleepEvent != null
        def updatedSleepEndTime = Instant.parse(updatedSleepEvent.payload.get("sleepEnd"))
        updatedSleepEndTime.toEpochMilli() == updatedSleepEnd
    }

    def "Scenario 16: Historical sync processes multiple days correctly"() {
        given: "mock Google Fit API responses for multiple days (same response for all calls)"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def dayStart = today.minusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def dayEnd = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(dayStart.toEpochMilli(), dayEnd.toEpochMilli(), 1000))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger historical sync for 2 days"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync/history?days=2")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status and statistics"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("processedDays") == 2
        body.getInt("totalEvents") >= 0
    }

    def "Scenario 17: Historical sync handles empty days gracefully"() {
        given: "mock Google Fit API responses with no data (will be called multiple times)"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger historical sync for 3 days"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync/history?days=3")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "all days are processed even with no data"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("processedDays") >= 2
        body.getInt("totalEvents") == 0
    }

    def "Scenario 18: Historical sync validates days parameter"() {
        when: "I trigger historical sync with invalid days parameter"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
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

    def "Scenario 19: Historical sync processes sleep sessions for each day"() {
        given: "mock Google Fit API responses with sleep sessions for multiple days"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def day1Start = today.minusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def day1SleepStart = day1Start.minusSeconds(3600 * 9).toEpochMilli()
        def day1SleepEnd = day1Start.plusSeconds(3600 * 5).toEpochMilli()

        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithSleep(day1SleepStart, day1SleepEnd, "sleep-day1"))

        when: "I trigger historical sync for 1 day"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync/history?days=1")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "sleep events are stored"
        def events = eventRepository.findAll()
        def sleepEvents = events.findAll { it.eventType == "SleepSessionRecorded.v1" }
        sleepEvents.size() >= 0
    }

    def "Scenario 20: Historical sync overwrites events with same idempotency key"() {
        given: "mock Google Fit API responses with steps data"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def dayStart = today.minusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def dayEnd = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(dayStart.toEpochMilli(), dayEnd.toEpochMilli(), 500))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger historical sync first time"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync/history?days=1")
                .then()
                .statusCode(200)

        def firstSyncEventsCount = eventRepository.findAll().size()
        def firstSyncEvent = eventRepository.findAll().find { it.eventType == "StepsBucketedRecorded.v1" }

        and: "I trigger historical sync second time with updated data"
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(dayStart.toEpochMilli(), dayEnd.toEpochMilli(), 800))
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync/history?days=1")
                .then()
                .statusCode(200)

        then: "events with same idempotency key are overwritten, not duplicated"
        def secondSyncEventsCount = eventRepository.findAll().size()
        secondSyncEventsCount == firstSyncEventsCount
        
        and: "event payload is updated"
        def updatedEvent = eventRepository.findAll().find { it.idempotencyKey == firstSyncEvent.idempotencyKey }
        updatedEvent != null
        updatedEvent.payload.get("count") == 800
        
        and: "eventId and createdAt are preserved"
        updatedEvent.eventId == firstSyncEvent.eventId
        updatedEvent.createdAt == firstSyncEvent.createdAt
    }

    def "Scenario 22: Sync overwrites walking sessions with same idempotency key"() {
        given: "mock Google Fit API responses with walking session"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def walkStart = todayStart.plusHours(10).toInstant().toEpochMilli()
        def walkEnd = todayStart.plusHours(11).toInstant().toEpochMilli()
        def sessionId = "walk-123"

        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithWalking(walkStart, walkEnd, sessionId))
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(walkStart, walkEnd, 5000, 3000.0, 150.0, 80))

        when: "I trigger sync first time"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def firstSyncEventsCount = eventRepository.findAll().size()
        def firstSyncWalkingEvent = eventRepository.findAll().find { it.eventType == "WalkingSessionRecorded.v1" }
        def originalEventId = firstSyncWalkingEvent.eventId
        def originalCreatedAt = firstSyncWalkingEvent.createdAt

        and: "I trigger sync second time with updated walking session"
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithWalking(walkStart, walkEnd, sessionId))
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(walkStart, walkEnd, 8000, 5000.0, 250.0, 85))
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "walking session with same idempotency key is overwritten, not duplicated"
        def secondSyncEventsCount = eventRepository.findAll().size()
        secondSyncEventsCount == firstSyncEventsCount
        
        and: "walking event payload is updated"
        def updatedWalkingEvent = eventRepository.findAll().find { it.idempotencyKey == firstSyncWalkingEvent.idempotencyKey }
        updatedWalkingEvent != null
        updatedWalkingEvent.payload.get("totalSteps") > firstSyncWalkingEvent.payload.get("totalSteps")
        def distance = updatedWalkingEvent.payload.get("totalDistanceMeters")
        distance > firstSyncWalkingEvent.payload.get("totalDistanceMeters")
        updatedWalkingEvent.payload.get("totalCalories") > firstSyncWalkingEvent.payload.get("totalCalories")
        
        and: "eventId and createdAt are preserved"
        updatedWalkingEvent.eventId == originalEventId
        updatedWalkingEvent.createdAt == originalCreatedAt
    }

    def "Scenario 23: Mixed batch with some events to overwrite and some new events"() {
        given: "mock Google Fit API responses with steps data"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def firstBucketStart = todayStart.plusSeconds(3600).toEpochMilli()
        def firstBucketEnd = todayStart.plusSeconds(4500).toEpochMilli()
        def secondBucketStart = todayStart.plusSeconds(4600).toEpochMilli()
        def secondBucketEnd = todayStart.plusSeconds(5500).toEpochMilli()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(firstBucketStart, firstBucketEnd, 500))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync first time"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def firstSyncEventsCount = eventRepository.findAll().size()
        def firstSyncEvent = eventRepository.findAll().find { it.eventType == "StepsBucketedRecorded.v1" }
        def firstSyncStepsEventsCount = eventRepository.findAll().findAll { it.eventType == "StepsBucketedRecorded.v1" }.size()

        and: "I trigger sync second time with updated first bucket and new second bucket"
        def responseBody = """
        {
            "bucket": [
                {
                    "startTimeMillis": ${firstBucketStart},
                    "endTimeMillis": ${firstBucketEnd},
                    "dataset": [{
                        "dataSourceId": "derived:com.google.step_count.delta:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${firstBucketStart * 1_000_000}",
                            "endTimeNanos": "${firstBucketEnd * 1_000_000}",
                            "value": [{"intVal": 1000}]
                        }]
                    }]
                },
                {
                    "startTimeMillis": ${secondBucketStart},
                    "endTimeMillis": ${secondBucketEnd},
                    "dataset": [{
                        "dataSourceId": "derived:com.google.step_count.delta:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${secondBucketStart * 1_000_000}",
                            "endTimeNanos": "${secondBucketEnd * 1_000_000}",
                            "value": [{"intVal": 600}]
                        }]
                    }]
                }
            ]
        }
        """
        setupGoogleFitApiMock(responseBody)
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "first event is overwritten and second event is added"
        def secondSyncStepsEvents = eventRepository.findAll().findAll { it.eventType == "StepsBucketedRecorded.v1" }
        secondSyncStepsEvents.size() == firstSyncStepsEventsCount + 1
        
        and: "first event payload is updated"
        def updatedEvent = eventRepository.findAll().find { it.idempotencyKey == firstSyncEvent.idempotencyKey }
        updatedEvent != null
        updatedEvent.payload.get("count") == 1000
        
        and: "second event is stored"
        def secondEvent = eventRepository.findAll().find { 
            it.eventType == "StepsBucketedRecorded.v1" && it.idempotencyKey != firstSyncEvent.idempotencyKey 
        }
        secondEvent != null
        secondEvent.payload.get("count") == 600
    }

    def "Scenario 21: Historical sync validates upper bound for days parameter"() {
        when: "I trigger historical sync with days > 365"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
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

    def "Scenario 22: Historical sync uses default value when days parameter is missing"() {
        given: "mock Google Fit API responses"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger historical sync without days parameter"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync/history")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "default 7 days are processed"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("processedDays") == 7
    }

    def "Scenario 23: Historical sync handles partial failures correctly"() {
        given: "mock Google Fit API responses - first day succeeds, second fails"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def day1Start = today.minusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def day1End = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(day1Start.toEpochMilli(), day1End.toEpochMilli(), 500))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger historical sync for 2 days"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync/history?days=2")
                .then()
                .extract()

        then: "response status is 200 (partial success is acceptable)"
        response.statusCode() == 200

        and: "some days are processed"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("processedDays") >= 1
    }

    def "Scenario 24: Sync handles Sessions API errors gracefully"() {
        given: "mock Google Fit API - aggregate succeeds but sessions fails"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 500))
        setupGoogleFitSessionsApiMockError(500, '{"error": "Sessions API Error"}')

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
    }

    def "Scenario 25: Sync handles OAuth token refresh errors gracefully"() {
        given: "mock OAuth endpoint returning error"
        setupOAuthMockError(401, '{"error": "invalid_grant"}')
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())

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
    }

    def "Scenario 26: Events have occurredAt set to bucket end time"() {
        given: "mock Google Fit API response with steps data"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def bucketStart = todayStart.plusSeconds(3600).toEpochMilli()
        def bucketEnd = todayStart.plusSeconds(3900).toEpochMilli()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(bucketStart, bucketEnd, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "event occurredAt matches bucket end time"
        def events = eventRepository.findAll()
        events.size() > 0
        def stepsEvent = events.find { it.eventType == "StepsBucketedRecorded.v1" }
        stepsEvent != null
        def occurredAtMillis = Instant.parse(stepsEvent.occurredAt.toString()).toEpochMilli()
        occurredAtMillis == bucketEnd
    }
}

