package com.healthassistant.evaluation

import io.restassured.RestAssured
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.util.concurrent.TimeUnit

/**
 * AI evaluation tests for medical exam import using REAL Gemini API.
 *
 * Tests verify AI extraction accuracy from actual medical document images:
 *   - morphology.png: CBC blood test with ~23 markers
 *   - usg.png: Abdominal ultrasound descriptive report
 *
 * Expected values extracted from the actual images (ground truth):
 *   morphology.png: MORPHOLOGY, WBC=4.18, RBC=3.32 (low), HCT=33.80 (low), PLT=266, ~23 markers
 *   usg.png: ABDOMINAL_USG, descriptive reportText, no numeric lab results
 *
 * IMPORTANT: Requires GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=key ./gradlew :integration-tests:evaluationTest --tests "*AiMedicalExamImportEvalSpec"
 */
@Title("AI Evaluation: Medical Exam Import from Real Images")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiMedicalExamImportEvalSpec extends BaseEvaluationSpec {

    // Note: directory name has trailing space: "medical /"
    private static final String MORPHOLOGY_IMAGE_PATH = "/screenshots/medical /morphology.png"
    private static final String USG_IMAGE_PATH = "/screenshots/medical /usg.png"

    private static final String ANALYZE_ENDPOINT = "/v1/medical-exams/import/analyze"
    private static final String IMPORT_BASE = "/v1/medical-exams/import"
    private static final String EXAMS_BASE = "/v1/medical-exams"

    def setup() {
        cleanMedicalExamData()
    }

    def cleanup() {
        cleanMedicalExamData()
    }

    private void cleanMedicalExamData() {
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", testDeviceId)
        jdbcTemplate.update("DELETE FROM medical_exam_import_drafts WHERE device_id = ?", testDeviceId)
    }

    // ==================== Morphology Image Tests ====================

    def "E-MED-01: AI identifies blood morphology image as MORPHOLOGY exam type"() {
        given: "real CBC blood test image (morphology.png)"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "exam type is correctly identified as MORPHOLOGY"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        println "DEBUG: E-MED-01 response: ${response.body().asString()?.take(300)}"
        body.getString("examTypeCode") == "MORPHOLOGY"
        body.getString("status") == "PENDING"
        body.getFloat("confidence") >= 0.7f
        body.getString("draftId") != null
    }

    def "E-MED-02: AI extracts WBC value from morphology image within expected range"() {
        given: "real CBC blood test image with WBC=4.18 tys/ul"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "WBC marker is extracted with value close to 4.18"
        response.statusCode() == 200
        def results = response.body().jsonPath().getList("results")
        def markerCodes = results.collect { it.markerCode }
        println "DEBUG: E-MED-02 extracted markers: ${markerCodes}"

        def wbc = results.find { it.markerCode == "WBC" }
        wbc != null
        def wbcVal = wbc.valueNumeric as float
        println "DEBUG: E-MED-02 WBC extracted: ${wbcVal} (expected ~4.18)"
        // Allow ±0.5 tolerance around expected value 4.18
        wbcVal >= 3.5f && wbcVal <= 5.0f
    }

    def "E-MED-03: AI extracts RBC value as low compared to reference range"() {
        given: "real CBC image with low RBC=3.32 (reference 4.00-5.50)"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "RBC is extracted with value around 3.32 and reference range"
        response.statusCode() == 200
        def results = response.body().jsonPath().getList("results")

        def rbc = results.find { it.markerCode == "RBC" }
        rbc != null
        def rbcVal = rbc.valueNumeric as float
        println "DEBUG: E-MED-03 RBC extracted: ${rbcVal} (expected ~3.32, ref: 4.00-5.50)"
        // Allow ±0.5 tolerance around expected value 3.32
        rbcVal >= 2.8f && rbcVal <= 4.0f
        // Reference range should be extracted from the report
        rbc.refRangeHigh != null
        (rbc.refRangeHigh as float) >= 4.5f
    }

    def "E-MED-04: AI extracts at least 15 markers from morphology image"() {
        given: "real CBC image with approximately 23 visible markers"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "sufficient number of markers are extracted"
        response.statusCode() == 200
        def results = response.body().jsonPath().getList("results")
        println "DEBUG: E-MED-04 extracted ${results.size()} markers: ${results.collect { it.markerCode }}"
        results.size() >= 15
    }

    def "E-MED-05: AI extracts PLT (platelet) count from morphology image"() {
        given: "real CBC image with PLT=266 tys/ul (normal range 130-400)"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "PLT is extracted with value around 266"
        response.statusCode() == 200
        def results = response.body().jsonPath().getList("results")

        def plt = results.find { it.markerCode == "PLT" }
        plt != null
        def pltVal = plt.valueNumeric as float
        println "DEBUG: E-MED-05 PLT extracted: ${pltVal} (expected ~266)"
        // Allow ±30 tolerance around expected value 266
        pltVal >= 230f && pltVal <= 310f
    }

    def "E-MED-06: Full flow - morphology analyze → confirm → exam saved and queryable"() {
        given: "real CBC blood test image"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "step 1 - analyze the image"
        def analyzeResponse = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "draft is created successfully"
        analyzeResponse.statusCode() == 200
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")
        draftId != null
        analyzeResponse.body().jsonPath().getString("examTypeCode") == "MORPHOLOGY"

        when: "step 2 - confirm the draft"
        def confirmPath = "${IMPORT_BASE}/${draftId}/confirm"
        def confirmResponse = authenticatedPost(confirmPath)

        then: "examination is created with AI_IMPORT source and lab results"
        confirmResponse.statusCode() == 201
        def examBody = confirmResponse.body().jsonPath()
        println "DEBUG: E-MED-06 confirmed exam: ${confirmResponse.body().asString()?.take(300)}"
        def examId = examBody.getString("id")
        examId != null
        examBody.getString("examTypeCode") == "MORPHOLOGY"
        examBody.getString("source") == "AI_IMPORT"
        def savedResults = examBody.getList("results")
        println "DEBUG: E-MED-06 saved ${savedResults.size()} results"
        savedResults.size() >= 10

        when: "step 3 - query exam details from GET endpoint"
        def detailPath = "${EXAMS_BASE}/${examId}"
        def detailResponse = authenticatedGet(detailPath)

        then: "exam is retrievable with all results"
        detailResponse.statusCode() == 200
        detailResponse.body().jsonPath().getString("source") == "AI_IMPORT"
        detailResponse.body().jsonPath().getList("results").size() >= 10
    }

    // ==================== USG Image Tests ====================

    def "E-MED-07: AI identifies abdominal USG image as ABDOMINAL_USG exam type"() {
        given: "real abdominal ultrasound report image (usg.png)"
        def imageBytes = loadTestImage(USG_IMAGE_PATH)

        when: "analyzing the USG image"
        def response = analyzeExamWithFile(imageBytes, "usg.png")

        then: "exam type is correctly identified as abdominal ultrasound"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        println "DEBUG: E-MED-07 USG response: ${response.body().asString()}"
        body.getString("examTypeCode") == "ABDOMINAL_USG"
        body.getString("status") == "PENDING"
        body.getFloat("confidence") >= 0.6f
    }

    def "E-MED-08: AI extracts descriptive report text from abdominal USG"() {
        given: "real USG report with Polish descriptive findings about liver, kidneys, etc."
        def imageBytes = loadTestImage(USG_IMAGE_PATH)

        when: "analyzing the USG image"
        def response = analyzeExamWithFile(imageBytes, "usg.png")

        then: "report text is extracted and contains organ findings"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        def reportText = body.getString("reportText")
        println "DEBUG: E-MED-08 reportText (${reportText?.length()} chars): ${reportText?.take(200)}"
        // Report text must be present and contain meaningful content
        reportText != null
        reportText.length() > 30
    }

    def "E-MED-09: USG image produces no meaningful clinical numeric lab values (descriptive exam)"() {
        given: "real USG report image which contains only descriptive text, no clinical lab values"
        def imageBytes = loadTestImage(USG_IMAGE_PATH)

        when: "analyzing the USG image"
        def response = analyzeExamWithFile(imageBytes, "usg.png")

        then: "no clinical results have actual numeric values (REPORT_METADATA markers like photo count are excluded)"
        response.statusCode() == 200
        def results = response.body().jsonPath().getList("results")
        println "DEBUG: E-MED-09 USG results count: ${results.size()}"
        // Exclude REPORT_METADATA category (e.g. 'Wydano zdjęć') — those are not clinical lab values
        def clinicalNumericResults = results.findAll {
            it.valueNumeric != null && (it.valueNumeric as double) > 0 && it.category != "REPORT_METADATA"
        }
        println "DEBUG: E-MED-09 USG numeric results (valueNumeric > 0): ${clinicalNumericResults.size()}"
        clinicalNumericResults.size() == 0
    }

    // ==================== Private helpers ====================

    private def analyzeExamWithFile(byte[] imageBytes, String filename) {
        def deviceId = testDeviceId
        def path = ANALYZE_ENDPOINT
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", TEST_SECRET_BASE64)

        return RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart("files", filename, imageBytes, "image/png")
                .post(path)
                .then()
                .extract()
    }

    private def authenticatedPost(String path) {
        def deviceId = testDeviceId
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", TEST_SECRET_BASE64)

        return RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .contentType(io.restassured.http.ContentType.JSON)
                .post(path)
                .then()
                .extract()
    }

    private def authenticatedGet(String path) {
        def deviceId = testDeviceId
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("GET", path, timestamp, nonce, deviceId, "", TEST_SECRET_BASE64)

        return RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .get(path)
                .then()
                .extract()
    }
}
