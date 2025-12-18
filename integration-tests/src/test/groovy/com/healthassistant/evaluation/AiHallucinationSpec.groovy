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
}
