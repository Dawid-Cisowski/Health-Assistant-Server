package com.healthassistant.benchmark

import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title
import spock.lang.Unroll

import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * AI Benchmark Tests for Meal Catalog via meal import endpoint.
 *
 * Measures quality, cost (tokens), and response time for catalog-based meal imports.
 * Each test runs for ALL models specified in BENCHMARK_MODELS env var.
 *
 * Run with:
 *   GEMINI_API_KEY=key ./gradlew :integration-tests:test --tests "*AiMealCatalogBenchmarkSpec"
 *
 * Run with multiple models:
 *   GEMINI_API_KEY=key BENCHMARK_MODELS="gemini-2.0-flash,gemini-2.0-pro-exp-02-05" \
 *     ./gradlew :integration-tests:test --tests "*AiMealCatalogBenchmarkSpec"
 */
@Title("AI Benchmark Tests - Meal Catalog Import")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 300, unit = TimeUnit.SECONDS)
class AiMealCatalogBenchmarkSpec extends BaseBenchmarkSpec {

    private static final String IMPORT_ENDPOINT = "/v1/meals/import"

    // ==================== BM-CAT-01: Banana from catalog ====================

    @Unroll
    def "BM-CAT-01: Import banana from image with catalog [#modelName]"() {
        given: "model is set and banana is in catalog"
        currentModel = modelName
        seedCatalogProduct("Banan", "SNACK", 95, 1, 0, 24, "HEALTHY")
        def imageBytes = loadTestImage("/screenshots/meal/banana.png")

        when: "importing banana from image"
        def result = benchmarkMealImageImport(imageBytes, "banana.png")
        recordBenchmark("BM-CAT-01", "Import banana from catalog", result)

        then: "import succeeded and values are EXACT from catalog"
        result.passed
        def body = new groovy.json.JsonSlurper().parseText(result.response)
        println "DEBUG BM-CAT-01: calories=${body.caloriesKcal}, protein=${body.proteinGrams}, fat=${body.fatGrams}, carbs=${body.carbohydratesGrams}"

        body.caloriesKcal == 95
        body.proteinGrams == 1
        body.fatGrams == 0
        body.carbohydratesGrams == 24

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-CAT-02: Skyr from catalog ====================

    @Unroll
    def "BM-CAT-02: Import skyr from image with catalog [#modelName]"() {
        given: "model is set and skyr is in catalog"
        currentModel = modelName
        seedCatalogProduct("Skyr pitny z Piatnicy", "SNACK", 120, 20, 2, 6, "HEALTHY")
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing skyr from image"
        def result = benchmarkMealImageImport(imageBytes, "skyr.png")
        recordBenchmark("BM-CAT-02", "Import skyr from catalog", result)

        then: "import succeeded and values are EXACT from catalog"
        result.passed
        def body = new groovy.json.JsonSlurper().parseText(result.response)
        println "DEBUG BM-CAT-02: calories=${body.caloriesKcal}, protein=${body.proteinGrams}, fat=${body.fatGrams}, carbs=${body.carbohydratesGrams}"

        body.caloriesKcal == 120
        body.proteinGrams == 20
        body.fatGrams == 2
        body.carbohydratesGrams == 6

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-CAT-03: Banana without catalog (estimation) ====================

    @Unroll
    def "BM-CAT-03: Import banana from image without catalog [#modelName]"() {
        given: "model is set and catalog is empty"
        currentModel = modelName
        def imageBytes = loadTestImage("/screenshots/meal/banana.png")

        when: "importing banana without catalog"
        def result = benchmarkMealImageImport(imageBytes, "banana.png")
        recordBenchmark("BM-CAT-03", "Import banana without catalog", result)

        then: "import succeeded with reasonable estimated values"
        result.passed
        def body = new groovy.json.JsonSlurper().parseText(result.response)
        println "DEBUG BM-CAT-03: calories=${body.caloriesKcal}, protein=${body.proteinGrams}, fat=${body.fatGrams}, carbs=${body.carbohydratesGrams}"

        body.caloriesKcal >= 80 && body.caloriesKcal <= 135
        body.carbohydratesGrams >= 18 && body.carbohydratesGrams <= 40

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-CAT-04: Skyr without catalog (estimation) ====================

    @Unroll
    def "BM-CAT-04: Import skyr from image without catalog [#modelName]"() {
        given: "model is set and catalog is empty"
        currentModel = modelName
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing skyr without catalog"
        def result = benchmarkMealImageImport(imageBytes, "skyr.png")
        recordBenchmark("BM-CAT-04", "Import skyr without catalog", result)

        then: "import succeeded with reasonable values"
        result.passed
        def body = new groovy.json.JsonSlurper().parseText(result.response)
        println "DEBUG BM-CAT-04: calories=${body.caloriesKcal}, protein=${body.proteinGrams}"

        body.caloriesKcal > 0
        body.proteinGrams > 0

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Benchmark Helper ====================

    /**
     * Import meal from image and measure all metrics.
     */
    BenchmarkResult benchmarkMealImageImport(byte[] imageBytes, String fileName) {
        def deviceId = getTestDeviceId()
        def startTime = System.currentTimeMillis()

        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", IMPORT_ENDPOINT, timestamp, nonce, deviceId, "", TEST_SECRET_BASE64)

        def response = RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart("images", fileName, imageBytes, "image/png")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        def endTime = System.currentTimeMillis()
        def statusCode = response.statusCode()
        def bodyString = response.body().asString()
        def body = response.body().jsonPath()

        def status = body.getString("status")
        def inputTokens = body.getLong("promptTokens") ?: 0L
        def outputTokens = body.getLong("completionTokens") ?: 0L

        return BenchmarkResult.builder()
                .model(currentModel)
                .response(bodyString)
                .passed(statusCode == 200 && status == "success")
                .errorMessage(status != "success" ? body.getString("errorMessage") : null)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .estimatedCostUsd(BenchmarkResult.calculateCost(currentModel, inputTokens, outputTokens))
                .responseTimeMs(endTime - startTime)
                .timestamp(Instant.now())
                .build()
    }
}
