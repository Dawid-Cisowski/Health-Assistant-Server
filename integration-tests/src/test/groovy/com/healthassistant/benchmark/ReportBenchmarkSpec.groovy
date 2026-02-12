package com.healthassistant.benchmark

import com.healthassistant.reports.api.ReportsFacade
import com.healthassistant.reports.api.dto.ReportType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title
import spock.lang.Unroll

import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * AI Benchmark Tests for Health Reports module.
 *
 * Measures quality, cost, and time of AI-generated report summaries.
 * Each test runs for ALL models specified in BENCHMARK_MODELS env var.
 *
 * Run with:
 *   GEMINI_API_KEY=key ./gradlew :integration-tests:test --tests "*ReportBenchmarkSpec"
 *
 * Run with multiple models:
 *   GEMINI_API_KEY=key BENCHMARK_MODELS="gemini-2.0-flash,gemini-2.0-pro-exp-02-05" \
 *     ./gradlew :integration-tests:test --tests "*ReportBenchmarkSpec"
 */
@Title("Report AI Benchmark Tests")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 300, unit = TimeUnit.SECONDS)
class ReportBenchmarkSpec extends BaseBenchmarkSpec {

    @Autowired
    ReportsFacade reportsFacade

    @Autowired
    JdbcTemplate jdbcTemplate

    def today = LocalDate.now(POLAND_ZONE)
    def yesterday = today.minusDays(1)

    def setup() {
        def deviceId = getTestDeviceId()
        jdbcTemplate.update("DELETE FROM health_reports WHERE device_id = ?", deviceId)
    }

    // ==================== BM-R01: Daily Report AI Summary Quality ====================

    @Unroll
    def "BM-R01: Daily report AI summary quality [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "comprehensive health data for today"
        submitStepsForToday(11500)
        submitSleepForLastNight(460)  // ~7.7h
        submitActiveCalories(620)
        submitMeal("Owsianka z owocami", "BREAKFAST", 380, 14, 8, 60)
        submitMeal("Grillowany kurczak z ryzem", "LUNCH", 550, 42, 15, 45)
        waitForProjections()

        and: "data for yesterday (for comparison)"
        submitStepsForDate(yesterday, 7500)
        submitSleepForDate(yesterday, 390)
        waitForProjections()

        when: "generating daily report and measuring"
        def startTime = System.currentTimeMillis()
        def reportId = reportsFacade.generateReport(getTestDeviceId(), ReportType.DAILY, today, today)
        def endTime = System.currentTimeMillis()

        then: "report is generated"
        reportId.isPresent()
        def report = reportsFacade.getReport(getTestDeviceId(), reportId.get()).get()
        def aiSummary = report.aiSummary()
        aiSummary != null && aiSummary.length() > 50

        and: "record benchmark result"
        def result = BenchmarkResult.builder()
                .model(modelName)
                .response(aiSummary)
                .passed(true)
                .inputTokens(0L)   // Token info not available via facade
                .outputTokens(0L)
                .estimatedCostUsd(0.0)
                .responseTimeMs(endTime - startTime)
                .timestamp(Instant.now())
                .build()
        recordBenchmark("BM-R01", "Daily report AI summary", result)

