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
    // Expected: 8500 steps

    @Unroll
    def "BM-01: Simple steps query [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "8500 steps recorded today"
        submitStepsForToday(8500)
        waitForProjections()

        when: "asking about today's steps"
        def question = "How many steps did I take today?"
        def result = benchmarkChat(question)
        recordBenchmark("BM-01", "Simple steps query", result)

        then: "LLM judge validates response quality"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality. Do NOT consider whether tools were called - assume the data is correct.

            The response MUST:
            1. State exactly 8500 steps (or formatted as 8,500 or 8 500)
            2. Be a direct answer to the question

            FAIL if:
            - Wrong number of steps (not 8500)
            - Vague answer like "several thousand" without exact number
            - Says no data available when data exists
        """)
        println "LLM Judge BM-01: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-01", judgeResult)
        judgeResult.score >= 0.7

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
        submitSleepForLastNight(420) // 7h = 420 minutes
        waitForProjections()

        when: "first message about steps (start conversation)"
        askAssistantStart("How many steps did I take today?")

        and: "follow-up about sleep (continue same conversation)"
        def result = benchmarkChatContinue("And how much sleep did I get today?")
        recordBenchmark("BM-02", "Multi-turn conversation", result)

        then: "LLM judge validates multi-turn context retention"
        result.passed
        def judgeResult = llmAsJudge("And how much sleep did I get today?", result.response, """
            Evaluate ONLY the text response quality. Do NOT consider whether tools were called - assume the data is correct.

            CONTEXT: This is a follow-up question after asking about steps. The user has 420 minutes (7 hours) of sleep recorded for today.

            The response MUST:
            1. Answer about sleep (not steps - this is a follow-up)
            2. Include sleep duration: 7 hours OR 420 minutes
            3. Be contextually appropriate as a follow-up response

            FAIL if:
            - Talks about steps instead of sleep
            - Wrong sleep duration (not ~7 hours or 420 minutes)
            - Says no sleep data available
        """)
        println "LLM Judge BM-02: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-02", judgeResult)
        judgeResult.score >= 0.7

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
        recordBenchmark("BM-03", "AI daily summary", result)

        then: "summary is generated with reasonable content"
        result.passed
        result.response.length() > 30

        and: "LLM judge validates summary quality"
        def judgeResult = llmAsJudge("Generate AI daily summary", result.response, """
            Evaluate ONLY the text response quality. Do NOT consider whether tools were called - assume the data is correct.

            CONTEXT: User has recorded: 12000 steps, 8 hours sleep, 450 active calories, breakfast (oatmeal with fruits, 350 kcal).

            The response MUST:
            1. Be written in Polish language
            2. Mention at least 2 of the following data points:
               - Steps: ~12000 (excellent day)
               - Sleep: 8 hours (good sleep)
               - Calories burned: 450
               - Breakfast/meal information
            3. Provide an overall assessment or encouragement

            FAIL if:
            - Written in English (should be Polish)
            - Mentions wrong data (e.g., wrong step count, wrong sleep hours)
            - Generic text without referencing actual data
        """)
        println "LLM Judge BM-03: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-03", judgeResult)
        judgeResult.score >= 0.6

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 4: Meal Import - Simple (Banana) ====================
    // 1 medium banana (~120g): 89 kcal, 1.1g protein, 0.3g fat, 23g carbs
    // Tolerance: ±20%

    @Unroll
    def "BM-04: Meal import - banana [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "importing a banana"
        def result = benchmarkMealImport("banan")
        recordBenchmark("BM-04", "Meal import - banana", result)

        then: "import succeeds"
        result.passed

        and: "calories are accurate for a banana (89 kcal ±20% = 71-107)"
        def calories = result.parsedResponse.caloriesKcal
        calories != null && calories >= 71 && calories <= 107

        and: "carbohydrates are accurate (23g ±25% = 17-29)"
        def carbs = result.parsedResponse.carbohydratesGrams
        carbs != null && carbs >= 17 && carbs <= 29

        and: "protein is low (1g ±2 = 0-3)"
        def protein = result.parsedResponse.proteinGrams
        protein != null && protein >= 0 && protein <= 3

        and: "fat is minimal (0.3g ±1 = 0-2)"
        def fat = result.parsedResponse.fatGrams
        fat != null && fat >= 0 && fat <= 2

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 5: Meal Import - Complex (Chicken meal) ====================
    // 200g grilled chicken breast: 330 kcal, 50g protein, 7g fat, 0g carbs
    // 200g broccoli: 68 kcal, 6g protein, 0.8g fat, 14g carbs
    // 100g cooked rice: 130 kcal, 3g protein, 0.3g fat, 28g carbs
    // TOTAL: 528 kcal, 59g protein, 8g fat, 42g carbs
    // Tolerance: ±15%

    @Unroll
    def "BM-05: Meal import - chicken with rice and broccoli [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "importing a complex meal"
        def result = benchmarkMealImport("200g grillowanego kurczaka, 200g brokuła i 100g ryżu")
        recordBenchmark("BM-05", "Meal import - complex", result)

        then: "import succeeds"
        result.passed

        and: "calories are accurate (528 kcal ±15% = 449-607)"
        def calories = result.parsedResponse.caloriesKcal
        calories != null && calories >= 449 && calories <= 607

        and: "protein is accurate - this is HIGH protein meal (59g ±15% = 50-68)"
        def protein = result.parsedResponse.proteinGrams
        protein != null && protein >= 50 && protein <= 68

        and: "fat is low (8g ±30% = 6-10)"
        def fat = result.parsedResponse.fatGrams
        fat != null && fat >= 5 && fat <= 12

        and: "carbohydrates are moderate (42g ±20% = 34-50)"
        def carbs = result.parsedResponse.carbohydratesGrams
        carbs != null && carbs >= 34 && carbs <= 50

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
        recordBenchmark("BM-06", "Sleep import - screenshot", result)

        then: "import succeeds"
        result.passed

        and: "total sleep minutes is approximately 378 (±10%)"
        def totalMinutes = result.parsedResponse.totalSleepMinutes
        totalMinutes != null && totalMinutes >= 340 && totalMinutes <= 420

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
        recordBenchmark("BM-07", "Polish language query", result)

        then: "LLM judge validates Polish language handling"
        result.passed
        def judgeResult = llmAsJudge("Ile kroków zrobiłem dzisiaj?", result.response, """
            Evaluate ONLY the text response quality. Do NOT consider whether tools were called - assume the data is correct.

            CONTEXT: User asked in Polish "How many steps did I take today?" and has 7500 steps recorded.

            The response MUST:
            1. State exactly 7500 steps (or formatted as 7,500 / 7 500)
            2. Respond in Polish OR English (both acceptable, but Polish preferred)
            3. Be a direct answer to the question

            FAIL if:
            - Wrong number of steps (not 7500)
            - Says no data available
        """)
        println "LLM Judge BM-07: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-07", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 8: Date Recognition - "Yesterday" ====================
    // Yesterday: 450 minutes = 7 hours 30 minutes

    @Unroll
    def "BM-08: Date recognition - yesterday [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "sleep recorded for yesterday"
        submitSleepForDate(yesterday, 450) // 7.5h = 450 minutes
        waitForProjections()

        when: "asking about yesterday"
        def result = benchmarkChat("How did I sleep yesterday?")
        recordBenchmark("BM-08", "Date recognition - yesterday", result)

        then: "LLM judge validates date recognition"
        result.passed
        def judgeResult = llmAsJudge("How did I sleep yesterday?", result.response, """
            Evaluate ONLY the text response quality. Do NOT consider whether tools were called - assume the data is correct.

            CONTEXT: User asked about YESTERDAY's sleep. Yesterday has 450 minutes (7.5 hours) of sleep recorded.

            The response MUST:
            1. Reference YESTERDAY (not today, not last night from today's perspective)
            2. Include correct sleep duration: 7.5 hours OR 450 minutes OR "7 hours 30 minutes"
            3. Discuss sleep quality/duration contextually

            FAIL if:
            - Talks about today's sleep instead of yesterday's
            - Wrong sleep duration (not 7.5h / 450min)
            - Says no data for yesterday when data exists
        """)
        println "LLM Judge BM-08: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-08", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 9: Multi-tool Query ====================
    // Steps: 9000, Calories: 380, Sleep: 420min (7h)

    @Unroll
    def "BM-09: Multi-tool query [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "various health data"
        submitStepsForToday(9000)
        submitActiveCalories(380)
        submitSleepForLastNight(420) // 7h
        waitForProjections()

        when: "asking for complete health summary"
        def question = "Give me a complete health summary for today including steps, calories, and sleep"
        def result = benchmarkChat(question)
        recordBenchmark("BM-09", "Multi-tool query", result)

        then: "LLM judge validates multi-tool response completeness"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality. Do NOT consider whether tools were called - assume the data is correct.

            CONTEXT: User asked for complete health summary. Data: 9000 steps, 380 active calories, 7 hours (420 min) sleep.

            The response MUST include ALL THREE data points:
            1. Steps: 9000 (or 9,000)
            2. Calories: 380 kcal burned
            3. Sleep: 7 hours OR 420 minutes

            FAIL if:
            - Missing any of the three metrics (steps, calories, sleep)
            - Wrong values for any metric
            - Only mentions 1 or 2 metrics instead of all 3
        """)
        println "LLM Judge BM-09: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-09", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 10: Weekly Summary Query ====================
    // Total: 5000+5500+6000+6500+7000+7500+8000 = 45500 steps

    @Unroll
    def "BM-10: Weekly summary query [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "steps recorded for 7 days"
        (0..6).each { daysAgo ->
            submitStepsForDate(today.minusDays(daysAgo), 5000 + (daysAgo * 500))
        }
        waitForProjections()

        when: "asking about weekly total"
        def result = benchmarkChat("How many steps did I take this week in total?")
        recordBenchmark("BM-10", "Weekly summary query", result)

        then: "LLM judge validates weekly aggregation"
        result.passed
        def judgeResult = llmAsJudge("How many steps did I take this week in total?", result.response, """
            Evaluate ONLY the text response quality. Do NOT consider whether tools were called - assume the data is correct.

            CONTEXT: User has 7 days of step data: 5000, 5500, 6000, 6500, 7000, 7500, 8000 steps.
            Total: 45,500 steps over 7 days. Average: ~6,500 steps/day.

            The response MUST:
            1. Provide a weekly total around 45,500 (allow ±1000 for rounding)
            2. Reference that this is a WEEK of data (7 days)
            3. Be mathematically reasonable (not wildly off)

            ACCEPTABLE totals: 44,500 - 46,500

            FAIL if:
            - Total is way off (e.g., 30,000 or 60,000)
            - Only shows daily data without aggregation
            - Confuses with a single day's steps
        """)
        println "LLM Judge BM-10: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-10", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Test 11: Long Multi-turn Conversation ====================
    // Today: 12500 steps, 450min sleep (7.5h), 520 calories, 2 meals
    // Yesterday: 9800 steps, 420min sleep (7h)

    @Unroll
    def "BM-11: Long conversation with multiple queries [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "comprehensive health data for multiple days"
        // Today's data
        submitStepsForToday(12500)
        submitSleepForLastNight(450) // 7.5h
        submitActiveCalories(520)
        submitMeal("Oatmeal with banana", "BREAKFAST", 380, 12, 8, 65)
        submitMeal("Grilled chicken salad", "LUNCH", 450, 45, 18, 20)

        // Yesterday's data
        submitStepsForDate(yesterday, 9800)
        submitSleepForDate(yesterday, 420)

        // Day before yesterday
        submitStepsForDate(today.minusDays(2), 11200)

        waitForProjections()

        when: "conducting a long multi-turn conversation"
        // Turn 1: Ask about today's steps (start conversation)
        askAssistantStart("How many steps did I take today?")

        // Turn 2: Follow-up about sleep (continue same conversation)
        askAssistantContinue("And how much sleep did I get today?")

        // Turn 3: Compare with yesterday
        askAssistantContinue("How does today compare to yesterday in terms of steps?")

        // Turn 4: Ask about calories
        askAssistantContinue("What about my calorie burn today?")

        // Turn 5: Ask about meals
        askAssistantContinue("What did I eat today?")

        // Turn 6: Final summary question - this is the one we benchmark (continue conversation)
        def question = "Based on all this information, give me a brief overall health assessment for today. How am I doing?"
        def result = benchmarkChatContinue(question)
        recordBenchmark("BM-11", "Long conversation (6 turns)", result)

        then: "response is substantial"
        result.passed
        result.response.length() > 100

        and: "LLM judge confirms the response quality"
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality. Do NOT consider whether tools were called - assume the data is correct.

            CONTEXT: This is a summary after a 6-turn conversation about health data: 12500 steps, ~7.5h sleep, 520 calories, meals (oatmeal + chicken salad).

            The response MUST:
            1. Reference actual data (steps: 12500, sleep: ~7.5h, meals: oatmeal + chicken salad)
            2. Provide an overall assessment (positive/negative/neutral)
            3. Be coherent and relevant to health

            FAIL if:
            - Response is completely generic without referencing any specific numbers
            - Response contradicts the data (e.g., says "low steps" when 12500 is high)
        """)
        println "LLM Judge BM-11: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-11", judgeResult)
        judgeResult.score >= 0.6

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
