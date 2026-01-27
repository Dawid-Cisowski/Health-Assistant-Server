package com.healthassistant.benchmark

import com.healthassistant.evaluation.BaseEvaluationSpec
import groovy.json.JsonSlurper
import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import io.restassured.http.ContentType
import spock.lang.Shared

import java.time.Instant
import java.time.LocalDate

/**
 * Base class for AI benchmark tests.
 * Extends BaseEvaluationSpec and adds benchmark-specific methods for measuring
 * quality, cost (tokens), and time metrics.
 *
 * Supports multiple models via BENCHMARK_MODELS environment variable (comma-separated).
 * Example: BENCHMARK_MODELS="gemini-2.0-flash,gemini-2.0-pro-exp-02-05"
 */
abstract class BaseBenchmarkSpec extends BaseEvaluationSpec {

    /**
     * List of models to benchmark. Parsed from BENCHMARK_MODELS env var.
     * If not set, falls back to GEMINI_MODEL or default "gemini-2.0-flash".
     */
    @Shared
    static List<String> BENCHMARK_MODELS = parseModels()

    // Current model being tested (set per test via where: block)
    String currentModel

    private static List<String> parseModels() {
        def modelsEnv = System.getenv("BENCHMARK_MODELS")
        if (modelsEnv) {
            return modelsEnv.split(",").collect { it.trim() }.findAll { it }
        }
        // Fallback to single model
        def singleModel = System.getenv("GEMINI_MODEL") ?: "gemini-2.0-flash"
        return [singleModel]
    }

    def setupSpec() {
        BenchmarkReporter.clear()
        println "=== BENCHMARK MODELS: ${BENCHMARK_MODELS.join(', ')} ==="
    }

    def cleanupSpec() {
        BenchmarkReporter.printConsoleReport()
    }

    // ==================== Benchmark Methods ====================

    /**
     * Send chat message and measure all metrics.
     * Returns BenchmarkResult with response, tokens, and timing.
     *
     * Note: Token counts are estimated since the SSE endpoint doesn't return usage data.
     * - Input: ~1500 (system prompt) + message tokens
     * - Output: response content tokens
     */
    BenchmarkResult benchmarkChat(String message) {
        def deviceId = getTestDeviceId()
        def startTime = System.currentTimeMillis()
        Long ttft = null

        def chatRequest = """{"message": "${escapeJson(message)}"}"""
        def response = authenticatedPostRequestWithBody(
                deviceId, TEST_SECRET_BASE64,
                "/v1/assistant/chat", chatRequest
        )
                .when()
                .post("/v1/assistant/chat")
                .then()
                .extract()

        def endTime = System.currentTimeMillis()
        def responseBody = response.body().asString()
        def statusCode = response.statusCode()

        // Parse SSE response
        def content = parseSSEContent(responseBody)

        // Calculate TTFT from first content event timestamp (approximation)
        ttft = parseSSETTFT(responseBody, startTime)

        // Estimate tokens (SSE endpoint doesn't return usage data)
        // System prompt ~1500 tokens + user message
        def estimatedInputTokens = 1500L + estimateTokens(message)
        // Output based on response length
        def estimatedOutputTokens = estimateTokens(content ?: "")

        return BenchmarkResult.builder()
                .model(currentModel)
                .response(content)
                .passed(statusCode == 200 && content?.length() > 0)
                .errorMessage(statusCode != 200 ? "HTTP ${statusCode}" : null)
                .inputTokens(estimatedInputTokens)
                .outputTokens(estimatedOutputTokens)
                .estimatedCostUsd(BenchmarkResult.calculateCost(currentModel, estimatedInputTokens, estimatedOutputTokens))
                .responseTimeMs(endTime - startTime)
                .ttftMs(ttft)
                .timestamp(Instant.now())
                .build()
    }

