package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Integration tests for Daily Summary feature
 */
@Title("Feature: Daily Summary")
class DailySummarySpec extends BaseIntegrationSpec {


    def "Scenario 1: Generate daily summary for a date with multiple event types"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 12)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "events for the day"
        def timestamp = System.currentTimeMillis()
        
        // Steps events
        def steps1 = """
        {
            "events": [
                {
                    "idempotencyKey": "steps1|${timestamp}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-12T08:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-12T07:00:00Z",
                        "bucketEnd": "2025-11-12T08:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        def steps2 = """
        {
            "events": [
                {
                    "idempotencyKey": "steps2|${timestamp}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-12T12:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-12T11:00:00Z",
                        "bucketEnd": "2025-11-12T12:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        // Active minutes
        def activeMinutes = """
        {
            "events": [
                {
                    "idempotencyKey": "activeMinutes|${timestamp}",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-12T14:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-12T13:00:00Z",
                        "bucketEnd": "2025-11-12T14:00:00Z",
                        "activeMinutes": 25,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        // Active calories
        def activeCalories = """
        {
            "events": [
                {
                    "idempotencyKey": "activeCalories|${timestamp}",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-12T16:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-12T15:00:00Z",
                        "bucketEnd": "2025-11-12T16:00:00Z",
                        "energyKcal": 125.5,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        // Exercise session
        def exercise = """
        {
            "events": [
                {
                    "idempotencyKey": "exercise|${timestamp}",
                    "type": "ExerciseSessionRecorded.v1",
                    "occurredAt": "2025-11-12T10:00:00Z",
                    "payload": {
                        "sessionId": "e4210819-5708-3835-bcbb-2776e037e258",
                        "type": "other_0",
                        "start": "2025-11-12T09:03:15Z",
                        "end": "2025-11-12T10:03:03Z",
                        "durationMinutes": 59,
                        "distanceMeters": "5838.7",
                        "steps": 13812,
                        "avgSpeedMetersPerSecond": "1.65",
                        "avgHr": 83,
                        "maxHr": 123,
                        "originPackage": "com.heytap.health.international"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        // Sleep session
        def sleep = """
        {
            "events": [
                {
                    "idempotencyKey": "sleep|${timestamp}",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-12T08:00:00Z",
                    "payload": {
                        "sleepStart": "2025-11-12T00:30:00Z",
                        "sleepEnd": "2025-11-12T08:00:00Z",
                        "totalMinutes": 450,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        // Heart rate
        def heartRate = """
        {
            "events": [
                {
                    "idempotencyKey": "heartRate|${timestamp}",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-12T15:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-12T14:00:00Z",
                        "bucketEnd": "2025-11-12T15:00:00Z",
                        "avg": 78.3,
                        "min": 61,
                        "max": 115,
                        "samples": 46,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        when: "I store all events"
        authenticatedRequest(deviceId, secretBase64, steps1).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, steps2).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, activeMinutes).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, activeCalories).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, exercise).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, sleep).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, heartRate).post("/v1/health-events")

        and: "I generate summary for the date"
        def generateResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${dateStr}")
                .then()
                .extract()

        then: "summary is generated successfully"
        generateResponse.statusCode() == 200
        
        def summary = generateResponse.body().jsonPath()
        summary.getString("date") == dateStr
        summary.getInt("activity.steps") == 1484 // 742 + 742
        summary.getInt("activity.activeMinutes") == 25
        summary.get("activity.activeCalories") == 125.5 || summary.get("activity.activeCalories") == 125.0
        summary.getDouble("activity.distanceMeters") == 5838.7
        
        summary.getList("workouts").size() == 1
        summary.getString("workouts[0].type") == "WALK"
        summary.getInt("workouts[0].durationMinutes") == 59
        
        summary.getString("sleep.start") != null
        summary.getInt("sleep.totalMinutes") == 450
        
        summary.getInt("heart.avgBpm") != null
        summary.getInt("heart.maxBpm") != null
        
        summary.getInt("score.activityScore") >= 0
        summary.getInt("score.sleepScore") >= 0
        summary.getInt("score.readinessScore") >= 0
        summary.getInt("score.overallScore") >= 0

        and: "summary is stored in database (can be retrieved)"
        def getResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()
        getResponse.statusCode() == 200
    }

    def "Scenario 2: Get daily summary by date"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 13)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "events for the day"
        def timestamp = System.currentTimeMillis()
        def steps = """
        {
            "events": [
                {
                    "idempotencyKey": "steps|${timestamp}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-13T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-13T09:00:00Z",
                        "bucketEnd": "2025-11-13T10:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        authenticatedRequest(deviceId, secretBase64, steps).post("/v1/health-events")

        and: "summary is generated"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${dateStr}")

        when: "I get summary for the date"
        def getResponse = RestAssured.given()
                .contentType(ContentType.JSON)
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

        when: "I get summary for the date"
        def getResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()

        then: "404 is returned"
        getResponse.statusCode() == 404
    }

    def "Scenario 4: Generate summary overwrites existing summary"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 15)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "first set of events"
        def timestamp1 = System.currentTimeMillis()
        def steps1 = """
        {
            "events": [
                {
                    "idempotencyKey": "steps1|${timestamp1}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-15T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-15T09:00:00Z",
                        "bucketEnd": "2025-11-15T10:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        authenticatedRequest(deviceId, secretBase64, steps1).post("/v1/health-events")

        and: "first summary is generated"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${dateStr}")

        and: "second set of events"
        def timestamp2 = System.currentTimeMillis()
        def steps2 = """
        {
            "events": [
                {
                    "idempotencyKey": "steps2|${timestamp2}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-15T14:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-15T13:00:00Z",
                        "bucketEnd": "2025-11-15T14:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        authenticatedRequest(deviceId, secretBase64, steps2).post("/v1/health-events")

        when: "I generate summary again"
        def generateResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${dateStr}")
                .then()
                .extract()

        then: "summary is regenerated with updated data"
        generateResponse.statusCode() == 200
        
        def summary = generateResponse.body().jsonPath()
        summary.getInt("activity.steps") == 1484 // Both events included

        and: "summary can be retrieved (overwritten)"
        def getResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .get("/v1/daily-summaries/${dateStr}")
                .then()
                .extract()
        getResponse.statusCode() == 200
        getResponse.body().jsonPath().getInt("activity.steps") == 1484
    }

    def "Scenario 5: Generate summary for date with no events returns empty summary"() {
        given: "date with no events"
        def summaryDate = LocalDate.of(2025, 11, 16)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        when: "I generate summary for the date"
        def generateResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${dateStr}")
                .then()
                .extract()

        then: "summary is generated with null/zero values"
        generateResponse.statusCode() == 200
        
        def summary = generateResponse.body().jsonPath()
        summary.getString("date") == dateStr
        summary.get("activity.steps") == null
        summary.getList("workouts").size() == 0
        // Sleep and heart may be null or empty objects - check that sleep.totalMinutes is null
        summary.get("sleep.totalMinutes") == null
        summary.get("heart.restingBpm") == null
        
        and: "scores are calculated (default values)"
        summary.getInt("score.activityScore") == 0
        summary.getInt("score.sleepScore") == 0
        summary.getInt("score.readinessScore") >= 0
        summary.getInt("score.overallScore") >= 0
    }

    def "Scenario 6: Generate summary aggregates multiple workouts"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 17)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "multiple exercise sessions"
        def timestamp = System.currentTimeMillis()
        def exercise1 = """
        {
            "events": [
                {
                    "idempotencyKey": "exercise1|${timestamp}",
                    "type": "ExerciseSessionRecorded.v1",
                    "occurredAt": "2025-11-17T08:00:00Z",
                    "payload": {
                        "sessionId": "session1",
                        "type": "other_0",
                        "start": "2025-11-17T07:03:15Z",
                        "end": "2025-11-17T08:03:03Z",
                        "durationMinutes": 59,
                        "distanceMeters": "5838.7",
                        "steps": 13812,
                        "avgSpeedMetersPerSecond": "1.65",
                        "avgHr": 83,
                        "maxHr": 123,
                        "originPackage": "com.heytap.health.international"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        def exercise2 = """
        {
            "events": [
                {
                    "idempotencyKey": "exercise2|${timestamp}",
                    "type": "ExerciseSessionRecorded.v1",
                    "occurredAt": "2025-11-17T14:00:00Z",
                    "payload": {
                        "sessionId": "session2",
                        "type": "other_0",
                        "start": "2025-11-17T13:03:15Z",
                        "end": "2025-11-17T14:03:03Z",
                        "durationMinutes": 59,
                        "distanceMeters": "5838.7",
                        "steps": 13812,
                        "avgSpeedMetersPerSecond": "1.65",
                        "avgHr": 83,
                        "maxHr": 123,
                        "originPackage": "com.heytap.health.international"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        authenticatedRequest(deviceId, secretBase64, exercise1).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, exercise2).post("/v1/health-events")

        when: "I generate summary"
        def generateResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${dateStr}")
                .then()
                .extract()

        then: "summary contains both workouts"
        generateResponse.statusCode() == 200
        
        def summary = generateResponse.body().jsonPath()
        summary.getList("workouts").size() == 2
        summary.getString("workouts[0].type") == "WALK"
        summary.getString("workouts[1].type") == "WALK"
    }

    def "Scenario 7: Generate summary calculates activity score correctly"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 18)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "events with high activity (should score well)"
        def timestamp = System.currentTimeMillis()
        
        // 15000 steps (should score high)
        def steps = """
        {
            "events": [
                {
                    "idempotencyKey": "highSteps|${timestamp}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-18T12:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-18T11:00:00Z",
                        "bucketEnd": "2025-11-18T12:00:00Z",
                        "count": 15000,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        // 45 active minutes (should score high)
        def activeMinutes = """
        {
            "events": [
                {
                    "idempotencyKey": "highActiveMinutes|${timestamp}",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "2025-11-18T14:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-18T13:00:00Z",
                        "bucketEnd": "2025-11-18T14:00:00Z",
                        "activeMinutes": 45,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        // 500 active calories (should score high)
        def activeCalories = """
        {
            "events": [
                {
                    "idempotencyKey": "highCalories|${timestamp}",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "2025-11-18T16:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-18T15:00:00Z",
                        "bucketEnd": "2025-11-18T16:00:00Z",
                        "energyKcal": 500.0,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        authenticatedRequest(deviceId, secretBase64, steps).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, activeMinutes).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, activeCalories).post("/v1/health-events")

        when: "I generate summary"
        def generateResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${dateStr}")
                .then()
                .extract()

        then: "activity score is high"
        generateResponse.statusCode() == 200
        
        def summary = generateResponse.body().jsonPath()
        def activityScore = summary.getInt("score.activityScore")
        activityScore >= 80 // Should be high with 15k steps, 45 min, 500 cal
    }

    def "Scenario 8: Generate summary calculates sleep score correctly"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 19)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "sleep session with optimal duration (7-9 hours = 420-540 minutes)"
        def timestamp = System.currentTimeMillis()
        def sleep = """
        {
            "events": [
                {
                    "idempotencyKey": "optimalSleep|${timestamp}",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "2025-11-19T08:00:00Z",
                    "payload": {
                        "sleepStart": "2025-11-19T00:00:00Z",
                        "sleepEnd": "2025-11-19T08:00:00Z",
                        "totalMinutes": 480,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        authenticatedRequest(deviceId, secretBase64, sleep).post("/v1/health-events")

        when: "I generate summary"
        def generateResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${dateStr}")
                .then()
                .extract()

        then: "sleep score is high"
        generateResponse.statusCode() == 200
        
        def summary = generateResponse.body().jsonPath()
        def sleepScore = summary.getInt("score.sleepScore")
        sleepScore == 100 // 480 minutes is optimal
    }

    def "Scenario 9: Generate summary calculates readiness score from heart rate"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "date for summary"
        def summaryDate = LocalDate.of(2025, 11, 20)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)

        and: "heart rate with optimal resting HR (60-70 bpm)"
        def timestamp = System.currentTimeMillis()
        def heartRate = """
        {
            "events": [
                {
                    "idempotencyKey": "optimalHR|${timestamp}",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "2025-11-20T10:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-20T09:00:00Z",
                        "bucketEnd": "2025-11-20T10:00:00Z",
                        "avg": 65.0,
                        "min": 60,
                        "max": 80,
                        "samples": 30,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        authenticatedRequest(deviceId, secretBase64, heartRate).post("/v1/health-events")

        when: "I generate summary"
        def generateResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${dateStr}")
                .then()
                .extract()

        then: "readiness score is high"
        generateResponse.statusCode() == 200
        
        def summary = generateResponse.body().jsonPath()
        def readinessScore = summary.getInt("score.readinessScore")
        readinessScore == 100 // Resting HR 60 is optimal
    }

    def "Scenario 10: Generate summary only includes events from specified date"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "events from different dates"
        def timestamp = System.currentTimeMillis()
        
        // Event from day before
        def previousDay = """
        {
            "events": [
                {
                    "idempotencyKey": "prevDay|${timestamp}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-21T23:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-21T22:00:00Z",
                        "bucketEnd": "2025-11-21T23:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        // Event from target day
        def targetDate = LocalDate.of(2025, 11, 22)
        def targetDateStr = targetDate.format(DateTimeFormatter.ISO_DATE)
        def targetDay = """
        {
            "events": [
                {
                    "idempotencyKey": "targetDay|${timestamp}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-22T12:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-22T11:00:00Z",
                        "bucketEnd": "2025-11-22T12:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        
        // Event from day after
        def nextDay = """
        {
            "events": [
                {
                    "idempotencyKey": "nextDay|${timestamp}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-23T01:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-23T00:00:00Z",
                        "bucketEnd": "2025-11-23T01:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        authenticatedRequest(deviceId, secretBase64, previousDay).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, targetDay).post("/v1/health-events")
        authenticatedRequest(deviceId, secretBase64, nextDay).post("/v1/health-events")

        when: "I generate summary for target date"
        def generateResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate/${targetDateStr}")
                .then()
                .extract()

        then: "summary only includes events from target date"
        generateResponse.statusCode() == 200
        
        def summary = generateResponse.body().jsonPath()
        summary.getInt("activity.steps") == 742 // Only target day event
    }

    def "Scenario 11: Trigger scheduled generation generates summary for previous day"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "yesterday's date"
        def yesterday = LocalDate.now().minusDays(1)
        def yesterdayStr = yesterday.format(DateTimeFormatter.ISO_DATE)

        and: "events for yesterday"
        def timestamp = System.currentTimeMillis()
        def steps = """
        {
            "events": [
                {
                    "idempotencyKey": "yesterdaySteps|${timestamp}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "${yesterdayStr}T12:00:00Z",
                    "payload": {
                        "bucketStart": "${yesterdayStr}T11:00:00Z",
                        "bucketEnd": "${yesterdayStr}T12:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
        authenticatedRequest(deviceId, secretBase64, steps).post("/v1/health-events")

        when: "I trigger scheduled generation"
        def triggerResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .post("/v1/daily-summaries/generate")
                .then()
                .extract()

        then: "summary is generated for yesterday"
        triggerResponse.statusCode() == 200

        and: "summary can be retrieved"
        def getResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .get("/v1/daily-summaries/${yesterdayStr}")
                .then()
                .extract()

        getResponse.statusCode() == 200
        getResponse.body().jsonPath().getString("date") == yesterdayStr
    }

}