        and: "LLM judge validates daily report quality"
        def question = "Daily health report: 11500 steps (goal met), 7.7h sleep (goal met), 620 cal burned (goal met), 2 healthy meals (goal failed), comparison with yesterday (7500 steps, 6.5h sleep)"
        def judgeResult = llmAsJudge(question, aiSummary, """
            CONTEXT: Daily report AI summary. Data: 11500 steps, ~7.7h sleep, 620 active cal,
            2 meals (only 2 vs goal of 3). Comparison: today much better than yesterday (7500 steps, 6.5h sleep).

            The report MUST:
            1. Be in Polish
            2. Use Markdown formatting (## headers or **bold**)
            3. Reference at least 2 actual data points from input
            4. Comment on goals (most achieved, meals failed)
            5. Reference improvement over previous period

            FAIL if:
            - Not in Polish
            - Fabricates data not in input
            - No Markdown formatting
            - Completely ignores goals
        """)
        println "LLM Judge BM-R01: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-R01", judgeResult)
        judgeResult.score >= 0.6

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-R02: Weekly Report AI Summary Quality ====================

    @Unroll
    def "BM-R02: Weekly report AI summary quality [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "health data for a full week"
        def weekStart = today.minusDays(6)
        (0..6).each { offset ->
            def date = weekStart.plusDays(offset)
            submitStepsForDate(date, 7000 + offset * 1000)  // 7000-13000
            submitSleepForDate(date, 380 + offset * 15)     // 380-470 min
        }
        waitForProjections()

        when: "generating weekly report and measuring"
        def startTime = System.currentTimeMillis()
        def reportId = reportsFacade.generateReport(getTestDeviceId(), ReportType.WEEKLY, weekStart, today)
        def endTime = System.currentTimeMillis()

        then: "report is generated"
        reportId.isPresent()
        def report = reportsFacade.getReport(getTestDeviceId(), reportId.get()).get()
        def aiSummary = report.aiSummary()
        aiSummary != null && aiSummary.length() > 50

        and: "record benchmark result"
        def result = BenchmarkResult.builder()
                .model(modelName)
                .response(aiSummary)
                .passed(true)
                .inputTokens(0L)
                .outputTokens(0L)
                .estimatedCostUsd(0.0)
                .responseTimeMs(endTime - startTime)
                .timestamp(Instant.now())
                .build()
        recordBenchmark("BM-R02", "Weekly report AI summary", result)

        and: "LLM judge validates weekly report quality"
        def question = "Weekly health report: 7 days, steps 7000-13000 (avg ~10000), sleep 380-470 min (avg ~7h)"
        def judgeResult = llmAsJudge(question, aiSummary, """
            CONTEXT: Weekly report AI summary covering 7 days.
            Steps: 7000 to 13000 per day (avg ~10,000).
            Sleep: 380 to 470 min per day (avg ~425 min / ~7h).
            Goals evaluated for averages, workouts, active minutes, etc.

            The report MUST:
            1. Be in Polish
            2. Use Markdown formatting
            3. Discuss weekly trends or aggregates (not single day)
            4. Reference at least 1 actual metric from the data

            FAIL if:
            - Not in Polish
            - Describes single day data only
            - Fabricates specific workout or meal data not provided
        """)
        println "LLM Judge BM-R02: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-R02", judgeResult)
        judgeResult.score >= 0.6

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-R03: Report AI with Comprehensive Goals ====================

    @Unroll
    def "BM-R03: Daily report with all goal types [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "data designed to have mixed goal outcomes"
        submitStepsForToday(15000)           // >= 10000 -> ACHIEVED
        submitSleepForLastNight(350)         // >= 420   -> FAILED
        submitActiveCalories(800)            // >= 600   -> ACHIEVED
        submitMeal("Salad", "LUNCH", 350, 15, 8, 40)       // HEALTHY
        submitMeal("Grilled fish", "DINNER", 400, 35, 12, 20) // HEALTHY
        submitMeal("Smoothie", "SNACK", 200, 10, 3, 35)    // HEALTHY -> 3 healthy = ACHIEVED
        waitForProjections()

        when: "generating daily report"
        def startTime = System.currentTimeMillis()
        def reportId = reportsFacade.generateReport(getTestDeviceId(), ReportType.DAILY, today, today)
        def endTime = System.currentTimeMillis()

        then: "report generated"
        reportId.isPresent()
        def report = reportsFacade.getReport(getTestDeviceId(), reportId.get()).get()
        def aiSummary = report.aiSummary()
        aiSummary != null

        and: "record and evaluate"
        def result = BenchmarkResult.builder()
                .model(modelName)
                .response(aiSummary)
                .passed(aiSummary?.length() > 30)
                .inputTokens(0L)
                .outputTokens(0L)
                .estimatedCostUsd(0.0)
                .responseTimeMs(endTime - startTime)
                .timestamp(Instant.now())
                .build()
        recordBenchmark("BM-R03", "Report with mixed goals", result)

        def judgeResult = llmAsJudge(
            "Daily report: 15000 steps (ACHIEVED), 5.8h sleep (FAILED), 800 cal burned (ACHIEVED), 3 healthy meals (ACHIEVED)",
            aiSummary,
            """
            CONTEXT: Daily report with MIXED goal outcomes.
            ACHIEVED: steps (15000), burned calories (800), healthy meals (3).
            FAILED: sleep (350 min = 5h50min, goal was 7h).

            The report MUST:
            1. Be in Polish
            2. Acknowledge that sleep goal was NOT met
            3. Acknowledge positive results (steps excellent at 15000)
            4. Use encouraging but realistic tone

            FAIL if:
            - Says ALL goals met when sleep was clearly missed
            - Ignores the poor sleep entirely
            - Not in Polish
            """
        )
        println "LLM Judge BM-R03: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-R03", judgeResult)
        judgeResult.score >= 0.6

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Report Generation ====================

    def cleanupSpec() {
        BenchmarkReporter.printConsoleReport()

        def jsonPath = Paths.get("build/reports/benchmark/report-benchmark-report.json")
        BenchmarkReporter.writeJsonReport(jsonPath)

        def htmlPath = Paths.get("build/reports/benchmark/report-benchmark-report.html")
        BenchmarkReporter.writeHtmlReport(htmlPath)

        def mdPath = Paths.get("build/reports/benchmark/report-benchmark-report.md")
        BenchmarkReporter.writeMarkdownReport(mdPath)
    }
}
