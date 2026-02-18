package com.healthassistant.benchmark

import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title
import spock.lang.Unroll

import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * AI Benchmark Tests for Mutation Tools.
 *
 * Measures quality, cost (tokens), and response time for mutation-related queries.
 * Each test runs for ALL models specified in BENCHMARK_MODELS env var.
 *
 * Run with:
 *   GEMINI_API_KEY=key ./gradlew :integration-tests:test --tests "*AiMutationBenchmarkSpec"
 *
 * Run with multiple models:
 *   GEMINI_API_KEY=key BENCHMARK_MODELS="gemini-2.0-flash,gemini-2.0-pro-exp-02-05" \
 *     ./gradlew :integration-tests:test --tests "*AiMutationBenchmarkSpec"
 */
@Title("AI Benchmark Tests - Mutation Tools")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 300, unit = TimeUnit.SECONDS)
class AiMutationBenchmarkSpec extends BaseBenchmarkSpec {

    def today = LocalDate.now(POLAND_ZONE)

    // ==================== BM-M01: Record Meal with Full Macros ====================

    @Unroll
    def "BM-M01: Record meal with full macros [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "user reports a full meal"
        def question = "Zjadłem kurczaka z ryżem na obiad: 500 kcal, 40g białka, 15g tłuszczu, 55g węgli"
        def result = benchmarkChat(question)
        recordBenchmark("BM-M01", "Record meal with full macros", result)

        then: "LLM judge validates meal recording"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality.

            The response MUST:
            1. Confirm the meal was recorded/saved successfully
            2. Mention the meal (chicken with rice / kurczak z ryżem)
            3. Reference the calories (~500 kcal)

            FAIL if:
            - Refuses to record
            - Asks for information that was already provided
            - Says it cannot perform mutations
        """)
        println "LLM Judge BM-M01: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-M01", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-M02: Record Meal with Macro Estimation ====================

    @Unroll
    def "BM-M02: Record meal with macro estimation [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "user reports a meal without macros"
        def question = "Zjadłem banana na śniadanie"
        def result = benchmarkChat(question)
        recordBenchmark("BM-M02", "Record meal with macro estimation", result)

        then: "LLM judge validates meal recording with estimation"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality.

            The response MUST:
            1. Confirm recording a banana as a meal
            2. Show estimated/approximated macros (calories should be around 80-120 kcal)
            3. Indicate that values were estimated

            FAIL if:
            - Refuses to record without exact macros
            - Assigns wildly wrong calories (>300 or <30 for a banana)
        """)
        println "LLM Judge BM-M02: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-M02", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-M03: Record Weight ====================

    @Unroll
    def "BM-M03: Record weight measurement [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "user reports weight"
        def question = "Ważyłem się rano - 82.5 kg"
        def result = benchmarkChat(question)
        recordBenchmark("BM-M03", "Record weight measurement", result)

        then: "LLM judge validates weight recording"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality.

            The response MUST:
            1. Confirm recording 82.5 kg weight
            2. NOT refuse or ask for additional information

            FAIL if:
            - Wrong weight value
            - Refuses to record
        """)
        println "LLM Judge BM-M03: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-M03", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-M04: Record Workout ====================

    @Unroll
    def "BM-M04: Record workout with exercises [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "user describes a workout"
        def question = "Zrobiłem dzisiaj wyciskanie na ławce: 3 serie po 10 powtórzeń z 80kg"
        def result = benchmarkChat(question)
        recordBenchmark("BM-M04", "Record workout with exercises", result)

        then: "LLM judge validates workout recording"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality.

            The response MUST:
            1. Confirm recording a bench press workout
            2. Reference 3 sets, 10 reps, 80 kg

            FAIL if:
            - Refuses to record
            - Wrong exercise details
        """)
        println "LLM Judge BM-M04: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-M04", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-M05: Record Sleep ====================

    @Unroll
    def "BM-M05: Record sleep session [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "user reports sleep"
        def question = "Spałem od 23:00 do 7:00"
        def result = benchmarkChat(question)
        recordBenchmark("BM-M05", "Record sleep session", result)

        then: "LLM judge validates sleep recording"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality.

            The response MUST:
            1. Confirm recording sleep of approximately 8 hours (480 minutes)
            2. NOT refuse to record

            FAIL if:
            - Wrong duration (not ~8 hours)
            - Refuses to record
        """)
        println "LLM Judge BM-M05: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-M05", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-M06: Record then Query ====================

    @Unroll
    def "BM-M06: Record meal then query calories [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "user records a meal"
        askAssistantStart("Zjadłem kanapkę z szynką i serem, 400 kcal, 25g białka, 18g tłuszczu, 35g węgli")

        and: "then asks about calories"
        def question = "Ile kalorii zjadłem dzisiaj?"
        def result = benchmarkChatContinue(question)
        recordBenchmark("BM-M06", "Record meal then query calories", result)

        then: "LLM judge validates calorie report"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality.

            CONTEXT: User just recorded a 400 kcal sandwich.

            The response MUST:
            1. Report approximately 400 calories consumed today
            2. Reference the sandwich that was just recorded

            FAIL if:
            - Says no meal data available
            - Reports wrong calorie count (not ~400)
        """)
        println "LLM Judge BM-M06: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-M06", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-M07: Delete Workout Flow ====================

    @Unroll
    def "BM-M07: Delete workout from today [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        and: "a workout exists for today"
        submitWorkout("Bench Press", 3, 10, 80.0)
        waitForProjections()

        when: "user asks to delete workout"
        def question = "Usuń mój dzisiejszy trening"
        def result = benchmarkChat(question)
        recordBenchmark("BM-M07", "Delete workout from today", result)

        then: "LLM judge validates deletion flow"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality.

            The response MUST either:
            1. Confirm the workout was deleted, OR
            2. Ask for confirmation before deleting (showing workout details), OR
            3. Show the found workout and offer to delete it

            All three behaviors are acceptable.

            FAIL if:
            - Says it cannot delete workouts
            - Says no workout found when one exists
        """)
        println "LLM Judge BM-M07: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-M07", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-M08: Speculative Intent - Should NOT Record ====================

    @Unroll
    def "BM-M08: Do not record speculative meal [#modelName]"() {
        given: "model is set"
        currentModel = modelName

        when: "user expresses intent without eating"
        def question = "Myślę, że dzisiaj zjem pizzę na obiad"
        def result = benchmarkChat(question)
        recordBenchmark("BM-M08", "Do not record speculative meal", result)

        then: "LLM judge validates no recording happened"
        result.passed
        def judgeResult = llmAsJudge(question, result.response, """
            Evaluate ONLY the text response quality.

            The user said they THINK they will eat pizza - this is just a thought, not a statement of having eaten.

            The response MUST:
            1. NOT confirm recording a meal
            2. Either discuss pizza or respond naturally without recording

            FAIL if:
            - Confirms recording pizza as a meal (user didn't eat it yet)
        """)
        println "LLM Judge BM-M08: ${judgeResult}"
        updateBenchmarkWithJudgeResult("BM-M08", judgeResult)
        judgeResult.score >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }
}
