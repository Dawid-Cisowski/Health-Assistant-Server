package com.healthassistant.benchmark

import io.restassured.RestAssured
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title
import spock.lang.Unroll

import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * AI Benchmark Tests for Medical Exam Import.
 *
 * Measures quality, cost (tokens), and response time for medical exam image imports.
 * Each test runs for ALL models specified in BENCHMARK_MODELS env var.
 *
 * Ground truth values (from actual test images):
 *   morphology.png: WBC=4.18, RBC=3.32 (low), HCT=33.80 (low), PLT=266, ~23 markers
 *   usg.png: ABDOMINAL_USG, descriptive report text, no numeric results
 *
 * Run with:
 *   GEMINI_API_KEY=key ./gradlew :integration-tests:test --tests "*AiMedicalExamBenchmarkSpec"
 *
 * Run with multiple models:
 *   GEMINI_API_KEY=key BENCHMARK_MODELS="gemini-2.0-flash,gemini-2.0-pro-exp-02-05" \
 *     ./gradlew :integration-tests:test --tests "*AiMedicalExamBenchmarkSpec"
 */
@Title("AI Benchmark Tests - Medical Exam Import")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 300, unit = TimeUnit.SECONDS)
class AiMedicalExamBenchmarkSpec extends BaseBenchmarkSpec {

    private static final String ANALYZE_ENDPOINT = "/v1/medical-exams/import/analyze"
    private static final String MORPHOLOGY_IMAGE_PATH = "/screenshots/medical /morphology.png"
    private static final String USG_IMAGE_PATH = "/screenshots/medical /usg.png"

    // ==================== BM-MED-01: Morphology extraction accuracy ====================

    @Unroll
    def "BM-MED-01: Morphology image extraction - exam type and marker count [#modelName]"() {
        given: "model is set"
        currentModel = modelName
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "importing morphology image"
        def result = benchmarkMedicalExamImport(imageBytes, "morphology.png")
        recordBenchmark("BM-MED-01", "Morphology extraction accuracy", result)

        then: "import succeeded with correct exam type and sufficient markers"
        result.passed
        def body = new groovy.json.JsonSlurper().parseText(result.response)
        println "DEBUG BM-MED-01 [${modelName}]: examType=${body.examTypeCode}, results=${body.results?.size()}, confidence=${body.confidence}"

        body.examTypeCode == "MORPHOLOGY"
        body.results != null
        body.results.size() >= 15
        body.confidence >= 0.7

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-MED-02: WBC marker extraction accuracy ====================

    @Unroll
    def "BM-MED-02: WBC marker extraction from morphology image [#modelName]"() {
        given: "model is set"
        currentModel = modelName
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "importing morphology image"
        def result = benchmarkMedicalExamImport(imageBytes, "morphology.png")
        recordBenchmark("BM-MED-02", "WBC marker extraction", result)

        then: "WBC is extracted within expected range (expected ~4.18 tys/ul)"
        result.passed
        def body = new groovy.json.JsonSlurper().parseText(result.response)
        def wbc = body.results?.find { it.markerCode == "WBC" }
        println "DEBUG BM-MED-02 [${modelName}]: WBC=${wbc?.valueNumeric} (expected ~4.18)"

        wbc != null
        def wbcVal = wbc.valueNumeric as float
        wbcVal >= 3.5f && wbcVal <= 5.0f

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-MED-03: USG descriptive report extraction ====================

    @Unroll
    def "BM-MED-03: USG descriptive report extraction accuracy [#modelName]"() {
        given: "model is set"
        currentModel = modelName
        def imageBytes = loadTestImage(USG_IMAGE_PATH)

        when: "importing USG image"
        def result = benchmarkMedicalExamImport(imageBytes, "usg.png")
        recordBenchmark("BM-MED-03", "USG descriptive report extraction", result)

        then: "USG type is identified and report text extracted, no numeric results"
        result.passed
        def body = new groovy.json.JsonSlurper().parseText(result.response)
        println "DEBUG BM-MED-03 [${modelName}]: examType=${body.examTypeCode}, reportText=${body.reportText?.take(80)}, results=${body.results?.size()}"

        body.examTypeCode == "ABDOMINAL_USG"
        body.reportText != null
        body.reportText.length() > 30
        body.results == null || body.results.size() == 0

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== BM-MED-04: Full import flow response time ====================

    @Unroll
    def "BM-MED-04: Full morphology import flow response time [#modelName]"() {
        given: "model is set"
        currentModel = modelName
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "running full analyze flow and measuring time"
        def startTime = System.currentTimeMillis()
        def analyzeResult = benchmarkMedicalExamImport(imageBytes, "morphology.png")
        def totalTimeMs = System.currentTimeMillis() - startTime

        def timedResult = BenchmarkResult.builder()
                .model(currentModel)
                .response(analyzeResult.response)
                .passed(analyzeResult.passed)
                .errorMessage(analyzeResult.errorMessage)
                .inputTokens(analyzeResult.inputTokens)
                .outputTokens(analyzeResult.outputTokens)
                .estimatedCostUsd(analyzeResult.estimatedCostUsd)
                .responseTimeMs(totalTimeMs)
                .timestamp(Instant.now())
                .build()
        recordBenchmark("BM-MED-04", "Full morphology import flow performance", timedResult)

        then: "analysis completed within 60 seconds"
        analyzeResult.passed
        println "DEBUG BM-MED-04 [${modelName}]: totalTimeMs=${totalTimeMs}"
        totalTimeMs < 60_000L

        cleanup:
        cleanAllData()

        where:
        modelName << BENCHMARK_MODELS
    }

    // ==================== Benchmark Helper ====================

    /**
     * Imports a medical exam from an image and measures response metrics.
     * Returns BenchmarkResult with the analyze endpoint response as JSON string.
     */
    BenchmarkResult benchmarkMedicalExamImport(byte[] imageBytes, String fileName) {
        def deviceId = getTestDeviceId()
        def startTime = System.currentTimeMillis()

        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", ANALYZE_ENDPOINT, timestamp, nonce, deviceId, "", TEST_SECRET_BASE64)

        def response = RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart("files", fileName, imageBytes, "image/png")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()

        def endTime = System.currentTimeMillis()
        def statusCode = response.statusCode()
        def bodyString = response.body().asString()
        def status = response.body().jsonPath().getString("status")

        return BenchmarkResult.builder()
                .model(currentModel)
                .response(bodyString)
                .passed(statusCode == 200 && "PENDING" == status)
                .errorMessage(statusCode != 200 ? "HTTP ${statusCode}: ${bodyString?.take(200)}" : null)
                .inputTokens(0L)
                .outputTokens(0L)
                .estimatedCostUsd(0.0)
                .responseTimeMs(endTime - startTime)
                .timestamp(Instant.now())
                .build()
    }
}
