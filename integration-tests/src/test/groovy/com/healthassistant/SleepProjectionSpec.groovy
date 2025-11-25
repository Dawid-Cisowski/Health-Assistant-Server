package com.healthassistant

import com.healthassistant.sleep.SleepDailyProjectionJpaRepository
import com.healthassistant.sleep.SleepSessionProjectionJpaRepository
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Sleep Projections
 */
@Title("Feature: Sleep Projections and Query API")
class SleepProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    @Autowired
    SleepDailyProjectionJpaRepository dailyProjectionRepository

    @Autowired
    SleepSessionProjectionJpaRepository sessionProjectionRepository

    def setup() {
        // Clean projection tables (in addition to base cleanup)
        sessionProjectionRepository?.deleteAll()
        dailyProjectionRepository?.deleteAll()
    }

    def "Scenario 1: SleepSession event creates session and daily projections"() {
        given: "a sleep session event"
        def date = "2025-11-20"
        // Sleep from 22:30 to 06:30 next day (8 hours)
        def sleepStart = "2025-11-19T21:30:00Z"  // 22:30 Warsaw time (CET = UTC+1)
        def sleepEnd = "2025-11-20T05:30:00Z"    // 06:30 Warsaw time
        def totalMinutes = 480
        def request = """
        {
            "events": [{
                "idempotencyKey": "test-device|sleep|${sleepStart}",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "${sleepEnd}",
                "payload": {
                    "sleepId": "sleep-2025-11-20",
                    "sleepStart": "${sleepStart}",
                    "sleepEnd": "${sleepEnd}",
                    "totalMinutes": ${totalMinutes},
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit the sleep event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "session projection is created"
        def sessions = sessionProjectionRepository.findByDateOrderBySessionNumberAsc(LocalDate.parse(date))
        sessions.size() == 1
        sessions[0].sessionNumber == 1
        sessions[0].durationMinutes == 480
        sessions[0].sleepStart.toString() == sleepStart
        sessions[0].sleepEnd.toString() == sleepEnd

        and: "daily projection is created with totals"
        def dailyData = dailyProjectionRepository.findByDate(LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalSleepMinutes == 480
        dailyData.get().sleepCount == 1
        dailyData.get().longestSessionMinutes == 480
        dailyData.get().shortestSessionMinutes == 480
        dailyData.get().averageSessionMinutes == 480
    }

    def "Scenario 2: Multiple sleep sessions in same day (naps)"() {
        given: "main sleep and a nap on the same day"
        def date = "2025-11-21"
        // Main sleep: 22:00 to 06:00 (8 hours = 480 min)
        def mainSleepStart = "2025-11-20T21:00:00Z"
        def mainSleepEnd = "2025-11-21T05:00:00Z"
        // Afternoon nap: 14:00 to 15:00 (1 hour = 60 min)
        def napStart = "2025-11-21T13:00:00Z"
        def napEnd = "2025-11-21T14:00:00Z"

        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|sleep|main",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "${mainSleepEnd}",
                    "payload": {
                        "sleepId": "sleep-main-2025-11-21",
                        "sleepStart": "${mainSleepStart}",
                        "sleepEnd": "${mainSleepEnd}",
                        "totalMinutes": 480,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|sleep|nap",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "${napEnd}",
                    "payload": {
                        "sleepId": "sleep-nap-2025-11-21",
                        "sleepStart": "${napStart}",
                        "sleepEnd": "${napEnd}",
                        "totalMinutes": 60,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit both sleep events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "two session projections are created"
        def sessions = sessionProjectionRepository.findByDateOrderBySessionNumberAsc(LocalDate.parse(date))
        sessions.size() == 2
        sessions[0].sessionNumber == 1
        sessions[0].durationMinutes == 480
        sessions[1].sessionNumber == 2
        sessions[1].durationMinutes == 60

        and: "daily projection aggregates both sessions"
        def dailyData = dailyProjectionRepository.findByDate(LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalSleepMinutes == 540  // 480 + 60
        dailyData.get().sleepCount == 2
        dailyData.get().longestSessionMinutes == 480
        dailyData.get().shortestSessionMinutes == 60
        dailyData.get().averageSessionMinutes == 270  // 540 / 2
    }

    def "Scenario 3: Query API returns daily detail with all sessions"() {
        given: "sleep data with multiple sessions"
        def date = "2025-11-22"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|sleep|night-2025-11-22",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-22T06:00:00Z",
                    "payload": {
                        "sleepId": "night-2025-11-22",
                        "sleepStart": "2025-11-21T22:00:00Z",
                        "sleepEnd": "2025-11-22T06:00:00Z",
                        "totalMinutes": 420,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|sleep|nap-2025-11-22",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-22T15:00:00Z",
                    "payload": {
                        "sleepId": "nap-2025-11-22",
                        "sleepStart": "2025-11-22T14:00:00Z",
                        "sleepEnd": "2025-11-22T15:00:00Z",
                        "totalMinutes": 60,
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

        when: "I query daily detail"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/sleep/daily/${date}")
                .get("/v1/sleep/daily/${date}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response contains both sessions"
        response.getList("sessions").size() == 2

        and: "session details are correct"
        def sessions = response.getList("sessions")
        sessions[0].sessionNumber == 1
        sessions[0].durationMinutes == 420
        sessions[1].sessionNumber == 2
        sessions[1].durationMinutes == 60

        and: "daily totals are correct"
        response.getInt("totalSleepMinutes") == 480
        response.getInt("sleepCount") == 2
        response.getInt("longestSessionMinutes") == 420
        response.getInt("shortestSessionMinutes") == 60
        response.getInt("averageSessionMinutes") == 240
    }

    def "Scenario 4: Query API returns range summary with daily stats"() {
        given: "sleep data across multiple days"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|sleep|2025-11-18",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-18T06:00:00Z",
                    "payload": {
                        "sleepId": "sleep-2025-11-18",
                        "sleepStart": "2025-11-17T22:00:00Z",
                        "sleepEnd": "2025-11-18T06:00:00Z",
                        "totalMinutes": 480,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|sleep|2025-11-19",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-19T07:00:00Z",
                    "payload": {
                        "sleepId": "sleep-2025-11-19",
                        "sleepStart": "2025-11-18T23:00:00Z",
                        "sleepEnd": "2025-11-19T07:00:00Z",
                        "totalMinutes": 420,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|sleep|2025-11-20",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-20T08:00:00Z",
                    "payload": {
                        "sleepId": "sleep-2025-11-20",
                        "sleepStart": "2025-11-20T00:00:00Z",
                        "sleepEnd": "2025-11-20T08:00:00Z",
                        "totalMinutes": 540,
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
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/sleep/range?startDate=2025-11-18&endDate=2025-11-20")
                .get("/v1/sleep/range?startDate=2025-11-18&endDate=2025-11-20")
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
        dailyStats[0].totalSleepMinutes == 480
        dailyStats[1].date == "2025-11-19"
        dailyStats[1].totalSleepMinutes == 420
        dailyStats[2].date == "2025-11-20"
        dailyStats[2].totalSleepMinutes == 540

        and: "totals are correct"
        response.getInt("totalSleepMinutes") == 1440  // 480 + 420 + 540
        response.getInt("averageSleepMinutes") == 480  // 1440 / 3
        response.getInt("daysWithData") == 3
    }

    def "Scenario 5: Range summary shows day with most and least sleep"() {
        given: "sleep data with varying amounts"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|sleep|2025-11-23",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-23T06:00:00Z",
                    "payload": {
                        "sleepId": "sleep-2025-11-23",
                        "sleepStart": "2025-11-22T22:00:00Z",
                        "sleepEnd": "2025-11-23T06:00:00Z",
                        "totalMinutes": 300,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|sleep|2025-11-24",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-24T08:00:00Z",
                    "payload": {
                        "sleepId": "sleep-2025-11-24",
                        "sleepStart": "2025-11-23T22:00:00Z",
                        "sleepEnd": "2025-11-24T08:00:00Z",
                        "totalMinutes": 600,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|sleep|2025-11-25",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-25T07:00:00Z",
                    "payload": {
                        "sleepId": "sleep-2025-11-25",
                        "sleepStart": "2025-11-24T23:00:00Z",
                        "sleepEnd": "2025-11-25T07:00:00Z",
                        "totalMinutes": 450,
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
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/sleep/range?startDate=2025-11-23&endDate=2025-11-25")
                .get("/v1/sleep/range?startDate=2025-11-23&endDate=2025-11-25")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "day with most sleep is identified"
        response.get("dayWithMostSleep.date") == "2025-11-24"
        response.getInt("dayWithMostSleep.sleepMinutes") == 600

        and: "day with least sleep is identified"
        response.get("dayWithLeastSleep.date") == "2025-11-23"
        response.getInt("dayWithLeastSleep.sleepMinutes") == 300
    }

    def "Scenario 6: Zero-duration sleep sessions are ignored"() {
        given: "a sleep session with zero duration"
        def request = """
        {
            "events": [{
                "idempotencyKey": "test-device|sleep|zero-duration",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-11-26T06:00:00Z",
                "payload": {
                    "sleepId": "zero-duration-sleep",
                    "sleepStart": "2025-11-26T06:00:00Z",
                    "sleepEnd": "2025-11-26T06:00:00Z",
                    "totalMinutes": 0,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit zero-duration event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "no projections are created"
        sessionProjectionRepository.findAll().isEmpty()
        dailyProjectionRepository.findAll().isEmpty()
    }

    def "Scenario 7: API returns 404 for date with no sleep data"() {
        given: "no sleep data exists"

        when: "I query daily detail"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/sleep/daily/2025-12-01")
                .get("/v1/sleep/daily/2025-12-01")
                .then()
                .extract()

        then: "404 is returned"
        response.statusCode() == 404
    }

    def "Scenario 8: Range summary includes days with no data as zero"() {
        given: "sleep only on first and last day of range"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|sleep|2025-11-27",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-27T06:00:00Z",
                    "payload": {
                        "sleepId": "sleep-2025-11-27",
                        "sleepStart": "2025-11-26T22:00:00Z",
                        "sleepEnd": "2025-11-27T06:00:00Z",
                        "totalMinutes": 480,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|sleep|2025-11-29",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-29T06:00:00Z",
                    "payload": {
                        "sleepId": "sleep-2025-11-29",
                        "sleepStart": "2025-11-28T22:00:00Z",
                        "sleepEnd": "2025-11-29T06:00:00Z",
                        "totalMinutes": 480,
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
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/sleep/range?startDate=2025-11-27&endDate=2025-11-29")
                .get("/v1/sleep/range?startDate=2025-11-27&endDate=2025-11-29")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "all 3 days are included"
        response.getList("dailyStats").size() == 3

        and: "middle day shows 0 sleep"
        def dailyStats = response.getList("dailyStats")
        dailyStats[0].totalSleepMinutes == 480
        dailyStats[1].totalSleepMinutes == 0  // 2025-11-28 has no data
        dailyStats[2].totalSleepMinutes == 480

        and: "daysWithData counts only non-zero days"
        response.getInt("daysWithData") == 2
    }

    def "Scenario 9: Idempotent events don't duplicate projections"() {
        given: "a sleep event"
        def date = "2025-11-30"
        def request = """
        {
            "events": [{
                "idempotencyKey": "test-device|sleep|duplicate-test",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-11-30T06:00:00Z",
                "payload": {
                    "sleepId": "duplicate-test",
                    "sleepStart": "2025-11-29T22:00:00Z",
                    "sleepEnd": "2025-11-30T06:00:00Z",
                    "totalMinutes": 480,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit the same event twice"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projection is not duplicated"
        def sessions = sessionProjectionRepository.findByDateOrderBySessionNumberAsc(LocalDate.parse(date))
        sessions.size() == 1
        sessions[0].durationMinutes == 480

        and: "daily projection reflects single session"
        def dailyData = dailyProjectionRepository.findByDate(LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalSleepMinutes == 480
        dailyData.get().sleepCount == 1
    }

    def "Scenario 10: Range query with invalid dates returns 400"() {
        when: "I query range with endDate before startDate"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"
        def response = authenticatedGetRequest(deviceId, secretBase64, "/v1/sleep/range?startDate=2025-11-30&endDate=2025-11-28")
                .get("/v1/sleep/range?startDate=2025-11-30&endDate=2025-11-28")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }

    def "Scenario 11: Event without sleepStart is ignored in projection"() {
        given: "an event missing sleepStart"
        def date = "2025-12-01"
        def request = """
        {
            "events": [{
                "idempotencyKey": "test-device|sleep|missing-sleep-start",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-12-01T06:00:00Z",
                "payload": {
                    "sleepEnd": "2025-12-01T06:00:00Z",
                    "totalMinutes": 480,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit the event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "no projections are created"
        def sessions = sessionProjectionRepository.findByDateOrderBySessionNumberAsc(LocalDate.parse(date))
        sessions.isEmpty()

        def dailyData = dailyProjectionRepository.findByDate(LocalDate.parse(date))
        !dailyData.isPresent()
    }

    def "Scenario 12: Multiple sessions update daily aggregates correctly"() {
        given: "three sleep sessions on the same day"
        def date = "2025-12-02"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|sleep|session1",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-12-02T06:00:00Z",
                    "payload": {
                        "sleepId": "session1-2025-12-02",
                        "sleepStart": "2025-12-01T22:00:00Z",
                        "sleepEnd": "2025-12-02T06:00:00Z",
                        "totalMinutes": 480,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|sleep|session2",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-12-02T13:00:00Z",
                    "payload": {
                        "sleepId": "session2-2025-12-02",
                        "sleepStart": "2025-12-02T12:00:00Z",
                        "sleepEnd": "2025-12-02T13:00:00Z",
                        "totalMinutes": 60,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                },
                {
                    "idempotencyKey": "test-device|sleep|session3",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-12-02T17:00:00Z",
                    "payload": {
                        "sleepId": "session3-2025-12-02",
                        "sleepStart": "2025-12-02T16:00:00Z",
                        "sleepEnd": "2025-12-02T17:00:00Z",
                        "totalMinutes": 30,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit all three events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "three session projections are created"
        def sessions = sessionProjectionRepository.findByDateOrderBySessionNumberAsc(LocalDate.parse(date))
        sessions.size() == 3
        sessions[0].sessionNumber == 1
        sessions[0].durationMinutes == 480
        sessions[1].sessionNumber == 2
        sessions[1].durationMinutes == 60
        sessions[2].sessionNumber == 3
        sessions[2].durationMinutes == 30

        and: "daily projection aggregates all correctly"
        def dailyData = dailyProjectionRepository.findByDate(LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().totalSleepMinutes == 570  // 480 + 60 + 30
        dailyData.get().sleepCount == 3
        dailyData.get().longestSessionMinutes == 480
        dailyData.get().shortestSessionMinutes == 30
        dailyData.get().averageSessionMinutes == 190  // 570 / 3
    }

    def "Scenario 13: Session time range is tracked correctly"() {
        given: "sleep session spanning across midnight"
        def date = "2025-12-03"
        def sleepStart = "2025-12-02T20:00:00Z"  // 21:00 Warsaw
        def sleepEnd = "2025-12-03T07:00:00Z"    // 08:00 Warsaw
        def request = """
        {
            "events": [{
                "idempotencyKey": "test-device|sleep|midnight-span",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "${sleepEnd}",
                "payload": {
                    "sleepId": "midnight-span-sleep",
                    "sleepStart": "${sleepStart}",
                    "sleepEnd": "${sleepEnd}",
                    "totalMinutes": 660,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit the event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "session projection has correct times"
        def sessions = sessionProjectionRepository.findByDateOrderBySessionNumberAsc(LocalDate.parse(date))
        sessions.size() == 1
        sessions[0].sleepStart.toString() == sleepStart
        sessions[0].sleepEnd.toString() == sleepEnd

        and: "daily projection reflects time range"
        def dailyData = dailyProjectionRepository.findByDate(LocalDate.parse(date))
        dailyData.isPresent()
        dailyData.get().firstSleepStart.toString() == sleepStart
        dailyData.get().lastSleepEnd.toString() == sleepEnd
    }
}
