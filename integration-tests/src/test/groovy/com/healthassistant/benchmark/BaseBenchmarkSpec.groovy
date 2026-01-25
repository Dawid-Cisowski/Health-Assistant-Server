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
 */
abstract class BaseBenchmarkSpec extends BaseEvaluationSpec {

    @Shared
    String currentModel = System.getenv("GEMINI_MODEL") ?: "gemini-2.0-flash"

    def setupSpec() {
        BenchmarkReporter.clear()
    }

    def cleanupSpec() {
        BenchmarkReporter.printConsoleReport()
    }

    // ==================== Benchmark Methods ====================

    /**
     * Send chat message and measure all metrics.
     * Returns BenchmarkResult with response, tokens, and timing.
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
        def tokenInfo = parseSSETokenUsage(responseBody)

        // Calculate TTFT from first content event timestamp (approximation)
        ttft = parseSSETTFT(responseBody, startTime)

        def inputTokens = tokenInfo.inputTokens ?: 0L
        def outputTokens = tokenInfo.outputTokens ?: 0L

        return BenchmarkResult.builder()
                .model(currentModel)
                .response(content)
                .passed(statusCode == 200 && content?.length() > 0)
                .errorMessage(statusCode != 200 ? "HTTP ${statusCode}" : null)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .estimatedCostUsd(BenchmarkResult.calculateCost(currentModel, inputTokens, outputTokens))
                .responseTimeMs(endTime - startTime)
                .ttftMs(ttft)
                .timestamp(Instant.now())
                .build()
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
     * Parse token usage from SSE response (from done event).
     */
    Map parseSSETokenUsage(String sseResponse) {
        def inputTokens = 0L
        def outputTokens = 0L

        sseResponse.split("\n\n").each { event ->
            event.split("\n").each { line ->
                if (line.startsWith("data:")) {
                    def json = line.substring(5).trim()
                    if (json && json.contains('"type":"done"')) {
                        // Try to extract token info from done event
                        try {
                            def parsed = new JsonSlurper().parseText(json)
                            if (parsed.usage) {
                                inputTokens = parsed.usage.promptTokens ?: parsed.usage.inputTokens ?: 0L
                                outputTokens = parsed.usage.completionTokens ?: parsed.usage.outputTokens ?: 0L
                            }
                        } catch (Exception ignored) {
                            // Token info not available in response
                        }
                    }
                }
            }
        }

        return [inputTokens: inputTokens, outputTokens: outputTokens]
    }

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
