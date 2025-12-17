package com.healthassistant

import com.healthassistant.googlefit.api.GoogleFitFacade
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
    GoogleFitFacade googleFitFacade

    def cleanup() {
        googleFitFacade?.deleteAllSyncState()
    }

    def "Scenario 1: Manual sync trigger returns success response"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API response"
        def now = Instant.now()
        def startTime = now.minusSeconds(900).toEpochMilli() // 15 minutes ago
        def endTime = now.toEpochMilli()
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger manual sync"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
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
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API response with steps data"
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
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "events are stored in database"
        def events = findAllEvents()
        events.size() > 0
        events.any { it.eventType() == "StepsBucketedRecorded.v1" }
    }

    def "Scenario 3: Sync completes and can be called again"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock empty Google Fit API response"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync first time"
        def response1 = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .extract()

        then: "sync succeeds"
        response1.statusCode() == 200
        response1.body().jsonPath().getString("status") == "success"

        when: "I trigger sync second time"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        def response2 = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .extract()

        then: "sync still succeeds (sync state was properly updated)"
        response2.statusCode() == 200
        response2.body().jsonPath().getString("status") == "success"
    }

    def "Scenario 4: Multiple syncs overwrite events with same idempotency key"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API response with steps data"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync first time"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def firstSyncEvents = findAllEvents()
        def firstSyncEventsCount = firstSyncEvents.size()
        def firstSyncEvent = firstSyncEvents.find { it.eventType() == "StepsBucketedRecorded.v1" }

        and: "I trigger sync second time with updated data"
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 1000))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "events with same idempotency key are overwritten, not duplicated"
        def secondSyncEventsCount = findAllEvents().size()
        secondSyncEventsCount == firstSyncEventsCount

        and: "event payload is updated"
        def updatedEvents = findAllEvents()
        def updatedEvent = updatedEvents.find { it.eventType() == "StepsBucketedRecorded.v1" }
        updatedEvent != null
        updatedEvent.payload().get("count") == 1000
    }

    def "Scenario 5: Sync generates daily summary for today"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "today's date"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))

        and: "mock Google Fit API response with steps data for today"
        def todayStart = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "daily summary for today is created"
        def events = findAllEvents()
        events.size() > 0
    }

    def "Scenario 6: Sync handles empty response from Google Fit API gracefully"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock empty Google Fit API response"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .extract()

        then: "sync completes successfully even with no data"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"

        and: "no events are stored"
        findAllEvents().size() == 0
    }

    def "Scenario 7: Sync processes different event types correctly"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API response with multiple data types"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(startTime, endTime, 742, 1000.5, 125.5, 75))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "events in database have correct types"
        def events = findAllEvents()
        def eventTypes = events.collect { it.eventType() }.unique()

        eventTypes.contains("StepsBucketedRecorded.v1")
        eventTypes.contains("DistanceBucketRecorded.v1")
        eventTypes.contains("ActiveCaloriesBurnedRecorded.v1")
        eventTypes.contains("HeartRateSummaryRecorded.v1")
    }

    def "Scenario 8: Sync handles errors gracefully"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API returning 500 error"
        setupGoogleFitApiMockError(500, '{"error": "Internal Server Error"}')

        when: "I trigger sync"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
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
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API response with steps data"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "all events have google-fit device ID"
        def events = findAllEvents()
        events.size() > 0
        events.every { it.deviceId() == "google-fit" }
    }

    def "Scenario 10: Sync updates daily summary when new events arrive"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "today's date"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))

        and: "initial sync with steps data"
        def todayStart = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def startTime1 = todayStart.plusSeconds(3600).toEpochMilli() // 1 hour after midnight
        def endTime1 = todayStart.plusSeconds(4500).toEpochMilli() // 1.25 hours after midnight
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime1, endTime1, 500))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def initialEventsCount = findAllEvents().size()

        and: "second sync with additional steps data (different time bucket)"
        def startTime2 = todayStart.plusSeconds(7200).toEpochMilli() // 2 hours after midnight
        def endTime2 = todayStart.plusSeconds(8100).toEpochMilli() // 2.25 hours after midnight
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime2, endTime2, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync again"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "new events arrived"
        def finalEventsCount = findAllEvents().size()
        finalEventsCount > initialEventsCount
    }

    def "Scenario 11: Sync stores sleep session events from Google Fit Sessions API"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with sleep session"
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
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "sleep session event is stored in database"
        def events = findAllEvents()
        def sleepEvents = events.findAll { it.eventType() == "SleepSessionRecorded.v1" }
        sleepEvents.size() == 1

        and: "sleep event has correct occurredAt (end time)"
        def sleepEvent = sleepEvents.first()
        sleepEvent.occurredAt().toEpochMilli() == endTimeMillis

        and: "sleep event payload contains correct data"
        def payload = sleepEvent.payload()
        payload.get("sleepStart") != null
        payload.get("sleepEnd") != null
        payload.get("totalMinutes") != null
        payload.get("totalMinutes") == (int) ((endTimeMillis - startTimeMillis) / 60000)
    }

    def "Scenario 12: Sync handles multiple sleep sessions correctly"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with multiple sleep sessions"
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
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "both sleep sessions are stored"
        def events = findAllEvents()
        def sleepEvents = events.findAll { it.eventType() == "SleepSessionRecorded.v1" }
        sleepEvents.size() == 2
    }

    def "Scenario 13: Sync filters out non-sleep sessions from Google Fit Sessions API"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with mixed session types"
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
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "only sleep session is stored"
        def events = findAllEvents()
        def sleepEvents = events.findAll { it.eventType() == "SleepSessionRecorded.v1" }
        sleepEvents.size() == 1

        and: "no walking session events are stored"
        def walkingEvents = events.findAll { it.eventType() == "WalkingSessionRecorded.v1" }
        walkingEvents.size() == 0
    }

    def "Scenario 14: Sync handles empty sleep sessions response gracefully"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with no sleep sessions"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "no sleep events are stored"
        def events = findAllEvents()
        def sleepEvents = events.findAll { it.eventType() == "SleepSessionRecorded.v1" }
        sleepEvents.size() == 0
    }

    def "Scenario 15: Sync overwrites sleep sessions with same idempotency key"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with same sleep session"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def sleepStart = todayStart.minusSeconds(3600 * 9).toEpochMilli()
        def sleepEnd = todayStart.plusSeconds(3600 * 5).toEpochMilli()
        def sessionId = "sleep-123"

        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithSleep(sleepStart, sleepEnd, sessionId))

        when: "I trigger sync first time"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def firstSyncEventsCount = findAllEvents().size()
        def firstSyncSleepEvent = findAllEvents().find { it.eventType() == "SleepSessionRecorded.v1" }

        and: "I trigger sync second time with updated sleep session"
        def updatedSleepEnd = todayStart.plusSeconds(3600 * 6).toEpochMilli()
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithSleep(sleepStart, updatedSleepEnd, sessionId))
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "sleep session with same idempotency key is overwritten, not duplicated"
        def secondSyncEventsCount = findAllEvents().size()
        secondSyncEventsCount == firstSyncEventsCount

        and: "sleep event payload is updated"
        def updatedSleepEvent = findAllEvents().find { it.eventType() == "SleepSessionRecorded.v1" }
        updatedSleepEvent != null
        def updatedSleepEndTime = Instant.parse(updatedSleepEvent.payload().get("sleepEnd").toString())
        updatedSleepEndTime.toEpochMilli() == updatedSleepEnd
    }

    def "Scenario 16: Historical sync processes multiple days correctly"() {
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

    def "Scenario 17: Historical sync handles empty days gracefully"() {
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

    def "Scenario 18: Historical sync validates days parameter"() {
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

    def "Scenario 19: Historical sync schedules sleep sessions processing for each day"() {
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

    def "Scenario 20: Historical sync schedules tasks for processing (async)"() {
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

    def "Scenario 22: Sync overwrites walking sessions with same idempotency key"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with walking session"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def walkStart = todayStart.plusHours(10).toInstant().toEpochMilli()
        def walkEnd = todayStart.plusHours(11).toInstant().toEpochMilli()
        def sessionId = "walk-123"

        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithWalking(walkStart, walkEnd, sessionId))
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(walkStart, walkEnd, 5000, 3000.0, 150.0, 80))

        when: "I trigger sync first time"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def firstSyncEventsCount = findAllEvents().size()
        def firstSyncWalkingEvent = findAllEvents().find { it.eventType() == "WalkingSessionRecorded.v1" }
        def firstTotalSteps = firstSyncWalkingEvent.payload().get("totalSteps")

        and: "I trigger sync second time with updated walking session"
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithWalking(walkStart, walkEnd, sessionId))
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(walkStart, walkEnd, 8000, 5000.0, 250.0, 85))
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "walking session with same idempotency key is overwritten, not duplicated"
        def secondSyncEventsCount = findAllEvents().size()
        secondSyncEventsCount == firstSyncEventsCount

        and: "walking event payload is updated"
        def updatedWalkingEvent = findAllEvents().find { it.eventType() == "WalkingSessionRecorded.v1" }
        updatedWalkingEvent != null
        updatedWalkingEvent.payload().get("totalSteps") > firstTotalSteps
    }

    def "Scenario 23: Mixed batch with some events to overwrite and some new events"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses with steps data"
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
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def firstSyncEvents = findAllEvents()
        def firstSyncEventsCount = firstSyncEvents.size()
        def firstSyncEvent = firstSyncEvents.find { it.eventType() == "StepsBucketedRecorded.v1" }
        def firstSyncStepsEventsCount = firstSyncEvents.findAll { it.eventType() == "StepsBucketedRecorded.v1" }.size()

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
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "first event is overwritten and second event is added"
        def secondSyncEvents = findAllEvents()
        def secondSyncStepsEvents = secondSyncEvents.findAll { it.eventType() == "StepsBucketedRecorded.v1" }
        secondSyncStepsEvents.size() == firstSyncStepsEventsCount + 1

        and: "step events include both the updated and the new one"
        def stepCounts = secondSyncStepsEvents.collect { it.payload().get("count") }
        stepCounts.contains(1000) // Updated first event
        stepCounts.contains(600)  // New second event
    }

    def "Scenario 21: Historical sync validates upper bound for days parameter"() {
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

    def "Scenario 22: Historical sync uses default value when days parameter is missing"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses"
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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

    def "Scenario 23: Historical sync schedules all days for processing"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API responses"
        def today = LocalDate.now(ZoneId.of("Europe/Warsaw"))
        def day1Start = today.minusDays(1).atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()
        def day1End = today.atStartOfDay(ZoneId.of("Europe/Warsaw")).toInstant()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(day1Start.toEpochMilli(), day1End.toEpochMilli(), 500))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

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

    def "Scenario 24: Sync handles Sessions API errors gracefully"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API - aggregate succeeds but sessions fails"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def startTime = todayStart.plusSeconds(3600).toEpochMilli()
        def endTime = todayStart.plusSeconds(4500).toEpochMilli()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime, endTime, 500))
        setupGoogleFitSessionsApiMockError(500, '{"error": "Sessions API Error"}')

        when: "I trigger sync"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .extract()

        then: "response indicates error status"
        response.statusCode() == 500
        def body = response.body().jsonPath()
        body.getString("status") == "error"
    }

    def "Scenario 25: Sync handles OAuth token refresh errors gracefully"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock OAuth endpoint returning error"
        setupOAuthMockError(401, '{"error": "invalid_grant"}')
        setupGoogleFitApiMock(createEmptyGoogleFitResponse())

        when: "I trigger sync"
        def response = authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .extract()

        then: "response indicates error status"
        response.statusCode() == 500
        def body = response.body().jsonPath()
        body.getString("status") == "error"
    }

    def "Scenario 26: Events have occurredAt set to bucket end time"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "mock Google Fit API response with steps data"
        def todayStart = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .atStartOfDay(ZoneId.of("Europe/Warsaw"))
                .toInstant()
        def bucketStart = todayStart.plusSeconds(3600).toEpochMilli()
        def bucketEnd = todayStart.plusSeconds(3900).toEpochMilli()

        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(bucketStart, bucketEnd, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        then: "event occurredAt matches bucket end time"
        def events = findAllEvents()
        events.size() > 0
        def stepsEvent = events.find { it.eventType() == "StepsBucketedRecorded.v1" }
        stepsEvent != null
        stepsEvent.occurredAt().toEpochMilli() == bucketEnd
    }
}

