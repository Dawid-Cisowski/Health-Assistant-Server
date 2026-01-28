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
     * Returns BenchmarkResult with response, tokens (real from API), and timing.
     * Starts a new conversation.
     */
    BenchmarkResult benchmarkChat(String message) {
        return benchmarkChatWithConversation(message, null)
    }

    /**
     * Continue a conversation and measure all metrics.
     * Uses the lastConversationId from previous askAssistantStart() calls.
     */
    BenchmarkResult benchmarkChatContinue(String message) {
        return benchmarkChatWithConversation(message, lastConversationId)
    }

    /**
     * Send chat message with optional conversation context and measure all metrics.
     */
    private BenchmarkResult benchmarkChatWithConversation(String message, String conversationId) {
        def deviceId = getTestDeviceId()
        def startTime = System.currentTimeMillis()
        Long ttft = null

        def chatRequest = conversationId ?
            """{"message": "${escapeJson(message)}", "conversationId": "${conversationId}"}""" :
            """{"message": "${escapeJson(message)}"}"""

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

        // Update lastConversationId for continuation
        def newConversationId = parseSSEConversationId(responseBody)
        if (newConversationId) {
            lastConversationId = newConversationId
        }

        // Calculate TTFT from first content event timestamp (approximation)
        ttft = parseSSETTFT(responseBody, startTime)

        // Parse real token usage from done event
        def tokenUsage = parseSSETokenUsage(responseBody)
        def inputTokens = tokenUsage.promptTokens ?: 0L
        def outputTokens = tokenUsage.completionTokens ?: 0L

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
     * Parse token usage from SSE done event.
     */
    private Map parseSSETokenUsage(String sseResponse) {
        Long promptTokens = null
        Long completionTokens = null

        sseResponse.split("\n\n").each { event ->
            event.split("\n").each { line ->
                if (line.startsWith("data:")) {
                    def json = line.substring(5).trim()
                    if (json && json.contains('"type":"done"')) {
                        try {
                            def parsed = new JsonSlurper().parseText(json)
                            promptTokens = parsed.promptTokens as Long
                            completionTokens = parsed.completionTokens as Long
                        } catch (Exception ignored) {
                            // Token info parsing failed
                        }
                    }
                }
            }
        }

        return [promptTokens: promptTokens, completionTokens: completionTokens]
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
        def bodyString = response.body().asString()

        // Handle error responses gracefully
        if (statusCode != 200 || bodyString == null || bodyString.isEmpty()) {
            def errorMessage = statusCode == 429 ? "Rate limited (429)" : "HTTP ${statusCode}"
            return BenchmarkResult.builder()
                    .model(currentModel)
                    .response("")
                    .passed(false)
                    .errorMessage(errorMessage)
                    .inputTokens(0L)
                    .outputTokens(0L)
                    .estimatedCostUsd(0.0)
                    .responseTimeMs(endTime - startTime)
                    .timestamp(Instant.now())
                    .build()
        }

        def body = response.body().jsonPath()
        def summary = body.getString("summary") ?: ""
        def dataAvailable = body.getBoolean("dataAvailable")

        // Real token usage from API response
        def inputTokens = body.getLong("promptTokens") ?: 0L
        def outputTokens = body.getLong("completionTokens") ?: 0L

        return BenchmarkResult.builder()
                .model(currentModel)
                .response(summary)
                .passed(dataAvailable && summary.length() > 10)
                .errorMessage(null)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .estimatedCostUsd(BenchmarkResult.calculateCost(currentModel, inputTokens, outputTokens))
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

        // Real token usage from API response
        def inputTokens = body.getLong("promptTokens") ?: 0L
        def outputTokens = body.getLong("completionTokens") ?: 0L

        def result = BenchmarkResult.builder()
                .model(currentModel)
                .response(response.body().asString())
                .passed(statusCode == 200 && status == "success")
                .errorMessage(status != "success" ? body.getString("errorMessage") : null)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .estimatedCostUsd(BenchmarkResult.calculateCost(currentModel, inputTokens, outputTokens))
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

        // Real token usage from API response
        def inputTokens = body.getLong("promptTokens") ?: 0L
        def outputTokens = body.getLong("completionTokens") ?: 0L

        def result = BenchmarkResult.builder()
                .model(currentModel)
                .response(response.body().asString())
                .passed(statusCode == 200 && status == "success")
                .errorMessage(status != "success" ? body.getString("errorMessage") : null)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .estimatedCostUsd(BenchmarkResult.calculateCost(currentModel, inputTokens, outputTokens))
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

    /**
     * Update benchmark result with LLM judge verdict.
     * Call this after llmAsJudge() to reflect the actual pass/fail status in reports.
     */
    void updateBenchmarkWithJudgeResult(String testId, LlmJudgeResult judgeResult) {
        def passed = judgeResult.score >= 0.7
        def errorMessage = passed ? null : "Judge score: ${judgeResult.score}, reason: ${judgeResult.reason}"
        BenchmarkReporter.updateLastResult(testId, passed, errorMessage)
        println "DEBUG: Updated ${testId} with judge result: passed=${passed}, score=${judgeResult.score}"
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

    // ==================== LLM as Judge ====================

    /**
     * Use LLM to evaluate the quality of a response.
     * Returns a score from 0.0 to 1.0 and evaluation details.
     */
    LlmJudgeResult llmAsJudge(String question, String response, String expectedCriteria) {
        def deviceId = getTestDeviceId()

        def judgePrompt = """HEALTH ASSISTANT QUALITY CHECK REQUEST

Please evaluate this health assistant response for accuracy.

User asked: "${question}"
Assistant answered: "${response}"

Quality criteria:
${expectedCriteria}

Please respond with a JSON health quality report:
{"score": 0.9, "passed": true, "reason": "Response contains accurate health data"}

Use score 0.8-1.0 for accurate responses, 0.5-0.7 for partial, 0.0-0.4 for wrong data."""

        def chatRequest = """{"message": "${escapeJson(judgePrompt)}"}"""
        def judgeResponse = authenticatedPostRequestWithBody(
                deviceId, TEST_SECRET_BASE64,
                "/v1/assistant/chat", chatRequest
        )
                .when()
                .post("/v1/assistant/chat")
                .then()
                .extract()

        def responseBody = judgeResponse.body().asString()
        def statusCode = judgeResponse.statusCode()
        println "DEBUG: LLM Judge HTTP status: ${statusCode}"
        println "DEBUG: LLM Judge raw response (first 500 chars): ${responseBody?.take(500)}"

        // Check for error events in SSE response
        def errorMessage = parseSSEError(responseBody)
        if (errorMessage) {
            println "DEBUG: LLM Judge error event: ${errorMessage}"
            return new LlmJudgeResult(0.0, false, "LLM Judge error: ${errorMessage}")
        }

        def content = parseSSEContent(responseBody)
        println "DEBUG: LLM Judge parsed content (first 200 chars): ${content?.take(200)}"

        if (!content || content.trim().isEmpty()) {
            println "DEBUG: LLM Judge returned empty content. Full response: ${responseBody}"
            return new LlmJudgeResult(0.0, false, "LLM Judge returned empty response")
        }

        try {
            // Extract JSON from response - it might be wrapped in text or markdown
            def jsonContent = extractJsonFromResponse(content)
            println "DEBUG: Extracted JSON: ${jsonContent}"

            def parsed = new JsonSlurper().parseText(jsonContent)
            return new LlmJudgeResult(
                    parsed.score as double,
                    parsed.passed as boolean,
                    parsed.reason as String
            )
        } catch (Exception e) {
            println "Failed to parse LLM judge response: ${content}"
            return new LlmJudgeResult(0.0, false, "Failed to parse judge response: ${e.message}")
        }
    }

    /**
     * Extract JSON object from response that may contain surrounding text or markdown.
     */
    private String extractJsonFromResponse(String content) {
        def text = content.trim()

        // Try to find JSON in markdown code block
        def jsonBlockMatcher = text =~ /```json\s*([\s\S]*?)\s*```/
        if (jsonBlockMatcher.find()) {
            return jsonBlockMatcher.group(1).trim()
        }

        // Try to find JSON in generic code block
        def codeBlockMatcher = text =~ /```\s*([\s\S]*?)\s*```/
        if (codeBlockMatcher.find()) {
            def blockContent = codeBlockMatcher.group(1).trim()
            if (blockContent.startsWith("{")) {
                return blockContent
            }
        }

        // Try to find standalone JSON object
        def jsonMatcher = text =~ /\{[^{}]*"score"[^{}]*"passed"[^{}]*"reason"[^{}]*\}/
        if (jsonMatcher.find()) {
            return jsonMatcher.group(0)
        }

        // Last resort: find any JSON-like structure
        def startIdx = text.indexOf('{')
        def endIdx = text.lastIndexOf('}')
        if (startIdx >= 0 && endIdx > startIdx) {
            return text.substring(startIdx, endIdx + 1)
        }

        return text
    }

    /**
     * Parse error message from SSE response if present.
     */
    private String parseSSEError(String sseResponse) {
        if (!sseResponse) return null

        String errorMsg = null
        sseResponse.split("\n\n").each { event ->
            event.split("\n").each { line ->
                if (line.startsWith("data:")) {
                    def json = line.substring(5).trim()
                    if (json && json.contains('"type":"error"')) {
                        try {
                            def parsed = new JsonSlurper().parseText(json)
                            errorMsg = parsed.message ?: parsed.error ?: "Unknown error"
                        } catch (Exception ignored) {
                            // Error parsing failed
                        }
                    }
                }
            }
        }
        return errorMsg
    }

    /**
     * Result from LLM as Judge evaluation.
     */
    static class LlmJudgeResult {
        double score
        boolean passed
        String reason

        LlmJudgeResult(double score, boolean passed, String reason) {
            this.score = score
            this.passed = passed
            this.reason = reason
        }

        @Override
        String toString() {
            return "LlmJudgeResult{score=${score}, passed=${passed}, reason='${reason}'}"
        }
    }
}