    /**
     * Estimate token count from text.
     * Rough approximation: ~4 characters per token for English, ~2 for Polish/mixed.
     */
    private static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0L
        // Use ~3 chars per token as middle ground for mixed content
        return Math.max(1L, (text.length() / 3) as long)
    }

    /**
     * Request AI daily summary and measure metrics.
     */
    BenchmarkResult benchmarkAiSummary(LocalDate date) {
        def deviceId = getTestDeviceId()
        def dateStr = date.toString()
        def startTime = System.currentTimeMillis()

        def response = authenticatedGetRequest(deviceId, TEST_SECRET_BASE64, "/v1/daily-summaries/${dateStr}/ai-text")
                .get("/v1/daily-summaries/${dateStr}/ai-text")
                .then()
                .extract()

        def endTime = System.currentTimeMillis()
        def statusCode = response.statusCode()
        def body = response.body().jsonPath()

        def summary = body.getString("summary") ?: ""
        def dataAvailable = body.getBoolean("dataAvailable")

        // AI summary endpoint doesn't return token usage, estimate based on response length
        def estimatedOutputTokens = (summary.length() / 4) as long
        def estimatedInputTokens = 500L  // Approximate system prompt + context

        return BenchmarkResult.builder()
                .model(currentModel)
                .response(summary)
                .passed(statusCode == 200 && dataAvailable && summary.length() > 10)
                .errorMessage(statusCode != 200 ? "HTTP ${statusCode}" : null)
                .inputTokens(estimatedInputTokens)
                .outputTokens(estimatedOutputTokens)
                .estimatedCostUsd(BenchmarkResult.calculateCost(currentModel, estimatedInputTokens, estimatedOutputTokens))
                .responseTimeMs(endTime - startTime)
                .timestamp(Instant.now())
                .build()
    }

    /**
     * Import meal from text description and measure metrics.
     * Uses /v1/meals/import multipart endpoint with description parameter.
     */
    BenchmarkResult benchmarkMealImport(String description) {
        def deviceId = getTestDeviceId()
        def startTime = System.currentTimeMillis()

        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", "/v1/meals/import", timestamp, nonce, deviceId, "", TEST_SECRET_BASE64)

        def response = RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart("description", description)
                .post("/v1/meals/import")
                .then()
                .extract()

        def endTime = System.currentTimeMillis()
        def statusCode = response.statusCode()
        def body = response.body().jsonPath()

        def status = body.getString("status")
        def caloriesKcal = body.get("caloriesKcal")
        def proteinGrams = body.get("proteinGrams")
        def fatGrams = body.get("fatGrams")
        def carbohydratesGrams = body.get("carbohydratesGrams")

        // Estimate tokens for meal import
        def estimatedInputTokens = 300L + (description.length() / 4) as long
        def estimatedOutputTokens = 100L

        def result = BenchmarkResult.builder()
                .model(currentModel)
                .response(response.body().asString())
                .passed(statusCode == 200 && status == "success")
                .errorMessage(status != "success" ? body.getString("errorMessage") : null)
                .inputTokens(estimatedInputTokens)
                .outputTokens(estimatedOutputTokens)
                .estimatedCostUsd(BenchmarkResult.calculateCost(currentModel, estimatedInputTokens, estimatedOutputTokens))
                .responseTimeMs(endTime - startTime)
                .timestamp(Instant.now())
                .build()

        // Attach parsed response for assertions
        result.metaClass.parsedResponse = [
                caloriesKcal      : caloriesKcal,
                proteinGrams      : proteinGrams,
                fatGrams          : fatGrams,
                carbohydratesGrams: carbohydratesGrams
        ]

        return result
    }

    /**
     * Import sleep from screenshot image and measure metrics.
     */
    BenchmarkResult benchmarkSleepImport(byte[] imageBytes, String fileName) {
        def deviceId = getTestDeviceId()
        def startTime = System.currentTimeMillis()

        def multiPartSpec = new MultiPartSpecBuilder(imageBytes)
                .fileName(fileName)
                .controlName("image")
                .mimeType("image/png")
                .build()

        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", "/v1/sleep/import-image", timestamp, nonce, deviceId, "", TEST_SECRET_BASE64)

        def response = RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart(multiPartSpec)
                .post("/v1/sleep/import-image")
                .then()
                .extract()

        def endTime = System.currentTimeMillis()
        def statusCode = response.statusCode()
        def body = response.body().jsonPath()

        def status = body.getString("status")
        def totalSleepMinutes = body.get("totalSleepMinutes")
        def sleepScore = body.get("sleepScore")

        // Vision API uses more tokens
        def estimatedInputTokens = 1000L  // Image tokens
        def estimatedOutputTokens = 100L

        def result = BenchmarkResult.builder()
                .model(currentModel)
                .response(response.body().asString())
                .passed(statusCode == 200 && status == "success")
                .errorMessage(status != "success" ? body.getString("errorMessage") : null)
                .inputTokens(estimatedInputTokens)
                .outputTokens(estimatedOutputTokens)
                .estimatedCostUsd(BenchmarkResult.calculateCost(currentModel, estimatedInputTokens, estimatedOutputTokens))
                .responseTimeMs(endTime - startTime)
                .timestamp(Instant.now())
                .build()

        // Attach parsed response for assertions
        result.metaClass.parsedResponse = [
                totalSleepMinutes: totalSleepMinutes,
                sleepScore       : sleepScore
        ]

        return result
    }

    /**
     * Record benchmark result with test ID and name.
     */
    void recordBenchmark(String testId, String testName, BenchmarkResult result) {
        result.testId = testId
        result.testName = testName
        BenchmarkReporter.recordResult(result)
        println "DEBUG: Recorded ${testId} - ${testName}: passed=${result.passed}, tokens=${result.inputTokens}/${result.outputTokens}, time=${result.responseTimeMs}ms"
    }

    // ==================== Helper Methods ====================

    /**
     * Estimate TTFT from SSE response timing.
     */
    Long parseSSETTFT(String sseResponse, long requestStartMs) {
        // Find first content event
        def lines = sseResponse.split("\n")
        lines.each { line ->
            if (line.startsWith("data:") && line.contains('"type":"content"')) {
                // First content received - approximate TTFT
                return System.currentTimeMillis() - requestStartMs
            }
        }
        return null
    }

    def authenticatedGetRequest(String deviceId, String secretBase64, String path) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("GET", path, timestamp, nonce, deviceId, "", secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
    }

    /**
     * Load screenshot from test resources.
     */
    byte[] loadScreenshot(String resourcePath) {
        def stream = getClass().getResourceAsStream(resourcePath)
        if (stream == null) {
            throw new IllegalArgumentException("Screenshot not found: ${resourcePath}")
        }
        return stream.bytes
    }

    /**
     * Submit sleep for a specific date.
     */
    void submitSleepForDate(LocalDate date, int totalMinutes) {
        def sleepEnd = date.atTime(7, 0).atZone(POLAND_ZONE).toInstant()
        def sleepStart = sleepEnd.minusSeconds(totalMinutes * 60L)

        def request = [
                deviceId: getTestDeviceId(),
                events  : [[
                                   idempotencyKey: UUID.randomUUID().toString(),
                                   type          : "SleepSessionRecorded.v1",
                                   occurredAt    : sleepEnd.toString(),
                                   payload       : [
                                           sleepId      : UUID.randomUUID().toString(),
                                           sleepStart   : sleepStart.toString(),
                                           sleepEnd     : sleepEnd.toString(),
                                           totalMinutes : totalMinutes,
                                           originPackage: "com.test"
                                   ]
                           ]]
        ]
        submitEventMap(request)
    }
}
