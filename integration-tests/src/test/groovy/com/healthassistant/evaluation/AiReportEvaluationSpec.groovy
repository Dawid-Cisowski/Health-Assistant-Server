package com.healthassistant.evaluation

import com.healthassistant.reports.api.ReportsFacade
import com.healthassistant.reports.api.dto.ReportType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Requires
import spock.lang.Retry
import spock.lang.Timeout
import spock.lang.Title

import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * AI evaluation tests for report AI summary quality.
 *
 * Uses real Gemini API to generate report AI summaries and evaluates quality
 * using the LLM-as-a-Judge pattern.
 *
 * IMPORTANT: Requires GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiReportEvaluationSpec"
 */
@Title("AI Report Evaluation Tests")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 300, unit = TimeUnit.SECONDS)
class AiReportEvaluationSpec extends BaseEvaluationSpec {

    @Autowired
    ReportsFacade reportsFacade

    @Autowired
    JdbcTemplate jdbcTemplate

    def today = LocalDate.now(POLAND_ZONE)
    def yesterday = today.minusDays(1)

    def setup() {
        // Clean reports for this evaluation device
        def deviceId = getTestDeviceId()
        jdbcTemplate.update("DELETE FROM health_reports WHERE device_id = ?", deviceId)
    }

    @Retry(count = 2, delay = 2000)
    def "AI report summary accurately reflects health data and goals"() {
        given: "comprehensive health data with some goals achieved and some not"
        submitStepsForToday(12500)           // Goal: >= 10,000 -> ACHIEVED
        submitSleepForLastNight(450)         // Goal: >= 420min -> ACHIEVED
        submitActiveCalories(700)            // Goal: >= 600 -> ACHIEVED
        submitMeal("Sniadanie", "BREAKFAST", 400, 20, 12, 55)    // HEALTHY
        submitMeal("Obiad", "LUNCH", 600, 35, 18, 50)           // HEALTHY
        // Only 2 healthy meals -> Goal: >= 3 -> NOT ACHIEVED
        waitForProjections()

        when: "generating daily report"
        def reportId = reportsFacade.generateReport(getTestDeviceId(), ReportType.DAILY, today, today)

        then: "report is generated with AI summary"
        reportId.isPresent()
        def report = reportsFacade.getReport(getTestDeviceId(), reportId.get()).get()
        report.aiSummary() != null
        report.aiSummary().length() > 50

        and: "AI summary mentions the health data"
        def judgeResult = llmAsJudge(
            "Generate daily health report for: 12500 steps, 7.5h sleep, 700 cal burned, 2 healthy meals",
            report.aiSummary(),
            """
            CONTEXT: The AI was given actual health data: 12500 steps (goal met), 450 min / 7.5h sleep (goal met),
            700 active calories (goal met), 2 healthy meals (goal NOT met - needs 3).

            The AI summary MUST:
            1. Be written in Polish
            2. Reference at least 2 of these data points: 12500 steps, 7.5h sleep, 700 calories
            3. Comment on goal achievement (most achieved, meals not achieved)
            4. Be structured with Markdown formatting (## headers or **bold**)

            FAIL if:
            - Not in Polish
            - Contains fabricated data not in the input
            - Ignores the failed healthy meals goal entirely
            - No Markdown formatting at all
            """
        )
        println "LLM Judge (report data accuracy): ${judgeResult}"
        judgeResult.score >= 0.6

        cleanup:
        cleanAllData()
    }

    @Retry(count = 2, delay = 2000)
    def "AI report summary uses Markdown formatting with headers"() {
        given: "health data for today"
        submitStepsForToday(9000)
        submitSleepForLastNight(420)
        submitActiveCalories(500)
        waitForProjections()

        when: "generating daily report"
        def reportId = reportsFacade.generateReport(getTestDeviceId(), ReportType.DAILY, today, today)

        then: "report AI summary uses Markdown"
        reportId.isPresent()
        def report = reportsFacade.getReport(getTestDeviceId(), reportId.get()).get()
        def summary = report.aiSummary()
        summary != null

        and: "summary contains Markdown headers"
        summary.contains("##") || summary.contains("**")

        cleanup:
        cleanAllData()
    }

    @Retry(count = 2, delay = 2000)
    def "AI report summary references comparison with previous period"() {
        given: "data for yesterday and today showing improvement"
        submitStepsForDate(yesterday, 6000)
        submitSleepForDate(yesterday, 350)
        waitForProjections()

        submitStepsForToday(11000)
        submitSleepForLastNight(480)
        submitActiveCalories(600)
        waitForProjections()

        when: "generating today's report (which includes comparison)"
        def reportId = reportsFacade.generateReport(getTestDeviceId(), ReportType.DAILY, today, today)

        then: "report has both AI summary and comparison"
        reportId.isPresent()
        def report = reportsFacade.getReport(getTestDeviceId(), reportId.get()).get()
        report.aiSummary() != null
        report.comparison() != null

        and: "AI summary references the comparison or improvement"
        def judgeResult = llmAsJudge(
            "Daily report with comparison: today 11000 steps vs yesterday 6000 steps, today 8h sleep vs yesterday 5.8h",
            report.aiSummary(),
            """
            CONTEXT: Today's data shows significant improvement vs yesterday:
            - Steps: 11000 today vs 6000 yesterday (+83%)
            - Sleep: 480min (8h) today vs 350min (5.8h) yesterday (+37%)

            The AI summary MUST:
            1. Be in Polish
            2. Reference improvement/comparison in some form (wzrost, poprawa, lepiej, wiecej, etc.)
            3. Not hallucinate data that wasn't provided

            FAIL if:
            - Says performance got worse when it clearly improved
            - No mention of any comparison or trend at all
            """
        )
        println "LLM Judge (comparison reference): ${judgeResult}"
        judgeResult.score >= 0.6

        cleanup:
        cleanAllData()
    }

    @Retry(count = 2, delay = 2000)
    def "AI report summary for weekly report aggregates data properly"() {
        given: "health data for a full week"
        def weekStart = today.minusDays(6)
        (0..6).each { offset ->
            def date = weekStart.plusDays(offset)
            submitStepsForDate(date, 8000 + offset * 500)
            submitSleepForDate(date, 400 + offset * 10)
        }
        waitForProjections()

        when: "generating weekly report"
        def reportId = reportsFacade.generateReport(getTestDeviceId(), ReportType.WEEKLY, weekStart, today)

        then: "weekly report has AI summary"
        reportId.isPresent()
        def report = reportsFacade.getReport(getTestDeviceId(), reportId.get()).get()
        report.aiSummary() != null
        report.reportType() == ReportType.WEEKLY

        and: "AI summary discusses the weekly period"
        def judgeResult = llmAsJudge(
            "Weekly health report for 7 days with avg ~9500 steps/day and avg ~6.8h sleep",
            report.aiSummary(),
            """
            CONTEXT: Weekly report covering 7 days. Steps range 8000-11000 (avg ~9500/day).
            Sleep range 400-460 min (avg ~7.2h).

            The AI summary MUST:
            1. Be in Polish
            2. Reference weekly/period data (not just single day)
            3. Include at least one concrete number or average

            FAIL if:
            - Describes a single day instead of a week
            - All fabricated data with no relation to actual input
            """
        )
        println "LLM Judge (weekly summary): ${judgeResult}"
        judgeResult.score >= 0.6

        cleanup:
        cleanAllData()
    }
}
