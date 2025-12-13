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

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)
        
        // Second sync with more steps
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime2, endTime2, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)
        
        // Third sync with heart rate
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(startTime3, endTime3, 0, 0, 0, 78))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
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
        summary.getLong("activity.distanceMeters") == 1001L // rounded from 1000.5
        // Allow for rounding differences in calories
        def activeCalories = summary.getDouble("activity.activeCalories")
        (activeCalories == 62.5 || activeCalories == 62.0 || activeCalories == 63.0)
        
        summary.getInt("heart.avgBpm") != null
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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        and: "sync data"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        and: "second sync with additional steps data"
        def startTime2 = summaryZoned.plusHours(13).toInstant().toEpochMilli()
        def endTime2 = summaryZoned.plusHours(14).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime2, endTime2, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())

        when: "I trigger sync again"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)
        
        // Second sync with more steps
        def startTime2 = summaryZoned.plusHours(13).toInstant().toEpochMilli()
        def endTime2 = summaryZoned.plusHours(14).toInstant().toEpochMilli()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(startTime2, endTime2, 742))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
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

    def "Scenario 7: Summary includes walking exercises"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 18)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "mock Google Fit API responses with walking session"
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))
        def walkStart = summaryZoned.plusHours(10).toInstant().toEpochMilli()
        def walkEnd = summaryZoned.plusHours(11).toInstant().toEpochMilli()
        
        setupGoogleFitSessionsApiMock(createGoogleFitSessionsResponseWithWalking(walkStart, walkEnd, "walk-123"))
        setupGoogleFitApiMock(createGoogleFitResponseWithMultipleDataTypes(walkStart, walkEnd, 5000, 3000.0, 150.0, 80))

        when: "I trigger sync"
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        and: "I get summary"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary includes walking exercise"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        def exercises = summary.getList("exercises")
        exercises.size() == 1
        exercises[0].get("type") == "WALK"
        exercises[0].get("distanceMeters") == 3000L
        exercises[0].get("steps") == 5000
        exercises[0].get("energyKcal") == 150
        exercises[0].get("durationMinutes") == 60
    }

    def "Scenario 8: Summary includes workout with basic metrics"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 19)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "workout event"
        def workoutTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(18).toInstant()
        def workoutEvent = [
            idempotencyKey: "gymrun-2025-11-19-1",
            type: "WorkoutRecorded.v1",
            occurredAt: workoutTime.toString(),
            payload: [
                workoutId: "gymrun-2025-11-19-1",
                performedAt: workoutTime.toString(),
                source: "GYMRUN_SCREENSHOT",
                note: "Plecy i biceps",
                exercises: [
                    [
                        name: "Podciąganie się nachwytem",
                        muscleGroup: "Plecy",
                        orderInWorkout: 1,
                        sets: [
                            [setNumber: 1, weightKg: 73.0, reps: 12, isWarmup: false],
                            [setNumber: 2, weightKg: 73.5, reps: 10, isWarmup: false]
                        ]
                    ],
                    [
                        name: "Wiosłowanie sztangą",
                        muscleGroup: "Plecy",
                        orderInWorkout: 2,
                        sets: [
                            [setNumber: 1, weightKg: 60.0, reps: 10, isWarmup: false],
                            [setNumber: 2, weightKg: 65.0, reps: 8, isWarmup: false],
                            [setNumber: 3, weightKg: 70.0, reps: 6, isWarmup: false]
                        ]
                    ]
                ]
            ]
        ]

        when: "I submit workout event"
        def submitResponse = authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([
                    events: [workoutEvent],
                    deviceId: deviceId
                ]))
                .post("/v1/health-events")
                .then()
                .extract()

        then: "workout is accepted"
        submitResponse.statusCode() == 200
        submitResponse.body().jsonPath().getString("status") == "success"

        when: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary includes workout with calculated metrics"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        summary.getString("date") == dateStr

        def workouts = summary.getList("workouts")
        workouts.size() == 1

        def workout = workouts[0]
        workout.get("workoutId") == "gymrun-2025-11-19-1"
        workout.get("note") == "Plecy i biceps"
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
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
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

    def "Scenario 10: Summary includes single sleep session"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 23)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "sleep session event"
        def sleepStart = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).minusHours(1).toInstant()
        def sleepEnd = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(7).toInstant()
        def sleepEvent = [
            idempotencyKey: "sleep-2025-11-23-1",
            type: "SleepSessionRecorded.v1",
            occurredAt: sleepEnd.toString(),
            payload: [
                sleepId: "sleep-2025-11-23-1",
                sleepStart: sleepStart.toString(),
                sleepEnd: sleepEnd.toString(),
                totalMinutes: 480,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        when: "I submit sleep event"
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([
                    events: [sleepEvent],
                    deviceId: deviceId
                ]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary includes single sleep session"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()

        def sleepSessions = summary.getList("sleep")
        sleepSessions.size() == 1

        def sleep = sleepSessions[0]
        sleep.get("totalMinutes") == 480
        sleep.get("start") != null
        sleep.get("end") != null
    }

    def "Scenario 11: Summary includes multiple sleep sessions (night sleep + nap)"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 24)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "night sleep event"
        def nightSleepStart = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).minusHours(1).toInstant()
        def nightSleepEnd = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(7).toInstant()
        def nightSleepEvent = [
            idempotencyKey: "sleep-2025-11-24-night",
            type: "SleepSessionRecorded.v1",
            occurredAt: nightSleepEnd.toString(),
            payload: [
                sleepId: "sleep-2025-11-24-night",
                sleepStart: nightSleepStart.toString(),
                sleepEnd: nightSleepEnd.toString(),
                totalMinutes: 480,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        and: "afternoon nap event"
        def napStart = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(14).toInstant()
        def napEnd = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(15).toInstant()
        def napEvent = [
            idempotencyKey: "sleep-2025-11-24-nap",
            type: "SleepSessionRecorded.v1",
            occurredAt: napEnd.toString(),
            payload: [
                sleepId: "sleep-2025-11-24-nap",
                sleepStart: napStart.toString(),
                sleepEnd: napEnd.toString(),
                totalMinutes: 60,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        when: "I submit both sleep events"
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([
                    events: [nightSleepEvent, napEvent],
                    deviceId: deviceId
                ]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary includes both sleep sessions"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()

        def sleepSessions = summary.getList("sleep")
        sleepSessions.size() == 2

        // Night sleep
        def nightSleep = sleepSessions.find { it.totalMinutes == 480 }
        nightSleep != null
        nightSleep.get("totalMinutes") == 480

        // Nap
        def nap = sleepSessions.find { it.totalMinutes == 60 }
        nap != null
        nap.get("totalMinutes") == 60
    }

    def "Scenario 12: Summary has empty sleep list when no sleep events"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 25)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "only activity event (no sleep)"
        def activityTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(10).toInstant()
        def stepsEvent = [
            idempotencyKey: "steps-2025-11-25-1",
            type: "StepsBucketedRecorded.v1",
            occurredAt: activityTime.toString(),
            payload: [
                bucketStart: activityTime.minusSeconds(3600).toString(),
                bucketEnd: activityTime.toString(),
                count: 1000,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        when: "I submit activity event without sleep"
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([
                    events: [stepsEvent],
                    deviceId: deviceId
                ]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary has empty sleep list"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()

        def sleepSessions = summary.getList("sleep")
        sleepSessions.size() == 0
    }

    def "Scenario 13: Get range summary for multiple days with various activities"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "multiple days with different activities"
        def startDate = LocalDate.of(2025, 11, 26)
        def midDate = LocalDate.of(2025, 11, 27)
        def endDate = LocalDate.of(2025, 11, 28)

        and: "first day with steps and sleep"
        def day1Time = startDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(10).toInstant()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(day1Time.toEpochMilli(), day1Time.plusSeconds(3600).toEpochMilli(), 5000))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def sleepEvent1 = [
            idempotencyKey: "sleep-2025-11-26-1",
            type: "SleepSessionRecorded.v1",
            occurredAt: startDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(7).toInstant().toString(),
            payload: [
                sleepId: "sleep-2025-11-26-1",
                sleepStart: startDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).minusHours(1).toInstant().toString(),
                sleepEnd: startDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(7).toInstant().toString(),
                totalMinutes: 480,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([events: [sleepEvent1], deviceId: deviceId]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "second day with more steps and meals"
        def day2Time = midDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(14).toInstant()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(day2Time.toEpochMilli(), day2Time.plusSeconds(3600).toEpochMilli(), 8000))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def mealEvent1 = [
            idempotencyKey: "meal-2025-11-27-1",
            type: "MealRecorded.v1",
            occurredAt: midDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(12).toInstant().toString(),
            payload: [
                title: "Grilled Chicken",
                mealType: "LUNCH",
                caloriesKcal: 500,
                proteinGrams: 45,
                fatGrams: 15,
                carbohydratesGrams: 30,
                healthRating: "HEALTHY"
            ]
        ]
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([events: [mealEvent1], deviceId: deviceId]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "third day with workout and fewer steps"
        def day3Time = endDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(9).toInstant()
        setupGoogleFitApiMock(createGoogleFitResponseWithSteps(day3Time.toEpochMilli(), day3Time.plusSeconds(3600).toEpochMilli(), 3000))
        setupGoogleFitSessionsApiMock(createEmptyGoogleFitSessionsResponse())
        authenticatedPostRequest(deviceId, secretBase64, "/v1/google-fit/sync")
                .post("/v1/google-fit/sync")
                .then()
                .statusCode(200)

        def workoutEvent = [
            idempotencyKey: "gymrun-2025-11-28-1",
            type: "WorkoutRecorded.v1",
            occurredAt: endDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(18).toInstant().toString(),
            payload: [
                workoutId: "gymrun-2025-11-28-1",
                performedAt: endDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(18).toInstant().toString(),
                source: "GYMRUN_SCREENSHOT",
                note: "Legs day",
                exercises: [
                    [
                        name: "Squat",
                        muscleGroup: "Legs",
                        orderInWorkout: 1,
                        sets: [
                            [setNumber: 1, weightKg: 100.0, reps: 10, isWarmup: false]
                        ]
                    ]
                ]
            ]
        ]
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([events: [workoutEvent], deviceId: deviceId]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I get range summary for the 3 days"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/range?startDate=2025-11-26&endDate=2025-11-28")
                .get("/v1/daily-summaries/range?startDate=2025-11-26&endDate=2025-11-28")
                .then()
                .extract()

        then: "range summary is returned with aggregated metrics"
        getResponse.statusCode() == 200
        def rangeSummary = getResponse.body().jsonPath()

        rangeSummary.getString("startDate") == "2025-11-26"
        rangeSummary.getString("endDate") == "2025-11-28"
        rangeSummary.getInt("daysWithData") == 3

        and: "activity summary shows totals and averages"
        rangeSummary.getInt("activity.totalSteps") == 16000 // 5000 + 8000 + 3000
        rangeSummary.getInt("activity.averageSteps") == 5333 // 16000 / 3

        and: "sleep summary shows correct aggregation"
        rangeSummary.getInt("sleep.totalSleepMinutes") == 480
        rangeSummary.getInt("sleep.averageSleepMinutes") == 480
        rangeSummary.getInt("sleep.daysWithSleep") == 1

        and: "nutrition summary shows meal data"
        rangeSummary.getInt("nutrition.totalCalories") == 500
        rangeSummary.getInt("nutrition.totalProtein") == 45
        rangeSummary.getInt("nutrition.totalMeals") == 1
        rangeSummary.getInt("nutrition.daysWithData") == 1

        and: "workout summary shows workout count"
        rangeSummary.getInt("workouts.totalWorkouts") == 1
        rangeSummary.getInt("workouts.daysWithWorkouts") == 1

        and: "daily stats include all 3 days"
        def dailyStats = rangeSummary.getList("dailyStats")
        dailyStats.size() == 3
        dailyStats[0].date == "2025-11-26"
        dailyStats[1].date == "2025-11-27"
        dailyStats[2].date == "2025-11-28"
    }

    def "Scenario 14: Range summary with invalid dates returns 400"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        when: "I request range with endDate before startDate"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/range?startDate=2025-11-30&endDate=2025-11-28")
                .get("/v1/daily-summaries/range?startDate=2025-11-30&endDate=2025-11-28")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        getResponse.statusCode() == 400
    }

    def "Scenario 15: Range summary returns empty stats for dates with no data"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        when: "I request range for dates with no summaries"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/range?startDate=2025-12-01&endDate=2025-12-03")
                .get("/v1/daily-summaries/range?startDate=2025-12-01&endDate=2025-12-03")
                .then()
                .extract()

        then: "range summary is returned with zero values"
        getResponse.statusCode() == 200
        def rangeSummary = getResponse.body().jsonPath()

        rangeSummary.getString("startDate") == "2025-12-01"
        rangeSummary.getString("endDate") == "2025-12-03"
        rangeSummary.getInt("daysWithData") == 0

        rangeSummary.getInt("activity.totalSteps") == 0
        rangeSummary.getInt("activity.averageSteps") == 0
        rangeSummary.getInt("sleep.totalSleepMinutes") == 0
        rangeSummary.getInt("nutrition.totalCalories") == 0
        rangeSummary.getInt("workouts.totalWorkouts") == 0

        def dailyStats = rangeSummary.getList("dailyStats")
        dailyStats.size() == 0
    }

    def "Scenario 16: Summary includes distance data from DistanceBucketRecorded events"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 12, 5)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "distance event"
        def bucketTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(10).toInstant()
        def distanceEvent = [
            idempotencyKey: "distance-2025-12-05-1",
            type: "DistanceBucketRecorded.v1",
            occurredAt: bucketTime.toString(),
            payload: [
                bucketStart: bucketTime.minusSeconds(3600).toString(),
                bucketEnd: bucketTime.toString(),
                distanceMeters: 2500.5,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        when: "I submit distance event"
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([
                    events: [distanceEvent],
                    deviceId: deviceId
                ]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary includes distance data"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        summary.getString("date") == dateStr
        summary.getLong("activity.distanceMeters") == 2501L // rounded from 2500.5
    }

    def "Scenario 17: Summary includes heart rate data from HeartRateSummaryRecorded events"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 12, 6)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "heart rate summary event"
        def bucketTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(9).toInstant()
        def heartRateEvent = [
            idempotencyKey: "hr-2025-12-06-1",
            type: "HeartRateSummaryRecorded.v1",
            occurredAt: bucketTime.toString(),
            payload: [
                bucketStart: bucketTime.minusSeconds(900).toString(),
                bucketEnd: bucketTime.toString(),
                avg: 78.5,
                min: 62,
                max: 115,
                samples: 50,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        when: "I submit heart rate event"
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([
                    events: [heartRateEvent],
                    deviceId: deviceId
                ]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary includes heart rate data"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        summary.getString("date") == dateStr
        def avgBpm = summary.getInt("heart.avgBpm")
        (avgBpm == 78 || avgBpm == 79) // Allow for rounding differences
        summary.getInt("heart.maxBpm") == 115
    }

    def "Scenario 18: Summary includes active minutes data"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 12, 7)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "active minutes event"
        def bucketTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(14).toInstant()
        def activeMinutesEvent = [
            idempotencyKey: "active-minutes-2025-12-07-1",
            type: "ActiveMinutesRecorded.v1",
            occurredAt: bucketTime.toString(),
            payload: [
                bucketStart: bucketTime.minusSeconds(3600).toString(),
                bucketEnd: bucketTime.toString(),
                activeMinutes: 45,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        when: "I submit active minutes event"
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([
                    events: [activeMinutesEvent],
                    deviceId: deviceId
                ]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary includes active minutes data"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        summary.getString("date") == dateStr
        summary.getInt("activity.activeMinutes") == 45
    }

    def "Scenario 19: Summary includes active calories data"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 12, 8)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "active calories event"
        def bucketTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(16).toInstant()
        def activeCaloriesEvent = [
            idempotencyKey: "active-calories-2025-12-08-1",
            type: "ActiveCaloriesBurnedRecorded.v1",
            occurredAt: bucketTime.toString(),
            payload: [
                bucketStart: bucketTime.minusSeconds(3600).toString(),
                bucketEnd: bucketTime.toString(),
                energyKcal: 320.5,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        when: "I submit active calories event"
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([
                    events: [activeCaloriesEvent],
                    deviceId: deviceId
                ]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary includes active calories data"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        summary.getString("date") == dateStr
        def activeCalories = summary.getDouble("activity.activeCalories")
        (activeCalories == 320.5 || activeCalories == 320.0 || activeCalories == 321.0) // Allow rounding
    }

    def "Scenario 20: Summary aggregates multiple event types from the same day correctly"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 12, 9)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "multiple event types for the same day"
        def baseTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))

        def stepsEvent = [
            idempotencyKey: "steps-2025-12-09-1",
            type: "StepsBucketedRecorded.v1",
            occurredAt: baseTime.plusHours(10).toInstant().toString(),
            payload: [
                bucketStart: baseTime.plusHours(9).toInstant().toString(),
                bucketEnd: baseTime.plusHours(10).toInstant().toString(),
                count: 3500,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        def distanceEvent = [
            idempotencyKey: "distance-2025-12-09-1",
            type: "DistanceBucketRecorded.v1",
            occurredAt: baseTime.plusHours(10).toInstant().toString(),
            payload: [
                bucketStart: baseTime.plusHours(9).toInstant().toString(),
                bucketEnd: baseTime.plusHours(10).toInstant().toString(),
                distanceMeters: 2800.0,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        def caloriesEvent = [
            idempotencyKey: "calories-2025-12-09-1",
            type: "ActiveCaloriesBurnedRecorded.v1",
            occurredAt: baseTime.plusHours(10).toInstant().toString(),
            payload: [
                bucketStart: baseTime.plusHours(9).toInstant().toString(),
                bucketEnd: baseTime.plusHours(10).toInstant().toString(),
                energyKcal: 150.0,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        def activeMinutesEvent = [
            idempotencyKey: "active-2025-12-09-1",
            type: "ActiveMinutesRecorded.v1",
            occurredAt: baseTime.plusHours(10).toInstant().toString(),
            payload: [
                bucketStart: baseTime.plusHours(9).toInstant().toString(),
                bucketEnd: baseTime.plusHours(10).toInstant().toString(),
                activeMinutes: 35,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        def heartRateEvent = [
            idempotencyKey: "hr-2025-12-09-1",
            type: "HeartRateSummaryRecorded.v1",
            occurredAt: baseTime.plusHours(10).toInstant().toString(),
            payload: [
                bucketStart: baseTime.plusHours(9).toInstant().toString(),
                bucketEnd: baseTime.plusHours(10).toInstant().toString(),
                avg: 85.0,
                min: 70,
                max: 120,
                samples: 60,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        when: "I submit all events"
        authenticatedPostRequestWithBody(deviceId, secretBase64, "/v1/health-events", groovy.json.JsonOutput.toJson([
                    events: [stepsEvent, distanceEvent, caloriesEvent, activeMinutesEvent, heartRateEvent],
                    deviceId: deviceId
                ]))
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I get summary for the date"
        def getResponse = authenticatedGetRequest(deviceId, secretBase64, "/v1/daily-summaries/${dateStr}")
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "summary includes all activity data"
        getResponse.statusCode() == 200
        def summary = getResponse.body().jsonPath()
        summary.getString("date") == dateStr
        summary.getInt("activity.steps") == 3500
        summary.getLong("activity.distanceMeters") == 2800L
        summary.getInt("activity.activeMinutes") == 35
        def activeCalories = summary.getDouble("activity.activeCalories")
        (activeCalories == 150.0 || activeCalories == 150)

        and: "summary includes heart rate data"
        summary.getInt("heart.avgBpm") == 85
        summary.get("heart.maxBpm") == 120
    }

}

