package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Title("Feature: AI Daily Summary")
class AiDailySummarySpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    def "GET /v1/daily-summaries/{date}/ai-text should require HMAC authentication"() {
        when: "request is sent without authentication"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .when()
                .get("/v1/daily-summaries/2025-12-10/ai-text")

        then: "should return 401 Unauthorized"
        response.statusCode() == 401
    }

    def "GET /v1/daily-summaries/{date}/ai-text should return AI summary for date with data"() {
        given: "configured AI response"
        TestChatModelConfiguration.setResponse("super dzien! duzo krokow i solidnie sie wyspales 💪")

        and: "health data exists for the date"
        def summaryDate = LocalDate.of(2025, 12, 10)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)
        def summaryZoned = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))

        def stepsEvent = [
            idempotencyKey: "steps-ai-test-1",
            type: "StepsBucketedRecorded.v1",
            occurredAt: summaryZoned.plusHours(10).toInstant().toString(),
            payload: [
                bucketStart: summaryZoned.plusHours(9).toInstant().toString(),
                bucketEnd: summaryZoned.plusHours(10).toInstant().toString(),
                count: 8500,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        def sleepEvent = [
            idempotencyKey: "sleep-ai-test-1",
            type: "SleepSessionRecorded.v1",
            occurredAt: summaryZoned.plusHours(7).toInstant().toString(),
            payload: [
                sleepId: "sleep-ai-test-1",
                sleepStart: summaryZoned.minusHours(1).toInstant().toString(),
                sleepEnd: summaryZoned.plusHours(7).toInstant().toString(),
                totalMinutes: 480,
                originPackage: "com.google.android.apps.fitness"
            ]
        ]

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: [stepsEvent, sleepEvent],
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        when: "authenticated request is sent"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        then: "should return 200 OK with AI summary"
        response.statusCode() == 200

        and: "response contains summary text"
        def body = response.body().jsonPath()
        body.getString("date") == dateStr
        body.getBoolean("dataAvailable") == true
        body.getString("summary") == "super dzien! duzo krokow i solidnie sie wyspales 💪"
    }

    def "GET /v1/daily-summaries/{date}/ai-text should return dataAvailable=false when no data exists"() {
        given: "date without any data"
        def dateStr = "2025-12-11"

        when: "authenticated request is sent"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        then: "should return 200 OK with dataAvailable=false"
        response.statusCode() == 200

        and: "response indicates no data"
        def body = response.body().jsonPath()
        body.getString("date") == dateStr
        body.getBoolean("dataAvailable") == false
        body.getString("summary") == "Brak danych na ten dzien"
    }

    def "GET /v1/daily-summaries/{date}/ai-text should include workout data in context"() {
        given: "configured AI response mentioning workout"
        TestChatModelConfiguration.setResponse("solidny trening pleców! 💪")

        and: "workout data exists for the date"
        def summaryDate = LocalDate.of(2025, 12, 12)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)
        def workoutTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(18).toInstant()

        def workoutEvent = [
            idempotencyKey: "workout-ai-test-1",
            type: "WorkoutRecorded.v1",
            occurredAt: workoutTime.toString(),
            payload: [
                workoutId: "workout-ai-test-1",
                performedAt: workoutTime.toString(),
                source: "GYMRUN_SCREENSHOT",
                note: "Plecy i biceps",
                exercises: [
                    [
                        name: "Wiosłowanie sztangą",
                        muscleGroup: "Plecy",
                        orderInWorkout: 1,
                        sets: [
                            [setNumber: 1, weightKg: 70.0, reps: 10, isWarmup: false]
                        ]
                    ]
                ]
            ]
        ]

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: [workoutEvent],
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        when: "authenticated request is sent"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        then: "should return 200 OK with workout summary"
        response.statusCode() == 200
        response.body().jsonPath().getBoolean("dataAvailable") == true
        response.body().jsonPath().getString("summary") == "solidny trening pleców! 💪"
    }

    def "GET /v1/daily-summaries/{date}/ai-text should include meal data in context"() {
        given: "configured AI response mentioning meals"
        TestChatModelConfiguration.setResponse("zdrowe jedzenie dzisiaj, brawo!")

        and: "meal data exists for the date"
        def summaryDate = LocalDate.of(2025, 12, 13)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)
        def mealTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw")).plusHours(12).toInstant()

        def mealEvent = [
            idempotencyKey: "meal-ai-test-1",
            type: "MealRecorded.v1",
            occurredAt: mealTime.toString(),
            payload: [
                title: "Grillowany kurczak z warzywami",
                mealType: "LUNCH",
                caloriesKcal: 450,
                proteinGrams: 40,
                fatGrams: 12,
                carbohydratesGrams: 35,
                healthRating: "VERY_HEALTHY"
            ]
        ]

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: [mealEvent],
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        when: "authenticated request is sent"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        then: "should return 200 OK with meal summary"
        response.statusCode() == 200
        response.body().jsonPath().getBoolean("dataAvailable") == true
        response.body().jsonPath().getString("summary") == "zdrowe jedzenie dzisiaj, brawo!"
    }

    def "GET /v1/daily-summaries/{date}/ai-text should handle comprehensive day data"() {
        given: "configured AI response for a full day"
        TestChatModelConfiguration.setResponse("pełen pakiet dzisiaj - sen, kroki, trening i zdrowe żarcie 🔥")

        and: "comprehensive health data exists for the date"
        def summaryDate = LocalDate.of(2025, 12, 14)
        def dateStr = summaryDate.format(DateTimeFormatter.ISO_DATE)
        def baseTime = summaryDate.atStartOfDay(ZoneId.of("Europe/Warsaw"))

        def events = [
            // Sleep
            [
                idempotencyKey: "sleep-comprehensive-1",
                type: "SleepSessionRecorded.v1",
                occurredAt: baseTime.plusHours(7).toInstant().toString(),
                payload: [
                    sleepId: "sleep-comprehensive-1",
                    sleepStart: baseTime.minusHours(1).toInstant().toString(),
                    sleepEnd: baseTime.plusHours(7).toInstant().toString(),
                    totalMinutes: 480,
                    originPackage: "com.google.android.apps.fitness"
                ]
            ],
            // Steps
            [
                idempotencyKey: "steps-comprehensive-1",
                type: "StepsBucketedRecorded.v1",
                occurredAt: baseTime.plusHours(12).toInstant().toString(),
                payload: [
                    bucketStart: baseTime.plusHours(11).toInstant().toString(),
                    bucketEnd: baseTime.plusHours(12).toInstant().toString(),
                    count: 10000,
                    originPackage: "com.google.android.apps.fitness"
                ]
            ],
            // Active calories
            [
                idempotencyKey: "calories-comprehensive-1",
                type: "ActiveCaloriesBurnedRecorded.v1",
                occurredAt: baseTime.plusHours(12).toInstant().toString(),
                payload: [
                    bucketStart: baseTime.plusHours(11).toInstant().toString(),
                    bucketEnd: baseTime.plusHours(12).toInstant().toString(),
                    energyKcal: 350.0,
                    originPackage: "com.google.android.apps.fitness"
                ]
            ],
            // Workout
            [
                idempotencyKey: "workout-comprehensive-1",
                type: "WorkoutRecorded.v1",
                occurredAt: baseTime.plusHours(18).toInstant().toString(),
                payload: [
                    workoutId: "workout-comprehensive-1",
                    performedAt: baseTime.plusHours(18).toInstant().toString(),
                    source: "GYMRUN_SCREENSHOT",
                    note: "Push day",
                    exercises: [
                        [name: "Bench Press", muscleGroup: "Chest", orderInWorkout: 1, sets: [[setNumber: 1, weightKg: 80.0, reps: 8, isWarmup: false]]]
                    ]
                ]
            ],
            // Meal
            [
                idempotencyKey: "meal-comprehensive-1",
                type: "MealRecorded.v1",
                occurredAt: baseTime.plusHours(13).toInstant().toString(),
                payload: [
                    title: "Salmon with veggies",
                    mealType: "LUNCH",
                    caloriesKcal: 550,
                    proteinGrams: 45,
                    fatGrams: 25,
                    carbohydratesGrams: 30,
                    healthRating: "HEALTHY"
                ]
            ]
        ]

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", groovy.json.JsonOutput.toJson([
            events: events,
            deviceId: DEVICE_ID
        ]))
            .post("/v1/health-events")
            .then()
            .statusCode(200)

        when: "authenticated request is sent"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        then: "should return 200 OK with comprehensive summary"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("date") == dateStr
        body.getBoolean("dataAvailable") == true
        body.getString("summary") == "pełen pakiet dzisiaj - sen, kroki, trening i zdrowe żarcie 🔥"
    }
}
