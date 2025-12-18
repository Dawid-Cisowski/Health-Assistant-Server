package com.healthassistant.evaluation

import org.springframework.ai.evaluation.EvaluationRequest
import spock.lang.Requires
import spock.lang.Timeout

import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Integration tests verifying AI assistant does not hallucinate health data.
 *
 * These tests use the LLM-as-a-Judge pattern:
 * 1. Setup golden data (known facts) in the database
 * 2. Verify data is stored via facade call
 * 3. Ask the AI assistant a question about that data
 * 4. Use LLM judge to verify the response matches the golden data
 *
 * IMPORTANT: These tests require GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:evaluationTest
 */
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class AiHallucinationSpec extends BaseEvaluationSpec {

    def "AI returns correct step count - 1000 steps today"() {
        given: "1000 steps recorded today"
        submitStepsForToday(1000)
        waitForProjections()

        and: "verify data exists via facade"
        def today = LocalDate.now(POLAND_ZONE)
        def stepsData = stepsFacade.getRangeSummary(today, today)
        assert stepsData != null : "Steps data should exist"
        println "DEBUG: Steps facade returned: totalSteps=${stepsData.totalSteps()}"

        when: "asking the AI assistant"
        def response = askAssistant("How many steps did I take today?")
        println "DEBUG: AI response: $response"

        then: "LLM judge verifies response contains correct data"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("User took exactly 1000 steps today", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI returns correct calorie count - 500 calories today"() {
        given: "500 active calories recorded today"
        submitActiveCalories(500)
        waitForProjections()

        when: "asking the AI assistant"
        def response = askAssistant("How many calories did I burn today?")
        println "DEBUG: AI response: $response"

        then: "LLM judge verifies response contains correct data"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("User burned 500 active calories today", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI returns correct sleep data - 7 hours yesterday"() {
        given: "7 hours of sleep recorded for yesterday"
        submitSleepForLastNight(420)
        waitForProjections()

        and: "verify data exists via facade"
        def yesterday = LocalDate.now(POLAND_ZONE).minusDays(1)
        def sleepData = sleepFacade.getRangeSummary(yesterday, yesterday)
        assert sleepData.totalSleepMinutes() == 420 : "Sleep should be 420 minutes, got ${sleepData.totalSleepMinutes()}"
        println "DEBUG: Sleep facade returned for ${yesterday}: totalSleepMinutes=${sleepData.totalSleepMinutes()}"

        when: "asking the AI assistant"
        def response = askAssistant("How much sleep did I get yesterday?")
        println "DEBUG: AI response: $response"

        then: "LLM judge verifies response contains correct data"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("User slept for approximately 7 hours (420 minutes)", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI returns correct workout data - bench press"() {
        given: "workout recorded today"
        submitWorkout("Bench Press", 3, 10, 60.0)
        waitForProjections()

        and: "verify data exists via facade"
        def today = LocalDate.now(POLAND_ZONE)
        def workouts = workoutFacade.getWorkoutsByDateRange(today, today)
        assert workouts.size() == 1 : "Should have 1 workout, got ${workouts.size()}"
        println "DEBUG: Workout facade returned for ${today}: ${workouts.size()} workouts"
        workouts.each { println "DEBUG: Workout: $it" }

        when: "asking the AI assistant"
        def response = askAssistant("What workout did I do today?")
        println "DEBUG: AI response: $response"

        then: "LLM judge verifies response contains correct data"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("User did bench press with 3 sets of 10 reps at 60kg", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI returns correct meal data - breakfast"() {
        given: "breakfast recorded today"
        submitMeal("Oatmeal with fruits", "BREAKFAST", 450, 25, 15, 50)
        waitForProjections()

        and: "verify data exists via facade"
        def today = LocalDate.now(POLAND_ZONE)
        def mealsData = mealsFacade.getRangeSummary(today, today)
        assert mealsData.totalMealCount() == 1 : "Should have 1 meal, got ${mealsData.totalMealCount()}"
        println "DEBUG: Meals facade returned for ${today}: totalMealCount=${mealsData.totalMealCount()}, totalCalories=${mealsData.totalCaloriesKcal()}"

        when: "asking the AI assistant"
        def response = askAssistant("What did I eat for breakfast today?")
        println "DEBUG: AI response: $response"

        then: "LLM judge verifies response contains correct data"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("User had a meal with approximately 450 calories, 25g protein, 15g fat, and 50g carbohydrates", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== No Data / Hallucination Prevention Tests ====================

    def "AI does not hallucinate steps when no data exists"() {
        given: "no steps recorded"
        // nothing submitted - data cleaned in setup()

        when: "asking about today's steps"
        def response = askAssistant("How many steps did I take today?")
        println "DEBUG: AI response: $response"

        then: "AI should indicate no data, not invent numbers"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("AI should indicate no data available, zero steps, or that it cannot find step data - NOT invent a specific number like 5000 or 10000", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI does not hallucinate sleep when no data exists"() {
        given: "no sleep recorded"
        // nothing submitted

        when: "asking about last night's sleep"
        def response = askAssistant("How did I sleep last night?")
        println "DEBUG: AI response: $response"

        then: "AI should indicate no data, not invent hours"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("AI should indicate no sleep data available or zero sleep recorded - NOT invent hours like 7 or 8", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Date Range Tests ====================

    def "AI returns correct weekly steps summary"() {
        given: "steps recorded for multiple days"
        def today = LocalDate.now(POLAND_ZONE)
        submitStepsForDate(today, 5000)
        submitStepsForDate(today.minusDays(1), 8000)
        submitStepsForDate(today.minusDays(2), 6000)
        waitForProjections()

        and: "verify data exists"
        def weekStart = today.minusDays(6)
        def stepsData = stepsFacade.getRangeSummary(weekStart, today)
        println "DEBUG: Steps facade returned for ${weekStart} to ${today}: totalSteps=${stepsData.totalSteps()}"

        when: "asking about this week's steps"
        def response = askAssistant("How many steps did I take this week?")
        println "DEBUG: AI response: $response"

        then: "AI returns approximately 19000 steps (5000+8000+6000)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("User took approximately 19000 total steps this week (sum of 5000, 8000, and 6000)", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Multiple Records Tests ====================

    def "AI returns correct summary for multiple meals"() {
        given: "three meals recorded today"
        submitMeal("Oatmeal", "BREAKFAST", 400, 15, 10, 60)
        submitMeal("Chicken Salad", "LUNCH", 550, 40, 20, 30)
        submitMeal("Pasta", "DINNER", 700, 25, 15, 90)
        waitForProjections()

        and: "verify data exists"
        def today = LocalDate.now(POLAND_ZONE)
        def mealsData = mealsFacade.getRangeSummary(today, today)
        assert mealsData.totalMealCount() == 3 : "Should have 3 meals"
        println "DEBUG: Meals facade: ${mealsData.totalMealCount()} meals, ${mealsData.totalCaloriesKcal()} total calories"

        when: "asking about today's meals"
        def response = askAssistant("How many calories did I eat today?")
        println "DEBUG: AI response: $response"

        then: "AI returns approximately 1650 total calories (400+550+700)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("User consumed approximately 1650 total calories today from 3 meals", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Daily Summary Tests ====================

    def "AI returns correct daily summary with mixed data"() {
        given: "various health data recorded today"
        submitStepsForToday(7500)
        submitActiveCalories(350)
        submitMeal("Lunch", "LUNCH", 600, 30, 20, 50)
        waitForProjections()

        when: "asking for daily summary"
        def response = askAssistant("Give me a summary of my health data for today")
        println "DEBUG: AI response: $response"

        then: "AI returns summary with correct data points"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest("Summary should include: approximately 7500 steps, approximately 350 active calories burned, and meal with approximately 600 calories", [], response)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }
}
