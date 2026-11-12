package com.healthassistant

import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Title

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Integration tests for AI Assistant mutation tools.
 * Tests tool methods directly via Spring ApplicationContext
 * to verify data is correctly persisted through facades.
 *
 * Uses Groovy duck typing to access package-private HealthTools class.
 */
@Title("Feature: AI Assistant Mutation Tools")
class AssistantMutationToolsSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-assistant-mutation"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    @Autowired
    ApplicationContext applicationContext

    private Object healthTools

    def setup() {
        healthTools = applicationContext.getBean("healthTools")
        cleanupEventsForDevice(DEVICE_ID)
        cleanupAllProjectionsForDevice(DEVICE_ID)
    }

    private ToolContext createToolContext() {
        return new ToolContext(Map.of("deviceId", DEVICE_ID))
    }

    private boolean isMutationSuccess(Object result) {
        return result.class.simpleName == "MutationSuccess"
    }

    private boolean isToolError(Object result) {
        return result.class.simpleName == "ToolError"
    }

    // ===================== recordMeal =====================

    def "recordMeal should create a meal and return MutationSuccess"() {
        when: "recording a meal through the tool"
        def result = healthTools.recordMeal(
                "Chicken with rice",
                "LUNCH",
                "500",
                "40",
                "15",
                "55",
                "HEALTHY",
                null,
                createToolContext()
        )

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message() == "Meal recorded successfully"
        result.data() != null

        and: "meal is persisted and visible via facade"
        def detail = mealsFacade.getDailyDetail(DEVICE_ID, LocalDate.now())
        detail.totalMealCount() >= 1
        detail.meals().any { it.title() == "Chicken with rice" && it.caloriesKcal() == 500 }
    }

    def "recordMeal should create a meal with custom occurredAt"() {
        given: "a timestamp from yesterday"
        def yesterday = Instant.now().minus(1, ChronoUnit.DAYS).toString()

        when: "recording a meal with custom timestamp"
        def result = healthTools.recordMeal(
                "Yesterday's dinner",
                "DINNER",
                "700",
                "50",
                "25",
                "60",
                "NEUTRAL",
                yesterday,
                createToolContext()
        )

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
    }

    def "recordMeal should return ToolError for invalid mealType"() {
        when: "recording a meal with invalid meal type"
        def result = healthTools.recordMeal(
                "Some food",
                "INVALID_TYPE",
                "300",
                "20",
                "10",
                "30",
                "HEALTHY",
                null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("Invalid mealType")
    }

    def "recordMeal should return ToolError for negative calories"() {
        when: "recording a meal with negative calories"
        def result = healthTools.recordMeal(
                "Some food",
                "LUNCH",
                "-100",
                "20",
                "10",
                "30",
                "HEALTHY",
                null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("non-negative")
    }

    def "recordMeal should return ToolError for non-numeric calories"() {
        when: "recording a meal with non-numeric calories"
        def result = healthTools.recordMeal(
                "Some food",
                "LUNCH",
                "abc",
                "20",
                "10",
                "30",
                "HEALTHY",
                null,
                createToolContext()
        )

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("caloriesKcal")
    }

    // ===================== getMealsDailyDetail =====================

    def "getMealsDailyDetail should return meals with eventIds"() {
        given: "a meal exists for today"
        healthTools.recordMeal("Test breakfast", "BREAKFAST", "300", "15", "10", "35", "HEALTHY", null, createToolContext())

        when: "getting daily detail"
        def today = LocalDate.now().toString()
        def result = healthTools.getMealsDailyDetail(today, createToolContext())

        then: "result contains meals with eventIds"
        !isToolError(result)
        result.totalMealCount() >= 1
        result.meals().every { it.eventId() != null && !it.eventId().isEmpty() }
    }

    def "getMealsDailyDetail should return ToolError for invalid date"() {
        when: "getting daily detail with invalid date"
        def result = healthTools.getMealsDailyDetail("not-a-date", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("Invalid date format")
    }

    // ===================== updateMeal =====================

    def "updateMeal should modify an existing meal"() {
        given: "a meal exists"
        def createResult = healthTools.recordMeal("Original meal", "LUNCH", "400", "30", "15", "45", "NEUTRAL", null, createToolContext())
        def eventId = createResult.data().eventId()

        when: "updating the meal"
        def result = healthTools.updateMeal(
                eventId,
                "Updated meal",
                "DINNER",
                "600",
                "45",
                "20",
                "55",
                "HEALTHY",
                createToolContext()
        )

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message() == "Meal updated successfully"
    }

    def "updateMeal should return ToolError for empty eventId"() {
        when: "updating with empty eventId"
        def result = healthTools.updateMeal("", "Title", "LUNCH", "400", "30", "15", "45", "NEUTRAL", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("eventId")
    }

    // ===================== deleteMeal =====================

    def "deleteMeal should remove a meal"() {
        given: "a meal exists"
        def createResult = healthTools.recordMeal("Meal to delete", "SNACK", "200", "10", "5", "25", "NEUTRAL", null, createToolContext())
        def eventId = createResult.data().eventId()

        when: "deleting the meal"
        def result = healthTools.deleteMeal(eventId, createToolContext())

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message() == "Meal deleted successfully"
    }

    def "deleteMeal should return ToolError for empty eventId"() {
        when: "deleting with empty eventId"
        def result = healthTools.deleteMeal("", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("eventId")
    }

    // ===================== recordWeight =====================

    def "recordWeight should record a weight measurement"() {
        when: "recording weight through the tool"
        def result = healthTools.recordWeight("82.5", null, null, createToolContext())

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message().contains("82.5 kg")
        result.data().weightKg() == new BigDecimal("82.5")
    }

    def "recordWeight should record weight with custom timestamp"() {
        given: "a timestamp from this morning"
        def thismorning = Instant.now().minus(3, ChronoUnit.HOURS).toString()

        when: "recording weight with timestamp"
        def result = healthTools.recordWeight("75.0", thismorning, null, createToolContext())

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
    }

    def "recordWeight should return ToolError for invalid weight"() {
        when: "recording weight with non-numeric value"
        def result = healthTools.recordWeight("abc", null, null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("weightKg")
    }

    def "recordWeight should return ToolError for zero weight"() {
        when: "recording zero weight"
        def result = healthTools.recordWeight("0", null, null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("positive")
    }

    def "recordWeight should return ToolError for weight exceeding 500 kg"() {
        when: "recording weight over 500"
        def result = healthTools.recordWeight("501", null, null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("between 1 and 500")
    }

    def "recordWeight should store full body composition from scale screenshot"() {
        given: "body composition read from a smart scale screenshot"
        def measuredAt = Instant.now().minus(1, ChronoUnit.HOURS).toString()
        def bodyCompositionJson = '''{
            "score": 88, "bmi": 24.1, "bodyFatPercent": 19.5, "musclePercent": 53.2,
            "hydrationPercent": 58.1, "boneMassKg": 3.1, "bmrKcal": 1720, "visceralFatLevel": 9,
            "subcutaneousFatPercent": 17.2, "proteinPercent": 17.4, "metabolicAge": 30,
            "idealWeightKg": 74.0, "weightControlKg": -4.2, "fatMassKg": 15.6,
            "leanBodyMassKg": 64.4, "muscleMassKg": 42.5, "proteinMassKg": 13.9, "bodyType": "STANDARD"
        }'''

        when: "recording weight with body composition"
        def result = healthTools.recordWeight("80.0", measuredAt, bodyCompositionJson, createToolContext())

        then: "result is a MutationSuccess"
        isMutationSuccess(result)

        when: "reading weight data back"
        def today = LocalDate.now().toString()
        def weekAgo = LocalDate.now().minusDays(7).toString()
        def m = healthTools.getWeightData(weekAgo, today, createToolContext()).measurements().first()

        then: "all body composition fields are persisted"
        m.weightKg() == new BigDecimal("80.0")
        m.score() == 88
        m.bodyFatPercent() == new BigDecimal("19.5")
        m.musclePercent() == new BigDecimal("53.2")
        m.visceralFatLevel() == 9
        m.metabolicAge() == 30
        m.muscleMassKg() == new BigDecimal("42.5")
        m.bodyType() == "STANDARD"
    }

    def "recordWeight should return ToolError for invalid bodyCompositionJson"() {
        when:
        def result = healthTools.recordWeight("80.0", null, "not-json", createToolContext())

        then:
        isToolError(result)
        result.message().contains("bodyCompositionJson")
    }

    def "recordWeight should return ToolError for out-of-range body composition value"() {
        when:
        def result = healthTools.recordWeight("80.0", null, '{"bodyFatPercent": 150}', createToolContext())

        then:
        isToolError(result)
    }

    def "recordWeight should return ToolError for unknown body composition field"() {
        when:
        def result = healthTools.recordWeight("80.0", null, '{"weightKg": 90, "source": "X"}', createToolContext())

        then:
        isToolError(result)
        result.message().contains("bodyCompositionJson")
    }

    // ===================== recordWorkout =====================

    def "recordWorkout should record a workout with exercises"() {
        given: "workout data"
        def performedAt = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        def exercisesJson = '''[
            {
                "name": "Bench Press",
                "orderInWorkout": 1,
                "sets": [
                    {"setNumber": 1, "weightKg": 80.0, "reps": 10, "isWarmup": false},
                    {"setNumber": 2, "weightKg": 80.0, "reps": 8, "isWarmup": false},
                    {"setNumber": 3, "weightKg": 80.0, "reps": 6, "isWarmup": false}
                ]
            }
        ]'''

        when: "recording workout through the tool"
        def result = healthTools.recordWorkout(performedAt, "Chest day", exercisesJson, createToolContext())

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message().contains("1 exercises")
        result.message().contains("3 sets")
        result.data().exerciseCount() == 1
        result.data().totalSets() == 3
    }

    def "getWorkoutData should return per-set breakdown (weight, reps, warmup)"() {
        given: "a recorded workout"
        def performedAt = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        def exercisesJson = '''[
            {"name": "Bench Press", "orderInWorkout": 1, "sets": [
                {"setNumber": 1, "weightKg": 40.0, "reps": 12, "isWarmup": true},
                {"setNumber": 2, "weightKg": 80.0, "reps": 8, "isWarmup": false}
            ]},
            {"name": "Squat", "orderInWorkout": 2, "sets": [
                {"setNumber": 1, "weightKg": 100.0, "reps": 5, "isWarmup": false}
            ]}
        ]'''
        healthTools.recordWorkout(performedAt, "Mixed", exercisesJson, createToolContext())

        when: "reading workouts through the tool"
        def today = LocalDate.now().toString()
        def weekAgo = LocalDate.now().minusDays(7).toString()
        def workouts = healthTools.getWorkoutData(weekAgo, today, createToolContext())

        then: "each exercise contains its sets"
        workouts.size() == 1
        def bench = workouts.first().exercises().find { it.orderInWorkout() == 1 }
        bench.sets().size() == 2
        bench.sets()[0].setNumber() == 1
        bench.sets()[0].weightKg() == new BigDecimal("40.0")
        bench.sets()[0].reps() == 12
        bench.sets()[0].isWarmup()
        bench.sets()[1].reps() == 8
        def squat = workouts.first().exercises().find { it.orderInWorkout() == 2 }
        squat.sets().size() == 1
        squat.sets()[0].reps() == 5
    }

    def "recordWorkout should return ToolError for empty exercises"() {
        given: "empty exercises"
        def performedAt = Instant.now().toString()

        when: "recording workout with empty exercises"
        def result = healthTools.recordWorkout(performedAt, null, "[]", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("empty")
    }

    def "recordWorkout should return ToolError for invalid JSON"() {
        when: "recording workout with invalid JSON"
        def result = healthTools.recordWorkout(Instant.now().toString(), null, "not json", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("exercisesJson")
    }

    def "recordWorkout should return ToolError for invalid performedAt"() {
        when: "recording workout with invalid timestamp"
        def result = healthTools.recordWorkout("not-a-timestamp", null, '[{"name":"Squat","orderInWorkout":1,"sets":[{"setNumber":1,"weightKg":100,"reps":5,"isWarmup":false}]}]', createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("performedAt")
    }

    // ===================== deleteWorkout =====================

    def "deleteWorkout should remove a workout"() {
        given: "a workout exists"
        def performedAt = Instant.now().minus(1, ChronoUnit.HOURS).toString()
        def exercisesJson = '[{"name":"Squat","orderInWorkout":1,"sets":[{"setNumber":1,"weightKg":100.0,"reps":5,"isWarmup":false}]}]'
        def createResult = healthTools.recordWorkout(performedAt, "Test", exercisesJson, createToolContext())
        def eventId = createResult.data().eventId()

        when: "deleting the workout"
        def result = healthTools.deleteWorkout(eventId, createToolContext())

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message() == "Workout deleted successfully"
    }

    def "deleteWorkout should return ToolError for empty eventId"() {
        when: "deleting with empty eventId"
        def result = healthTools.deleteWorkout("", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("eventId")
    }

    // ===================== recordSleep =====================

    def "recordSleep should record a sleep session"() {
        given: "sleep times"
        def sleepStart = Instant.now().minus(9, ChronoUnit.HOURS).toString()
        def sleepEnd = Instant.now().minus(1, ChronoUnit.HOURS).toString()

        when: "recording sleep through the tool"
        def result = healthTools.recordSleep(sleepStart, sleepEnd, createToolContext())

        then: "result is a MutationSuccess"
        isMutationSuccess(result)
        result.message().contains("minutes")
        result.data().totalMinutes() == 480  // 8 hours
    }

    def "recordSleep should return ToolError when sleepEnd is before sleepStart"() {
        given: "invalid sleep times (end before start)"
        def sleepStart = Instant.now().toString()
        def sleepEnd = Instant.now().minus(1, ChronoUnit.HOURS).toString()

        when: "recording sleep with invalid times"
        def result = healthTools.recordSleep(sleepStart, sleepEnd, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("sleepEnd must be after sleepStart")
    }

    def "recordSleep should return ToolError for invalid timestamp"() {
        when: "recording sleep with invalid timestamps"
        def result = healthTools.recordSleep("not-a-timestamp", "also-not", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("sleepStart")
    }

    // ===================== Title Validation =====================

    def "recordMeal should return ToolError for null title"() {
        when: "recording a meal with null title"
        def result = healthTools.recordMeal(null, "LUNCH", "300", "20", "10", "30", "HEALTHY", null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("title")
    }

    def "recordMeal should return ToolError for blank title"() {
        when: "recording a meal with blank title"
        def result = healthTools.recordMeal("   ", "LUNCH", "300", "20", "10", "30", "HEALTHY", null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("title")
    }

    def "recordMeal should return ToolError for title exceeding 200 characters"() {
        given: "a very long title"
        def longTitle = "A" * 201

        when: "recording a meal with long title"
        def result = healthTools.recordMeal(longTitle, "LUNCH", "300", "20", "10", "30", "HEALTHY", null, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("200")
    }

    def "updateMeal should return ToolError for title exceeding 200 characters"() {
        given: "a meal exists"
        def createResult = healthTools.recordMeal("Original", "LUNCH", "300", "20", "10", "30", "NEUTRAL", null, createToolContext())
        def eventId = createResult.data().eventId()

        when: "updating with too long title"
        def longTitle = "B" * 201
        def result = healthTools.updateMeal(eventId, longTitle, "LUNCH", "300", "20", "10", "30", "NEUTRAL", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("200")
    }

    // ===================== EventId Validation =====================

    def "deleteMeal should return ToolError for overly long eventId"() {
        when: "deleting with eventId longer than 64 characters"
        def longEventId = "x" * 65
        def result = healthTools.deleteMeal(longEventId, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("too long")
    }

    def "deleteWorkout should return ToolError for overly long eventId"() {
        when: "deleting workout with eventId longer than 64 characters"
        def longEventId = "x" * 65
        def result = healthTools.deleteWorkout(longEventId, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("too long")
    }

    def "deleteMeal should return ToolError for non-existent eventId"() {
        when: "deleting with a fake eventId"
        def result = healthTools.deleteMeal("non-existent-event-id", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("not found")
    }

    def "deleteWorkout should return ToolError for non-existent eventId"() {
        when: "deleting workout with a fake eventId"
        def result = healthTools.deleteWorkout("non-existent-event-id", createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("not found")
    }

    // ===================== Sleep Duration Limit =====================

    def "recordSleep should return ToolError for sleep exceeding 24 hours"() {
        given: "sleep times spanning more than 24 hours"
        def sleepStart = Instant.now().minus(30, ChronoUnit.HOURS).toString()
        def sleepEnd = Instant.now().minus(1, ChronoUnit.HOURS).toString()

        when: "recording overly long sleep"
        def result = healthTools.recordSleep(sleepStart, sleepEnd, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("24 hours")
    }

    // ===================== Workout Temporal Validation =====================

    def "recordWorkout should return ToolError for future workout date"() {
        given: "a future timestamp"
        def futureTimestamp = Instant.now().plus(1, ChronoUnit.DAYS).toString()
        def exercisesJson = '[{"name":"Squat","orderInWorkout":1,"sets":[{"setNumber":1,"weightKg":100,"reps":5,"isWarmup":false}]}]'

        when: "recording workout with future date"
        def result = healthTools.recordWorkout(futureTimestamp, null, exercisesJson, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("future")
    }

    def "recordWorkout should return ToolError for workout older than 30 days"() {
        given: "a timestamp 31 days ago"
        def oldTimestamp = Instant.now().minus(31, ChronoUnit.DAYS).toString()
        def exercisesJson = '[{"name":"Squat","orderInWorkout":1,"sets":[{"setNumber":1,"weightKg":100,"reps":5,"isWarmup":false}]}]'

        when: "recording workout too far in the past"
        def result = healthTools.recordWorkout(oldTimestamp, null, exercisesJson, createToolContext())

        then: "result is a ToolError"
        isToolError(result)
        result.message().contains("30 days")
    }

    // ===================== eventId in WorkoutDetailResponse =====================

    def "getWorkoutData should return eventId in workout responses"() {
        given: "a workout exists"
        def performedAt = Instant.now().minus(1, ChronoUnit.HOURS).toString()
        def exercisesJson = '[{"name":"Deadlift","orderInWorkout":1,"sets":[{"setNumber":1,"weightKg":120.0,"reps":5,"isWarmup":false}]}]'
        healthTools.recordWorkout(performedAt, "Back day", exercisesJson, createToolContext())

        when: "getting workout data via REST API"
        def today = LocalDate.now().toString()
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts?from=${today}&to=${today}")
                .get("/v1/workouts?from=${today}&to=${today}")
                .then()
                .extract()

        then: "response contains workouts with eventId"
        response.statusCode() == 200
        def workouts = response.body().jsonPath().getList('$')
        workouts.size() >= 1
        workouts.every { it.eventId != null && !it.eventId.isEmpty() }
    }

    // ===================== Full flow: record, query, update, delete meal =====================

    def "full meal flow: record, query, delete through assistant tools"() {
        when: "recording a meal"
        def createResult = healthTools.recordMeal(
                "Scrambled eggs", "BREAKFAST", "350", "25", "20", "5", "HEALTHY", null, createToolContext()
        )

        then: "meal is created"
        isMutationSuccess(createResult)

        when: "querying daily detail to get eventId"
        def today = LocalDate.now().toString()
        def dailyDetail = healthTools.getMealsDailyDetail(today, createToolContext())

        then: "daily detail contains the meal with eventId"
        dailyDetail.meals().any { it.title() == "Scrambled eggs" }
        def mealEventId = dailyDetail.meals().find { it.title() == "Scrambled eggs" }.eventId()
        mealEventId != null

        when: "deleting the meal using eventId from daily detail"
        def deleteResult = healthTools.deleteMeal(mealEventId, createToolContext())

        then: "meal is deleted"
        isMutationSuccess(deleteResult)
        deleteResult.message() == "Meal deleted successfully"
    }

    def "full meal flow: record, query, update through assistant tools"() {
        when: "recording a meal"
        def createResult = healthTools.recordMeal(
                "Original breakfast", "BREAKFAST", "300", "20", "10", "40", "NEUTRAL", null, createToolContext()
        )

        then: "meal is created"
        isMutationSuccess(createResult)

        when: "querying daily detail to get eventId"
        def today = LocalDate.now().toString()
        def dailyDetail = healthTools.getMealsDailyDetail(today, createToolContext())

        then: "daily detail contains the meal"
        dailyDetail.meals().any { it.title() == "Original breakfast" }
        def mealEventId = dailyDetail.meals().find { it.title() == "Original breakfast" }.eventId()

        when: "updating the meal"
        def updateResult = healthTools.updateMeal(
                mealEventId, "Updated breakfast with toast", "BREAKFAST", "450", "28", "22", "35", "HEALTHY", createToolContext()
        )

        then: "meal is updated successfully"
        isMutationSuccess(updateResult)
        updateResult.message() == "Meal updated successfully"

        and: "updated meal is visible in daily detail"
        def updatedDetail = healthTools.getMealsDailyDetail(today, createToolContext())
        updatedDetail.meals().any { it.title() == "Updated breakfast with toast" && it.caloriesKcal() == 450 }
    }
}
