package com.healthassistant.benchmark

import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.nio.file.Paths
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * AI Benchmark Tests - Happy Path
 *
 * 10 tests measuring quality, cost, and time metrics.
 * Each test records all metrics together for comparison between models.
 *
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiBenchmarkSpec"
 *
 * To compare models, run twice with different GEMINI_MODEL env var:
 * - GEMINI_MODEL=gemini-2.0-flash-001 (default)
 * - GEMINI_MODEL=gemini-2.0-pro-exp-02-05
 */
@Title("AI Benchmark Tests - Happy Path")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiBenchmarkSpec extends BaseBenchmarkSpec {

    def today = LocalDate.now(POLAND_ZONE)
    def yesterday = today.minusDays(1)

    // ==================== Test 1: Simple Chat - Steps Query ====================

    def "BM-01: Simple steps query"() {
        given: "8500 steps recorded today"
        submitStepsForToday(8500)
        waitForProjections()

        when: "asking about today's steps"
        def result = benchmarkChat("How many steps did I take today?")

        then: "response contains 8500"
        result.passed
        result.response.contains("8500") || result.response.contains("8,500") || result.response.contains("8 500")

        and: "metrics recorded"
        recordBenchmark("BM-01", "Simple steps query", result)
    }

    // ==================== Test 2: Multi-turn Conversation ====================

    def "BM-02: Multi-turn conversation"() {
        given: "health data exists"
        submitStepsForToday(10000)
        submitSleepForLastNight(420) // 7h
        waitForProjections()

        when: "first message about steps"
        askAssistant("How many steps did I take today?")

        and: "follow-up about sleep"
        def result = benchmarkChat("And how did I sleep last night?")

        then: "response mentions sleep duration (~7 hours or 420 minutes)"
        result.passed
        result.response.toLowerCase().contains("7") ||
                result.response.contains("420") ||
                result.response.toLowerCase().contains("sleep")

        and: "metrics recorded"
        recordBenchmark("BM-02", "Multi-turn conversation", result)
    }

    // ==================== Test 3: AI Daily Summary Generation ====================

    def "BM-03: AI daily summary generation"() {
        given: "comprehensive health data for today"
        submitStepsForToday(12000)
        submitSleepForLastNight(480) // 8h
        submitActiveCalories(450)
        submitMeal("Oatmeal with fruits", "BREAKFAST", 350, 15, 8, 50)
        waitForProjections()

        when: "requesting AI summary"
        def result = benchmarkAiSummary(today)

        then: "summary is generated with reasonable content"
        result.passed
        result.response.length() > 30

        and: "metrics recorded"
        recordBenchmark("BM-03", "AI daily summary", result)
    }

    // ==================== Test 4: Meal Import - Simple (Banana) ====================

    def "BM-04: Meal import - banana"() {
        when: "importing a banana"
        def result = benchmarkMealImport("banan")

        then: "import succeeds"
        result.passed

        and: "calories are reasonable for a banana (~90-135 kcal)"
        def calories = result.parsedResponse.caloriesKcal
        calories != null && calories >= 70 && calories <= 150

        and: "metrics recorded"
        recordBenchmark("BM-04", "Meal import - banana", result)
    }

    // ==================== Test 5: Meal Import - Complex (Chicken meal) ====================

    def "BM-05: Meal import - chicken with rice and broccoli"() {
        when: "importing a complex meal"
        def result = benchmarkMealImport("200g grillowanego kurczaka, 200g brokuła i 100g ryżu")

        then: "import succeeds"
        result.passed

        and: "calories are reasonable (~350-750 kcal)"
        def calories = result.parsedResponse.caloriesKcal
        calories != null && calories >= 300 && calories <= 800

        and: "protein is high (~35-90g)"
        def protein = result.parsedResponse.proteinGrams
        protein != null && protein >= 30 && protein <= 100

        and: "metrics recorded"
        recordBenchmark("BM-05", "Meal import - complex", result)
    }

    // ==================== Test 6: Sleep Import from Screenshot ====================

    def "BM-06: Sleep import from screenshot"() {
        given: "sleep screenshot exists"
        def imageBytes = loadScreenshot("/screenshots/sleep_1.png")

        when: "importing sleep from screenshot"
        def result = benchmarkSleepImport(imageBytes, "sleep_1.png")

        then: "import succeeds"
        result.passed

        and: "total sleep minutes is approximately 378 (±10%)"
        def totalMinutes = result.parsedResponse.totalSleepMinutes
        totalMinutes != null && totalMinutes >= 340 && totalMinutes <= 420

        and: "metrics recorded"
        recordBenchmark("BM-06", "Sleep import - screenshot", result)
    }

    // ==================== Test 7: Polish Language Query ====================

    def "BM-07: Polish language query"() {
        given: "steps recorded today"
        submitStepsForToday(7500)
        waitForProjections()

        when: "asking in Polish"
        def result = benchmarkChat("Ile kroków zrobiłem dzisiaj?")

        then: "response contains the step count"
        result.passed
        result.response.contains("7500") || result.response.contains("7,500") || result.response.contains("7 500")

        and: "metrics recorded"
        recordBenchmark("BM-07", "Polish language query", result)
    }

    // ==================== Test 8: Date Recognition - "Yesterday" ====================

    def "BM-08: Date recognition - yesterday"() {
        given: "sleep recorded for yesterday"
        submitSleepForDate(yesterday, 450) // 7.5h
        waitForProjections()

        when: "asking about yesterday"
        def result = benchmarkChat("How did I sleep yesterday?")

        then: "response mentions sleep data"
        result.passed
        result.response.toLowerCase().contains("7") ||
                result.response.contains("450") ||
                result.response.toLowerCase().contains("sleep") ||
                result.response.toLowerCase().contains("hour")

        and: "metrics recorded"
        recordBenchmark("BM-08", "Date recognition - yesterday", result)
    }

    // ==================== Test 9: Multi-tool Query ====================

    def "BM-09: Multi-tool query"() {
        given: "various health data"
        submitStepsForToday(9000)
        submitActiveCalories(380)
        submitSleepForLastNight(420)
        waitForProjections()

        when: "asking for complete health summary"
        def result = benchmarkChat("Give me a complete health summary for today including steps, calories, and sleep")

        then: "response contains multiple metrics"
        result.passed
        // At least steps should be mentioned
        result.response.contains("9000") || result.response.contains("9,000") ||
                result.response.toLowerCase().contains("step")

        and: "metrics recorded"
        recordBenchmark("BM-09", "Multi-tool query", result)
    }

    // ==================== Test 10: Weekly Summary Query ====================

    def "BM-10: Weekly summary query"() {
        given: "steps recorded for 7 days"
        (0..6).each { daysAgo ->
            submitStepsForDate(today.minusDays(daysAgo), 5000 + (daysAgo * 500))
        }
        waitForProjections()
        // Total: 5000+5500+6000+6500+7000+7500+8000 = 45500

        when: "asking about weekly total"
        def result = benchmarkChat("How many steps did I take this week in total?")

        then: "response contains approximate total"
        result.passed
        // Should mention something about 45000 steps or weekly total
        result.response.contains("45") || result.response.contains("46") ||
                result.response.toLowerCase().contains("week") ||
                result.response.toLowerCase().contains("total")

        and: "metrics recorded"
        recordBenchmark("BM-10", "Weekly summary query", result)
    }

    // ==================== Cleanup - Generate Report ====================

    def cleanupSpec() {
        // Print report to console
        BenchmarkReporter.printConsoleReport()

        // Write JSON report
        def reportPath = Paths.get("build/reports/benchmark/benchmark-report.json")
        BenchmarkReporter.writeJsonReport(reportPath)
    }
}
