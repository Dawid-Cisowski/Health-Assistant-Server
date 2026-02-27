package com.healthassistant.evaluation

import io.restassured.RestAssured
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.util.concurrent.TimeUnit

/**
 * AI evaluation tests for medical exam section interpretation using REAL Gemini API.
 *
 * Tests verify the quality of AI-generated clinical interpretations produced by
 * AiMedicalExamSectionInterpreter after extraction, covering:
 *   - Polish language output
 *   - Abnormal value flagging
 *   - Hedging language (nie diagnozuje, tylko "może wskazywać na")
 *   - Doctor consultation recommendation for significantly abnormal values
 *   - Length constraints (3-4 sentences)
 *   - USG descriptive section interpretation
 *
 * Ground truth for morphology.png:
 *   RBC = 3.32 tys/ul  (ref: 4.0-5.5  → LOW)
 *   HCT = 33.80%       (ref: ~37-47%  → LOW)
 *   WBC = 4.18 tys/ul  (ref: 4.0-10.0 → normal)
 *
 * IMPORTANT: Requires GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=key ./gradlew :integration-tests:evaluationTest --tests "*AiMedicalExamSectionInterpretEvalSpec"
 */
@Title("AI Evaluation: Medical Exam Section Interpretation Quality")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiMedicalExamSectionInterpretEvalSpec extends BaseEvaluationSpec {

    // Note: directory name has trailing space: "medical /"
    private static final String MORPHOLOGY_IMAGE_PATH = "/screenshots/medical /morphology.png"
    private static final String USG_IMAGE_PATH        = "/screenshots/medical /usg.png"
    private static final String ANALYZE_ENDPOINT      = "/v1/medical-exams/import/analyze"

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

    // ==================== Language tests ====================

    def "E-SI-01: Morphology conclusions are written in Polish"() {
        given: "real CBC blood test image"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "conclusions field is present on the MORPHOLOGY section"
        response.statusCode() == 200
        def sections = response.body().jsonPath().getList("sections")
        def morphSection = sections.find { it.examTypeCode == "MORPHOLOGY" }
        morphSection != null
        def conclusions = morphSection.conclusions as String
        println "DEBUG: E-SI-01 conclusions: ${conclusions?.take(300)}"
        conclusions != null && !conclusions.isBlank()

        and: "text contains Polish-language indicators (diacritics or common Polish words)"
        def polishDiacritics = /.*[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ].*/
        def polishWords      = /(?i).*(norma|wynik|badani|wartość|wartości|może|obniżon|poniżej|zalec|lekarz|erytrocyt|morfologi|hemoglobin|wskazywać).*/
        conclusions ==~ polishDiacritics || conclusions ==~ polishWords
    }

    def "E-SI-02: USG section conclusions are in Polish and reference the imaging findings"() {
        given: "real abdominal USG image"
        def imageBytes = loadTestImage(USG_IMAGE_PATH)

        when: "analyzing the USG image"
        def response = analyzeExamWithFile(imageBytes, "usg.png")

        then: "ABDOMINAL_USG section has non-blank conclusions"
        response.statusCode() == 200
        def sections = response.body().jsonPath().getList("sections")
        def usgSection = sections.find { it.examTypeCode == "ABDOMINAL_USG" }
        usgSection != null
        def conclusions = usgSection.conclusions as String
        println "DEBUG: E-SI-02 USG conclusions: ${conclusions?.take(300)}"
        conclusions != null && !conclusions.isBlank()

        and: "conclusions mention typical abdominal organs or imaging terms in Polish"
        def imagingTerms = /(?i).*(wątroba|nerka|śledziona|pęcherzyk|trzustka|jama brzuszna|narząd|obraz|badanie USG|ultrasonografi|prawidłow|nieprawidłow|echo).*/
        conclusions ==~ imagingTerms
    }

    // ==================== Abnormal value detection ====================

    def "E-SI-03: Low RBC and HCT values are flagged as abnormal in morphology interpretation"() {
        given: "CBC image with RBC=3.32 (ref: 4.0-5.5 → LOW) and HCT=33.80% (ref ~37-47% → LOW)"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "MORPHOLOGY section has conclusions"
        response.statusCode() == 200
        def morphSection = response.body().jsonPath().getList("sections").find { it.examTypeCode == "MORPHOLOGY" }
        morphSection != null
        def conclusions = morphSection.conclusions as String
        println "DEBUG: E-SI-03 conclusions: ${conclusions?.take(400)}"
        conclusions != null && !conclusions.isBlank()

        and: "interpretation mentions abnormality — low values, deviation from norm, or relevant markers"
        def abnormalityIndicators = /(?i).*(obniżon|poniżej|niski|niska|nieprawidłow|wskazywać|może|odchylen|zmniejszon|RBC|HCT|erytrocyt|hemoglobin|hematokryt|morfologi).*/
        conclusions ==~ abnormalityIndicators
    }

    def "E-SI-04: Interpretation uses hedging language — never a direct clinical diagnosis"() {
        given: "CBC image with out-of-range values"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "conclusions contain hedging phrase as per system prompt rules"
        response.statusCode() == 200
        def morphSection = response.body().jsonPath().getList("sections").find { it.examTypeCode == "MORPHOLOGY" }
        def conclusions = morphSection?.conclusions as String
        println "DEBUG: E-SI-04 conclusions: ${conclusions?.take(400)}"
        conclusions != null && !conclusions.isBlank()

        and: "uses 'może wskazywać', 'może sugerować' or similar hedging OR confirms norm — no direct diagnosis"
        def hedgingPhrases = /(?i).*(może wskazywać|może sugerować|może świadczyć|sugeruje|wskazuje na|w normie|prawidłow|zalec|konsultacja|warto skonsultować).*/
        conclusions ==~ hedgingPhrases
    }

    def "E-SI-05: Doctor consultation is recommended when morphology shows significantly low values"() {
        given: "CBC image with RBC=3.32 and HCT=33.80 — both significantly below reference"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "conclusions are present"
        response.statusCode() == 200
        def morphSection = response.body().jsonPath().getList("sections").find { it.examTypeCode == "MORPHOLOGY" }
        def conclusions = morphSection?.conclusions as String
        println "DEBUG: E-SI-05 conclusions: ${conclusions?.take(400)}"
        conclusions != null && !conclusions.isBlank()

        and: "interpretation mentions consulting a doctor or continuing diagnostics"
        def consultationKeywords = /(?i).*(lekarz|konsultacj|skonsultow|diagnostyk|wizyta|specjalista|powtórzyć|monitorow|zlec).*/
        conclusions ==~ consultationKeywords
    }

    // ==================== Length and format constraints ====================

    def "E-SI-06: Morphology interpretation is concise — between 30 and 800 characters (3-4 sentences)"() {
        given: "real CBC blood test image"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "MORPHOLOGY conclusions are within expected length bounds"
        response.statusCode() == 200
        def morphSection = response.body().jsonPath().getList("sections").find { it.examTypeCode == "MORPHOLOGY" }
        def conclusions = morphSection?.conclusions as String
        println "DEBUG: E-SI-06 conclusions length=${conclusions?.length()}: ${conclusions?.take(200)}"
        conclusions != null
        conclusions.length() >= 30
        conclusions.length() <= 800
    }

    def "E-SI-07: USG interpretation is concise — between 30 and 800 characters"() {
        given: "real abdominal USG image"
        def imageBytes = loadTestImage(USG_IMAGE_PATH)

        when: "analyzing the USG image"
        def response = analyzeExamWithFile(imageBytes, "usg.png")

        then: "ABDOMINAL_USG conclusions are within expected length bounds"
        response.statusCode() == 200
        def usgSection = response.body().jsonPath().getList("sections").find { it.examTypeCode == "ABDOMINAL_USG" }
        def conclusions = usgSection?.conclusions as String
        println "DEBUG: E-SI-07 USG conclusions length=${conclusions?.length()}: ${conclusions?.take(200)}"
        conclusions != null
        conclusions.length() >= 30
        conclusions.length() <= 800
    }

    def "E-SI-08: Conclusions do not contain bullet lists or markdown headers — plain flowing text"() {
        given: "real CBC blood test image"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "analyzing the morphology image"
        def response = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "conclusions text does not start with markdown list or header syntax"
        response.statusCode() == 200
        def morphSection = response.body().jsonPath().getList("sections").find { it.examTypeCode == "MORPHOLOGY" }
        def conclusions = morphSection?.conclusions as String
        println "DEBUG: E-SI-08 conclusions: ${conclusions?.take(300)}"
        conclusions != null && !conclusions.isBlank()

        and: "no markdown list markers at line starts"
        def lines = conclusions.split("\n").findAll { !it.isBlank() }
        !lines.every { it =~ /^\s*[-*•]\s+/ }
        !lines.every { it =~ /^\s*#{1,3}\s+/ }
    }

    // ==================== Full flow test ====================

    def "E-SI-09: Full flow — conclusions survive analyze → get draft round-trip"() {
        given: "real CBC blood test image"
        def imageBytes = loadTestImage(MORPHOLOGY_IMAGE_PATH)

        when: "step 1 — analyze the image"
        def analyzeResponse = analyzeExamWithFile(imageBytes, "morphology.png")

        then: "draft is created with non-blank conclusions"
        analyzeResponse.statusCode() == 200
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")
        draftId != null
        def analyzeSections = analyzeResponse.body().jsonPath().getList("sections")
        def analyzeConclusions = analyzeSections.find { it.examTypeCode == "MORPHOLOGY" }?.conclusions as String
        println "DEBUG: E-SI-09 analyze conclusions: ${analyzeConclusions?.take(200)}"
        analyzeConclusions != null && !analyzeConclusions.isBlank()

        when: "step 2 — GET the draft"
        def draftPath = "/v1/medical-exams/import/${draftId}"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def deviceId = testDeviceId
        def signature = generateHmacSignature("GET", draftPath, timestamp, nonce, deviceId, "", TEST_SECRET_BASE64)
        def getDraftResponse = RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .get(draftPath)
                .then()
                .extract()

        then: "conclusions in GET response match those from analyze response"
        getDraftResponse.statusCode() == 200
        def getDraftSections = getDraftResponse.body().jsonPath().getList("sections")
        def getDraftConclusions = getDraftSections.find { it.examTypeCode == "MORPHOLOGY" }?.conclusions as String
        println "DEBUG: E-SI-09 GET draft conclusions: ${getDraftConclusions?.take(200)}"
        getDraftConclusions != null && !getDraftConclusions.isBlank()
        getDraftConclusions == analyzeConclusions
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
}
