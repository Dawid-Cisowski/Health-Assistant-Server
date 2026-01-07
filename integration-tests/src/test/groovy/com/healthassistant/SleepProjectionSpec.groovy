package com.healthassistant

import com.healthassistant.sleep.api.SleepFacade
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Sleep Projections
 */
@Title("Feature: Sleep Projections and Query API")
class SleepProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-sleep-proj"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    @Autowired
    SleepFacade sleepFacade

    def setup() {
        sleepFacade.deleteProjectionsByDeviceId(DEVICE_ID)
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
                "idempotencyKey": "${DEVICE_ID}|sleep|${sleepStart}",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the sleep event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections are created (verified via API)"
        def response = waitForApiResponse("/v1/sleep/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getList("sessions").size() == 1
        response.getList("sessions")[0].sessionNumber == 1
        response.getList("sessions")[0].durationMinutes == 480
        response.getInt("totalSleepMinutes") == 480
        response.getInt("sleepCount") == 1
        response.getInt("longestSessionMinutes") == 480
        response.getInt("shortestSessionMinutes") == 480
        response.getInt("averageSessionMinutes") == 480
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|main",
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|nap",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit both sleep events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections are created (verified via API)"
        def response = waitForApiResponse("/v1/sleep/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getList("sessions").size() == 2
        response.getList("sessions")[0].sessionNumber == 1
        response.getList("sessions")[0].durationMinutes == 480
        response.getList("sessions")[1].sessionNumber == 2
        response.getList("sessions")[1].durationMinutes == 60
        response.getInt("totalSleepMinutes") == 540  // 480 + 60
        response.getInt("sleepCount") == 2
        response.getInt("longestSessionMinutes") == 480
        response.getInt("shortestSessionMinutes") == 60
        response.getInt("averageSessionMinutes") == 270  // 540 / 2
    }

    def "Scenario 3: Query API returns daily detail with all sessions"() {
        given: "sleep data with multiple sessions"
        def date = "2025-11-22"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|sleep|night-2025-11-22",
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|nap-2025-11-22",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query daily detail"
        def deviceId = DEVICE_ID
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|2025-11-18",
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|2025-11-19",
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|2025-11-20",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query range summary"
        def deviceId = DEVICE_ID
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|2025-11-23",
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|2025-11-24",
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|2025-11-25",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query range summary"
        def deviceId = DEVICE_ID
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
                "idempotencyKey": "${DEVICE_ID}|sleep|zero-duration",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit zero-duration event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "no projections are created (API returns 404)"
        apiReturns404("/v1/sleep/daily/2025-11-26", DEVICE_ID, SECRET_BASE64)
    }

    def "Scenario 7: API returns 404 for date with no sleep data"() {
        given: "no sleep data exists"

        when: "I query daily detail"
        def deviceId = DEVICE_ID
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|2025-11-27",
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|2025-11-29",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query 3-day range"
        def deviceId = DEVICE_ID
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
                "idempotencyKey": "${DEVICE_ID}|sleep|duplicate-test",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the same event twice"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        waitForApiResponse("/v1/sleep/daily/${date}", DEVICE_ID, SECRET_BASE64)

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projection is not duplicated (verified via API)"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/sleep/daily/${date}")
                .get("/v1/sleep/daily/${date}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
        response.getList("sessions").size() == 1
        response.getList("sessions")[0].durationMinutes == 480
        response.getInt("totalSleepMinutes") == 480
        response.getInt("sleepCount") == 1
    }

    def "Scenario 10: Range query with invalid dates returns 400"() {
        when: "I query range with endDate before startDate"
        def deviceId = DEVICE_ID
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
                "idempotencyKey": "${DEVICE_ID}|sleep|missing-sleep-start",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-12-01T06:00:00Z",
                "payload": {
                    "sleepEnd": "2025-12-01T06:00:00Z",
                    "totalMinutes": 480,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "no projections are created (API returns 404)"
        apiReturns404("/v1/sleep/daily/${date}", DEVICE_ID, SECRET_BASE64)
    }

    def "Scenario 12: Multiple sessions update daily aggregates correctly"() {
        given: "three sleep sessions on the same day"
        def date = "2025-12-02"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "${DEVICE_ID}|sleep|session1",
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|session2",
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
                    "idempotencyKey": "${DEVICE_ID}|sleep|session3",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit all three events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections are created (verified via API)"
        def response = waitForApiResponse("/v1/sleep/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getList("sessions").size() == 3
        response.getList("sessions")[0].sessionNumber == 1
        response.getList("sessions")[0].durationMinutes == 480
        response.getList("sessions")[1].sessionNumber == 2
        response.getList("sessions")[1].durationMinutes == 60
        response.getList("sessions")[2].sessionNumber == 3
        response.getList("sessions")[2].durationMinutes == 30
        response.getInt("totalSleepMinutes") == 570  // 480 + 60 + 30
        response.getInt("sleepCount") == 3
        response.getInt("longestSessionMinutes") == 480
        response.getInt("shortestSessionMinutes") == 30
        response.getInt("averageSessionMinutes") == 190  // 570 / 3
    }

    def "Scenario 13: Session time range is tracked correctly"() {
        given: "sleep session spanning across midnight"
        def date = "2025-12-03"
        def sleepStart = "2025-12-02T20:00:00Z"  // 21:00 Warsaw
        def sleepEnd = "2025-12-03T07:00:00Z"    // 08:00 Warsaw
        def request = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|sleep|midnight-span",
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
            "deviceId": "${DEVICE_ID}"
        }
        """

        when: "I submit the event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections are created with correct time range (verified via API)"
        def response = waitForApiResponse("/v1/sleep/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response.getList("sessions").size() == 1
        response.getInt("totalSleepMinutes") == 660
        response.getString("firstSleepStart") == sleepStart
        response.getString("lastSleepEnd") == sleepEnd
    }

    def "Scenario 14: Device isolation - different devices have separate projections"() {
        given: "sleep from two different devices"
        def date = "2025-12-04"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "${DEVICE_ID}|sleep|device1-2025-12-04",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-12-04T06:00:00Z",
                "payload": {
                    "sleepId": "device1-sleep",
                    "sleepStart": "2025-12-03T22:00:00Z",
                    "sleepEnd": "2025-12-04T06:00:00Z",
                    "totalMinutes": 480,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "${DEVICE_ID}"
        }
        """
        def request2 = """
        {
            "events": [{
                "idempotencyKey": "different-device-id|sleep|device2-2025-12-04",
                "type": "SleepSessionRecorded.v1",
                "occurredAt": "2025-12-04T07:00:00Z",
                "payload": {
                    "sleepId": "device2-sleep",
                    "sleepStart": "2025-12-03T23:00:00Z",
                    "sleepEnd": "2025-12-04T07:00:00Z",
                    "totalMinutes": 480,
                    "originPackage": "com.google.android.apps.fitness"
                }
            }],
            "deviceId": "different-device-id"
        }
        """

        when: "I submit events from both devices"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
        authenticatedPostRequestWithBody("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, "/v1/health-events", request2)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "each device has its own projection (verified via API)"
        def response1 = waitForApiResponse("/v1/sleep/daily/${date}", DEVICE_ID, SECRET_BASE64)
        response1.getInt("totalSleepMinutes") == 480
        response1.getList("sessions").size() == 1
        response1.getList("sessions")[0].durationMinutes == 480

        and: "different device has its own data"
        def response2 = waitForApiResponse("/v1/sleep/daily/${date}", "different-device-id", DIFFERENT_DEVICE_SECRET_BASE64)
        response2.getInt("totalSleepMinutes") == 480
        response2.getList("sessions").size() == 1
        response2.getList("sessions")[0].durationMinutes == 480
    }
}
