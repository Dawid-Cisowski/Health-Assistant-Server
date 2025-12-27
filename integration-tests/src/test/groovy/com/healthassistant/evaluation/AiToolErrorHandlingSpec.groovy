package com.healthassistant.evaluation

import org.springframework.ai.evaluation.EvaluationRequest
import spock.lang.Requires
import spock.lang.Timeout

import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Integration tests verifying AI assistant gracefully handles missing data and errors.
 *
 * These tests verify:
 * 1. AI gracefully responds when no data exists for requested period
 * 2. AI does not hallucinate data when tools return empty results
 * 3. AI provides helpful responses when data is partially available
 * 4. AI handles edge cases like future dates appropriately
 *
 * Uses the LLM-as-a-Judge pattern with real Gemini API.
 *
 * IMPORTANT: These tests require GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiToolErrorHandlingSpec"
 */
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiToolErrorHandlingSpec extends BaseEvaluationSpec {

    // ==================== No Data Available Tests ====================

    def "AI gracefully handles no steps data"() {
        given: "no steps recorded"
        // database cleaned in setup() - no data

        when: "asking about steps"
        def response = askAssistant("Ile kroków zrobiłem dzisiaj?")
        println "DEBUG: Response: $response"

        then: "AI indicates no data available without hallucinating"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate no step data is available, zero steps, or that it couldn't find any data. It should NOT invent a specific number like 5000, 8000, or 10000 steps.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI gracefully handles no sleep data"() {
        given: "no sleep recorded"
        // database cleaned in setup()

        when: "asking about sleep"
        def response = askAssistant("Jak spałem ostatniej nocy?")
        println "DEBUG: Response: $response"

        then: "AI indicates no sleep data available"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate no sleep data is available or couldn't find sleep records. It should NOT invent hours like 7 or 8 hours of sleep.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI gracefully handles no workout data"() {
        given: "no workouts recorded"
        // database cleaned in setup()

        when: "asking about workouts"
        def response = askAssistant("Jaki trening dzisiaj zrobiłem?")
        println "DEBUG: Response: $response"

        then: "AI indicates no workout data available"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate no workout data is available or no workouts were recorded. It should NOT invent exercises or workout details.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI gracefully handles no meal data"() {
        given: "no meals recorded"
        // database cleaned in setup()

        when: "asking about meals"
        def response = askAssistant("Co dzisiaj jadłem?")
        println "DEBUG: Response: $response"

        then: "AI indicates no meal data available"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate no meal data is available or no meals were recorded. It should NOT invent meals or calorie counts.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Partial Data Tests ====================

    def "AI correctly reports partial data - some days have steps, others don't"() {
        given: "steps only for some days in the week"
        def today = LocalDate.now(POLAND_ZONE)
        submitStepsForDate(today, 8000)
        submitStepsForDate(today.minusDays(2), 6000)
        // Days 1, 3, 4, 5, 6 have no data
        waitForProjections()

        when: "asking about weekly summary"
        def response = askAssistant("Ile kroków zrobiłem w tym tygodniu?")
        println "DEBUG: Response: $response"

        then: "AI reports only the data that exists (~14000 steps from 2 days)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should report approximately 14000 total steps (sum of 8000 and 6000). It should NOT add fabricated data for missing days.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI handles mixed availability - steps yes, calories no"() {
        given: "only steps data exists"
        submitStepsForToday(7500)
        // No calorie data
        waitForProjections()

        when: "asking for daily summary"
        def response = askAssistant("Daj mi podsumowanie dnia")
        println "DEBUG: Response: $response"

        then: "AI reports steps but indicates no calorie data"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should mention approximately 7500 steps. For calories, it should indicate no data or zero - NOT invent a calorie count.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Future Date Tests ====================

    def "AI handles question about future date appropriately"() {
        given: "some today's data exists"
        submitStepsForToday(5000)
        waitForProjections()

        when: "asking about tomorrow"
        def response = askAssistant("Ile kroków zrobię jutro?")
        println "DEBUG: Response: $response"

        then: "AI indicates it cannot predict future data"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate it cannot predict or doesn't have data for future dates. It should NOT invent a step count for tomorrow.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI handles question about next week appropriately"() {
        given: "current data exists"
        submitStepsForToday(6000)
        waitForProjections()

        when: "asking about next week"
        def response = askAssistant("Ile kroków zrobię w przyszłym tygodniu?")
        println "DEBUG: Response: $response"

        then: "AI indicates it cannot predict future"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate it cannot predict future step counts or that this data doesn't exist yet.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Data Integrity Tests ====================

    def "AI does not confuse data between categories"() {
        given: "only step data exists, no calories"
        submitStepsForToday(10000)
        // No calorie data
        waitForProjections()

        when: "asking specifically about calories"
        def response = askAssistant("Ile kalorii spaliłem dzisiaj?")
        println "DEBUG: Response: $response"

        then: "AI does not confuse step count with calories"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate no calorie data is available. It should NOT report 10000 calories (which is the step count). The number 10000 should NOT appear as calories.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI does not confuse data between dates"() {
        given: "steps only for yesterday, not today"
        def today = LocalDate.now(POLAND_ZONE)
        def yesterday = today.minusDays(1)
        submitStepsForDate(yesterday, 12000)
        // No today's data
        waitForProjections()

        when: "asking about today"
        def response = askAssistant("Ile kroków dzisiaj?")
        println "DEBUG: Response: $response"

        then: "AI does not report yesterday's steps as today's"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate no steps for today or zero steps today. It should NOT report 12000 steps (which is yesterday's data) as today's count.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Helpful Response Tests ====================

    def "AI suggests helpful action when no data exists"() {
        given: "completely empty database"
        // database cleaned in setup()

        when: "asking for health summary"
        def response = askAssistant("Pokaż moje dane zdrowotne")
        println "DEBUG: Response: $response"

        then: "AI provides helpful response about missing data"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate no health data is recorded and ideally suggest recording some data. It should NOT invent health statistics.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI distinguishes between zero and missing data"() {
        given: "explicitly zero steps recorded (edge case - bucket with 0 steps)"
        def today = LocalDate.now(POLAND_ZONE)
        submitStepsForDate(today, 0)  // Explicitly zero
        waitForProjections()

        when: "asking about steps"
        def response = askAssistant("Ile kroków dzisiaj?")
        println "DEBUG: Response: $response"

        then: "AI correctly reports zero steps, not 'no data'"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should report zero steps for today. This is different from 'no data' - it should acknowledge that data exists but shows 0.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Multiple Missing Data Types ====================

    def "AI handles comprehensive query with all data types missing"() {
        given: "completely empty database"
        // database cleaned in setup()

        when: "asking comprehensive question"
        def response = askAssistant("Opowiedz mi o mojej aktywności: kroki, sen, treningi i posiłki")
        println "DEBUG: Response: $response"

        then: "AI indicates no data for any category without hallucinating"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate that no data is available for steps, sleep, workouts, and meals. It should NOT invent any statistics for any of these categories.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }
}
