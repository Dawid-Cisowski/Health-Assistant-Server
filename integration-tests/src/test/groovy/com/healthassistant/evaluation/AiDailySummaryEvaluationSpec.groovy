package com.healthassistant.evaluation

import org.springframework.ai.evaluation.EvaluationRequest
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Requires
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

/**
 * Integration tests evaluating AI-generated daily summary quality.
 *
 * These tests use the LLM-as-a-Judge pattern to evaluate if AI summaries:
 * - Accurately reflect the actual health data
 * - Acknowledge missing data appropriately
 * - Use friendly, supportive tone
 * - Are grammatically correct in Polish
 * - Do not hallucinate data
 *
 * IMPORTANT: These tests require GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiDailySummaryEvaluationSpec"
 */
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiDailySummaryEvaluationSpec extends BaseEvaluationSpec {

    @Autowired
    DailySummaryEvaluator dailySummaryEvaluator

    def "AI summary accurately reflects complete health data"() {
        given: "comprehensive health data for today"
        submitStepsForToday(10500)
        submitActiveCalories(420)
        submitSleepForLastNight(450) // 7.5 hours
        submitWorkout("Bench Press", 3, 10, 60.0)
        submitMeal("Lunch", "LUNCH", 650, 35, 20, 60)
        waitForProjections()

        def actualData = """
            Steps: 10500
            Active Calories: 420 kcal
            Sleep: 450 minutes (7.5 hours)
            Workout: Bench Press, 3 sets x 10 reps @ 60kg
            Meal: Lunch, 650 kcal, 35g protein, 20g fat, 60g carbs
        """

        when: "asking for a daily summary"
        def summary = askAssistant("Daj mi podsumowanie mojego dnia zdrowotnego na dziś")
        println "DEBUG: AI Summary: $summary"

        then: "summary accurately reflects the data"
        def evaluation = dailySummaryEvaluator.evaluate(
                new EvaluationRequest(actualData, [], summary)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI summary handles partial data gracefully"() {
        given: "only steps data exists"
        submitStepsForToday(8000)
        waitForProjections()

        def actualData = """
            Steps: 8000
            Active Calories: none
            Sleep: none
            Workout: none
            Meals: none
        """

        when: "asking for a daily summary"
        def summary = askAssistant("Podsumuj moje dane zdrowotne z dzisiaj")
        println "DEBUG: AI Summary: $summary"

        then: "summary acknowledges missing data"
        def evaluation = dailySummaryEvaluator.evaluate(
                new EvaluationRequest(actualData, [], summary)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI summary does not hallucinate missing workout"() {
        given: "only steps and calories, no workout"
        submitStepsForToday(7000)
        submitActiveCalories(300)
        waitForProjections()

        def actualData = """
            Steps: 7000
            Active Calories: 300 kcal
            Sleep: none
            Workout: NONE - no workout recorded
            Meals: none
        """

        when: "asking for a comprehensive summary"
        def summary = askAssistant("Daj mi pełne podsumowanie zdrowotne z dzisiaj")
        println "DEBUG: AI Summary: $summary"

        then: "summary does not mention specific workout details"
        def evaluation = dailySummaryEvaluator.evaluate(
                new EvaluationRequest(actualData, [], summary)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI summary correctly converts minutes to hours"() {
        given: "sleep data in minutes"
        submitSleepForLastNight(405) // 6h 45min
        waitForProjections()

        def actualData = """
            Sleep: 405 minutes = 6 hours 45 minutes (or approximately 6.75 hours / ~7 hours)
        """

        when: "asking about sleep"
        def summary = askAssistant("Ile spałem wczoraj?")
        println "DEBUG: AI Summary: $summary"

        then: "summary uses reasonable time format"
        def evaluation = dailySummaryEvaluator.evaluate(
                new EvaluationRequest(actualData, [], summary)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI summary responds in Polish when asked in Polish"() {
        given: "health data exists"
        submitStepsForToday(9000)
        waitForProjections()

        def actualData = """
            Steps: 9000
            Language: Response should be in Polish
        """

        when: "asking in Polish"
        def summary = askAssistant("Ile kroków zrobiłem dzisiaj? Odpowiedz po polsku.")
        println "DEBUG: AI Summary: $summary"

        then: "summary is in Polish and accurate"
        def evaluation = dailySummaryEvaluator.evaluate(
                new EvaluationRequest(actualData, [], summary)
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }
}
