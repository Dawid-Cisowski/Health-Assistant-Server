package com.healthassistant.evaluation

import org.springframework.ai.evaluation.EvaluationRequest
import spock.lang.Requires
import spock.lang.Timeout

import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Integration tests verifying AI assistant correctly recognizes Polish date expressions.
 *
 * These tests verify:
 * 1. "dzisiaj" (today) is correctly interpreted
 * 2. "wczoraj" (yesterday) is correctly interpreted
 * 3. "ostatni tydzień" (last week) returns data for last 7 days
 * 4. "ostatni miesiąc" (last month) returns data for last 30 days
 * 5. Specific date formats are correctly parsed
 *
 * Uses the LLM-as-a-Judge pattern with real Gemini API.
 *
 * IMPORTANT: These tests require GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiDateRecognitionSpec"
 */
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiDateRecognitionSpec extends BaseEvaluationSpec {

    // ==================== "Dzisiaj" (Today) Tests ====================

    def "AI correctly interprets 'dzisiaj' for steps query"() {
        given: "steps recorded today and yesterday"
        def today = LocalDate.now(POLAND_ZONE)
        def yesterday = today.minusDays(1)

        submitStepsForDate(today, 8000)
        submitStepsForDate(yesterday, 5000)
        waitForProjections()

        when: "asking about today's steps in Polish"
        def response = askAssistant("Ile kroków zrobiłem dzisiaj?")
        println "DEBUG: Response: $response"

        then: "AI returns today's steps (8000), not yesterday's (5000)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 8000 steps for today, NOT 5000 (which is yesterday's data)",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI correctly interprets 'dzis' (informal today) for calories query"() {
        given: "calories recorded today"
        submitActiveCalories(450)
        waitForProjections()

        when: "asking about today's calories using informal 'dzis'"
        def response = askAssistant("Ile kalorii spaliłem dzis?")
        println "DEBUG: Response: $response"

        then: "AI returns today's calories"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 450 calories burned today",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== "Wczoraj" (Yesterday) Tests ====================

    def "AI correctly interprets 'wczoraj' for sleep query"() {
        given: "sleep recorded for yesterday"
        submitSleepForLastNight(480) // 8 hours
        waitForProjections()

        when: "asking about yesterday's sleep"
        def response = askAssistant("Ile spałem wczoraj?")
        println "DEBUG: Response: $response"

        then: "AI returns yesterday's sleep (8 hours)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 8 hours (480 minutes) of sleep for yesterday",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI correctly interprets 'wczoraj' for steps query"() {
        given: "steps recorded yesterday and today"
        def today = LocalDate.now(POLAND_ZONE)
        def yesterday = today.minusDays(1)

        submitStepsForDate(today, 10000)
        submitStepsForDate(yesterday, 6500)
        waitForProjections()

        when: "asking about yesterday's steps"
        def response = askAssistant("Ile kroków zrobiłem wczoraj?")
        println "DEBUG: Response: $response"

        then: "AI returns yesterday's steps (6500), not today's (10000)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 6500 steps for yesterday, NOT 10000 (which is today's data)",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== "Ostatni tydzień" (Last Week) Tests ====================

    def "AI correctly interprets 'ostatni tydzień' for steps summary"() {
        given: "steps recorded for last 7 days"
        def today = LocalDate.now(POLAND_ZONE)
        submitStepsForDate(today, 8000)
        submitStepsForDate(today.minusDays(1), 7000)
        submitStepsForDate(today.minusDays(2), 6000)
        submitStepsForDate(today.minusDays(3), 5500)
        submitStepsForDate(today.minusDays(4), 9000)
        submitStepsForDate(today.minusDays(5), 4500)
        submitStepsForDate(today.minusDays(6), 7500)
        waitForProjections()
        // Total: 47500 steps

        when: "asking about last week's steps"
        def response = askAssistant("Ile kroków zrobiłem w ostatnim tygodniu?")
        println "DEBUG: Response: $response"

        then: "AI returns sum of last 7 days (~47500 steps)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 47000-48000 total steps for the last week (sum of 7 days data)",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI correctly interprets 'przez ostatni tydzien' variant"() {
        given: "steps recorded for last 3 days"
        def today = LocalDate.now(POLAND_ZONE)
        submitStepsForDate(today, 5000)
        submitStepsForDate(today.minusDays(1), 6000)
        submitStepsForDate(today.minusDays(2), 4000)
        waitForProjections()
        // Total: 15000 steps

        when: "asking with variant phrasing"
        def response = askAssistant("Ile kroków przez ostatni tydzień?")
        println "DEBUG: Response: $response"

        then: "AI returns data from last 7 days period"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 15000 total steps (or average ~2100/day) for the last week period",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== "Ostatni miesiąc" (Last Month) Tests ====================

    def "AI correctly interprets 'ostatni miesiac' for calorie summary"() {
        given: "calories recorded for several days"
        def today = LocalDate.now(POLAND_ZONE)
        submitActiveCaloriesForDate(today, 400)
        submitActiveCaloriesForDate(today.minusDays(7), 500)
        submitActiveCaloriesForDate(today.minusDays(14), 350)
        submitActiveCaloriesForDate(today.minusDays(21), 600)
        waitForProjections()
        // Total: 1850 calories

        when: "asking about last month's calories"
        def response = askAssistant("Ile kalorii spaliłem w ostatnim miesiącu?")
        println "DEBUG: Response: $response"

        then: "AI returns sum from last 30 days"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 1800-1900 total calories burned over the last month",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Specific Date Tests ====================

    def "AI correctly interprets specific date format 'dd.MM'"() {
        given: "data for specific dates"
        def today = LocalDate.now(POLAND_ZONE)
        def targetDate = today.minusDays(3)
        submitStepsForDate(targetDate, 9500)
        waitForProjections()

        def formattedDate = targetDate.format(java.time.format.DateTimeFormatter.ofPattern("d.MM"))

        when: "asking about specific date"
        def response = askAssistant("Ile kroków zrobiłem ${formattedDate}?")
        println "DEBUG: Response for ${formattedDate}: $response"

        then: "AI returns data for that specific date"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 9500 steps for the specified date",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Relative Date Arithmetic Tests ====================

    def "AI correctly calculates 'przedwczoraj' (day before yesterday)"() {
        given: "steps for day before yesterday"
        def today = LocalDate.now(POLAND_ZONE)
        def dayBeforeYesterday = today.minusDays(2)

        submitStepsForDate(today, 8000)
        submitStepsForDate(today.minusDays(1), 7000)
        submitStepsForDate(dayBeforeYesterday, 3500)
        waitForProjections()

        when: "asking about day before yesterday"
        def response = askAssistant("Ile kroków przedwczoraj?")
        println "DEBUG: Response: $response"

        then: "AI returns steps from 2 days ago (3500)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 3500 steps for the day before yesterday (2 days ago)",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI correctly interprets '3 dni temu' (3 days ago)"() {
        given: "steps for 3 days ago"
        def today = LocalDate.now(POLAND_ZONE)
        def threeDaysAgo = today.minusDays(3)

        submitStepsForDate(today, 9000)
        submitStepsForDate(threeDaysAgo, 4200)
        waitForProjections()

        when: "asking about 3 days ago"
        def response = askAssistant("Ile kroków zrobiłem 3 dni temu?")
        println "DEBUG: Response: $response"

        then: "AI returns steps from 3 days ago (4200)"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should mention approximately 4200 steps for 3 days ago",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Edge Case Tests ====================

    def "AI handles missing data for requested date gracefully"() {
        given: "no data recorded for yesterday"
        // only today's data
        submitStepsForToday(5000)
        waitForProjections()

        when: "asking about yesterday with no data"
        def response = askAssistant("Ile kroków wczoraj?")
        println "DEBUG: Response: $response"

        then: "AI indicates no data or zero, does not hallucinate"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Response should indicate no data, zero steps, or missing data for yesterday - NOT invent a number",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Helper Methods ====================

    void submitActiveCaloriesForDate(LocalDate date, double kcal) {
        def bucketEnd = date.atTime(14, 0).atZone(POLAND_ZONE).toInstant()
        def bucketStart = bucketEnd.minusSeconds(3600)

        def request = [
            deviceId: TEST_DEVICE_ID,
            events: [[
                idempotencyKey: UUID.randomUUID().toString(),
                type: "ActiveCaloriesBurnedRecorded.v1",
                occurredAt: bucketEnd.toString(),
                payload: [
                    bucketStart: bucketStart.toString(),
                    bucketEnd: bucketEnd.toString(),
                    energyKcal: kcal,
                    originPackage: "com.test"
                ]
            ]]
        ]
        submitEventMap(request)
    }
}
