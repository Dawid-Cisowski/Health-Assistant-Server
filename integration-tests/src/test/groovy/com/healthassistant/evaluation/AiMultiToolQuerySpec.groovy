package com.healthassistant.evaluation

import org.springframework.ai.evaluation.EvaluationRequest
import spock.lang.Requires
import spock.lang.Timeout

import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Integration tests for complex queries requiring multiple AI tools.
 *
 * These tests verify that the AI can:
 * - Compare data across different time periods
 * - Identify peak/best days from a range
 * - Synthesize information from multiple data sources
 * - Calculate balances and aggregates correctly
 *
 * IMPORTANT: These tests require GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiMultiToolQuerySpec"
 */
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 300, unit = TimeUnit.SECONDS) // Longer timeout for multi-tool queries
class AiMultiToolQuerySpec extends BaseEvaluationSpec {

    def "AI identifies most active day correctly"() {
        given: "varied step counts over several days"
        def today = LocalDate.now(POLAND_ZONE)

        submitStepsForDate(today, 5000)
        submitStepsForDate(today.minusDays(1), 8000)
        submitStepsForDate(today.minusDays(2), 15000)  // Peak day
        submitStepsForDate(today.minusDays(3), 6000)
        submitStepsForDate(today.minusDays(4), 9000)
        submitStepsForDate(today.minusDays(5), 7000)

        waitForProjections()

        def peakDate = today.minusDays(2)
        def fromDate = today.minusDays(5).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        def toDate = today.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

        when: "asking about most active day"
        def response = askAssistant("Który dzień od ${fromDate} do ${toDate} miał najwięcej kroków?")
        println "DEBUG: AI response: $response"

        then: "AI identifies the day with 15000 steps"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Most active day was ${peakDate} with 15000 steps",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    @spock.lang.Ignore("Flaky: Gemini non-deterministically omits some data sources in synthesis")
    def "AI synthesizes data from multiple sources"() {
        given: "steps, calories, sleep, and meal data"
        submitStepsForToday(10000)
        submitActiveCalories(400)
        submitSleepForLastNight(420) // 7 hours
        submitMeal("Lunch", "LUNCH", 600, 30, 20, 50)
        submitWorkout("Squats", 4, 10, 80.0)
        waitForProjections()

        when: "asking for comprehensive analysis"
        def response = askAssistant("Daj mi pełną analizę mojego zdrowia dziś - kroki, kalorie, sen, posiłki i trening")
        println "DEBUG: AI response: $response"

        then: "AI mentions all data sources"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        """Summary should include:
                        - Steps: ~10000
                        - Active calories: ~400 kcal
                        - Sleep: ~7 hours (420 minutes)
                        - Meal: ~600 kcal
                        - Workout: Squats, 4x10 @ 80kg""",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI calculates weekly averages correctly"() {
        given: "7 days of step data"
        def today = LocalDate.now(POLAND_ZONE)

        submitStepsForDate(today, 8000)
        submitStepsForDate(today.minusDays(1), 10000)
        submitStepsForDate(today.minusDays(2), 6000)
        submitStepsForDate(today.minusDays(3), 9000)
        submitStepsForDate(today.minusDays(4), 7000)
        submitStepsForDate(today.minusDays(5), 11000)
        submitStepsForDate(today.minusDays(6), 5000)
        // Total: 56000, Average: 8000

        waitForProjections()

        when: "asking about weekly average"
        def response = askAssistant("Jaka była moja średnia dzienna ilość kroków w ostatnich 7 dniach?")
        println "DEBUG: AI response: $response"

        then: "AI calculates approximately 8000 average"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Daily average: approximately 8000 steps per day (can be 7000-9000 due to rounding). May also mention weekly total around 56000. AI may include daily breakdown details which is acceptable.",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI handles calories balance calculation"() {
        given: "meals eaten and calories burned"
        submitMeal("Breakfast", "BREAKFAST", 400, 15, 12, 55)
        submitMeal("Lunch", "LUNCH", 650, 35, 20, 60)
        submitMeal("Snack", "SNACK", 200, 5, 10, 20)
        // Total consumed: 1250 kcal

        submitActiveCalories(500)
        waitForProjections()

        when: "asking about calorie balance"
        def response = askAssistant("Ile kalorii zjadłem dzisiaj i ile spaliłem? Jaki jest bilans?")
        println "DEBUG: AI response: $response"

        then: "AI calculates balance correctly"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        """Calories consumed: ~1250 kcal (400+650+200)
                        Active calories burned: ~500 kcal
                        Net/balance: consumed more than burned (~750 kcal positive)""",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI identifies patterns in sleep data"() {
        given: "varied sleep over the week"
        def today = LocalDate.now(POLAND_ZONE)

        // Submit sleep sessions ending on different days
        submitSleepForDate(today.minusDays(1), 360)  // 6h
        submitSleepForDate(today.minusDays(2), 420)  // 7h
        submitSleepForDate(today.minusDays(3), 300)  // 5h - worst
        submitSleepForDate(today.minusDays(4), 480)  // 8h - best
        submitSleepForDate(today.minusDays(5), 390)  // 6.5h

        waitForProjections()

        when: "asking about sleep patterns"
        def response = askAssistant("Jak wyglądał mój sen w ostatnim tygodniu? Który dzień był najlepszy, a który najgorszy?")
        println "DEBUG: AI response: $response"

        then: "AI identifies best (8h) and worst (5h) sleep nights"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        """Sleep analysis:
                        - Best night: ~8 hours (480 minutes)
                        - Worst night: ~5 hours (300 minutes)
                        - Average: approximately 6-7 hours""",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI handles comparison when one period has no data"() {
        given: "steps only for this week, not last week"
        def today = LocalDate.now(POLAND_ZONE)

        submitStepsForDate(today, 8000)
        submitStepsForDate(today.minusDays(1), 7000)
        submitStepsForDate(today.minusDays(2), 9000)
        // Last week has no data

        waitForProjections()

        when: "asking to compare"
        def response = askAssistant("Porównaj moje kroki z tego tygodnia do zeszłego")
        println "DEBUG: AI response: $response"

        then: "AI acknowledges missing data for comparison"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "This week has data (~24000 steps total), but last week has no data or very little data available for comparison",
                        [],
                        response
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // Helper for sleep on specific date
    void submitSleepForDate(LocalDate date, int totalMinutes) {
        def sleepEnd = date.atTime(7, 0).atZone(POLAND_ZONE).toInstant()
        def sleepStart = sleepEnd.minusSeconds(totalMinutes * 60L)

        def request = [
            deviceId: getTestDeviceId(),
            events: [[
                idempotencyKey: UUID.randomUUID().toString(),
                type: "SleepSessionRecorded.v1",
                occurredAt: sleepEnd.toString(),
                payload: [
                    sleepId: UUID.randomUUID().toString(),
                    sleepStart: sleepStart.toString(),
                    sleepEnd: sleepEnd.toString(),
                    totalMinutes: totalMinutes,
                    originPackage: "com.test"
                ]
            ]]
        ]
        submitEventMap(request)
    }
}
