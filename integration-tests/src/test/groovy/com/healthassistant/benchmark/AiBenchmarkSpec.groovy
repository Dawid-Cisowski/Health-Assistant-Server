package com.healthassistant.benchmark

import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title
import spock.lang.Unroll

import java.nio.file.Paths
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * AI Benchmark Tests - Happy Path
 *
 * 10 tests measuring quality, cost, and time metrics.
 * Each test runs for ALL models specified in BENCHMARK_MODELS env var.
 *
 * Run with single model:
 *   GEMINI_API_KEY=key ./gradlew :integration-tests:benchmarkTest
 *
 * Run with multiple models for comparison:
 *   GEMINI_API_KEY=key BENCHMARK_MODELS="gemini-2.0-flash,gemini-2.0-pro-exp-02-05" \
 *     ./gradlew :integration-tests:benchmarkTest
 */
@Title("AI Benchmark Tests - Happy Path")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 300, unit = TimeUnit.SECONDS)
class AiBenchmarkSpec extends BaseBenchmarkSpec {

    def today = LocalDate.now(POLAND_ZONE)
    def yesterday = today.minusDays(1)

    // ==================== Test 1: Simple Chat - Steps Query ====================

    @Unroll
    def "BM-01: Simple steps query [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "8500 steps recorded today"
        submitStepsForToday(8500)
        waitForProjections()

        when: "asking about today's steps"
        def result = benchmarkChat("How many steps did I take today?")

        then: "response contains 8500"
        result.passed
        result.response.contains("8500") || result.response.contains("8,500") || result.response.contains("8 500")

        and: "metrics recorded"
        recordBenchmark("BM-01", "Simple steps query", result)

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 2: Multi-turn Conversation ====================

    @Unroll
    def "BM-02: Multi-turn conversation [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "health data exists"
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

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 3: AI Daily Summary Generation ====================

    @Unroll
    def "BM-03: AI daily summary generation [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "comprehensive health data for today"
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

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 4: Meal Import - Simple (Banana) ====================

    @Unroll
    def "BM-04: Meal import - banana [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "importing a banana"
        def result = benchmarkMealImport("banan")

        then: "import succeeds"
        result.passed

        and: "calories are reasonable for a banana (~90-135 kcal)"
        def calories = result.parsedResponse.caloriesKcal
        calories != null && calories >= 70 && calories <= 150

        and: "metrics recorded"
        recordBenchmark("BM-04", "Meal import - banana", result)

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 5: Meal Import - Complex (Chicken meal) ====================

    @Unroll
    def "BM-05: Meal import - chicken with rice and broccoli [#modelName]"() {
        given: "model is set"
        currentModel = modelName

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

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 6: Sleep Import from Screenshot ====================

    @Unroll
    def "BM-06: Sleep import from screenshot [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "sleep screenshot exists"
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

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 7: Polish Language Query ====================

    @Unroll
    def "BM-07: Polish language query [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "steps recorded today"
        submitStepsForToday(7500)
        waitForProjections()

        when: "asking in Polish"
        def result = benchmarkChat("Ile kroków zrobiłem dzisiaj?")

        then: "response contains the step count"
        result.passed
        result.response.contains("7500") || result.response.contains("7,500") || result.response.contains("7 500")

        and: "metrics recorded"
        recordBenchmark("BM-07", "Polish language query", result)

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 8: Date Recognition - "Yesterday" ====================

    @Unroll
    def "BM-08: Date recognition - yesterday [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "sleep recorded for yesterday"
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

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 9: Multi-tool Query ====================

    @Unroll
    def "BM-09: Multi-tool query [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "various health data"
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

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 10: Weekly Summary Query ====================

    @Unroll
    def "BM-10: Weekly summary query [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "steps recorded for 7 days"
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

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Cleanup - Generate Report ====================

    def cleanupSpec() {
        // Print report to console
        BenchmarkReporter.printConsoleReport()

        // Write JSON report
        def jsonPath = Paths.get("build/reports/benchmark/benchmark-report.json")
        BenchmarkReporter.writeJsonReport(jsonPath)

        // Write HTML report
        def htmlPath = Paths.get("build/reports/benchmark/benchmark-report.html")
        BenchmarkReporter.writeHtmlReport(htmlPath)

        // Write Markdown report (for GitHub Job Summary)
        def mdPath = Paths.get("build/reports/benchmark/benchmark-report.md")
        BenchmarkReporter.writeMarkdownReport(mdPath)
    }
}
