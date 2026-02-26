package com.healthassistant

import io.restassured.http.ContentType
import spock.lang.Narrative
import spock.lang.Title

@Title("CDA Medical Exam Import")
@Narrative("Tests deterministic CDA (HL7) file parsing for medical exam import — no AI needed")
class CdaImportSpec extends BaseIntegrationSpec {

    static final String DEVICE_ID = "test-cda-import"
    static final String ANALYZE_ENDPOINT = "/v1/medical-exams/import/analyze"
    static final String IMPORT_BASE = "/v1/medical-exams/import"
    static final String EXAMS_BASE = "/v1/medical-exams"

    static final String NOT_MEDICAL_AI_RESPONSE = '''
    {
      "isMedicalReport": false,
      "confidence": 0.10,
      "validationError": "Document is not a medical report",
      "sections": []
    }
    '''

    def setup() {
        cleanupAllProjectionsForDevice(DEVICE_ID)
        TestChatModelConfiguration.resetResponse()
    }

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    private byte[] loadTestCda() {
        return getClass().getResourceAsStream("/test.cda").bytes
    }

    def "should parse CDA file and create draft with correct sections"() {
        given: "a valid HL7 CDA file with morphology, urine and coagulation sections"
        def cdaBytes = loadTestCda()

        when: "uploading the CDA file for analysis"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", cdaBytes, "application/xml")
                .post(ANALYZE_ENDPOINT)

        then: "a draft is created with all three sections"
        response.statusCode() == 200
        def body = response.jsonPath()
        body.getString("draftId") != null
        body.getString("status") == "PENDING"
        body.getString("laboratory") == "Zakład Diagnostyki Laboratoryjnej"

        and: "the draft contains MORPHOLOGY, URINE and COAGULATION sections"
        def sections = body.getList("sections")
        sections.size() == 3
        sections.collect { it.examTypeCode }.containsAll(["MORPHOLOGY", "URINE", "COAGULATION"])
    }

    def "should extract correct numeric values from CDA"() {
        given: "a valid CDA file"
        def cdaBytes = loadTestCda()

        when: "uploading the CDA file"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", cdaBytes, "application/xml")
                .post(ANALYZE_ENDPOINT)

        then: "the MORPHOLOGY section contains RBC with correct values"
        response.statusCode() == 200
        def sections = response.jsonPath().getList("sections")
        def morphology = sections.find { it.examTypeCode == "MORPHOLOGY" }
        morphology != null

        def rbc = morphology.results.find { it.markerCode == "RBC" }
        rbc != null
        rbc.valueNumeric == 4.80f
        rbc.unit == "mln/\u00b5l"
        rbc.refRangeLow == 4.60f
        rbc.refRangeHigh == 6.50f
    }

    def "should map erytrocyty in URINE context to URINE_RBC not RBC"() {
        given: "a CDA file with an Erytrocyty marker inside a urine exam section"
        def cdaBytes = loadTestCda()

        when: "uploading the CDA file"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", cdaBytes, "application/xml")
                .post(ANALYZE_ENDPOINT)

        then: "the URINE section maps Erytrocyty to URINE_RBC"
        response.statusCode() == 200
        def sections = response.jsonPath().getList("sections")
        def urine = sections.find { it.examTypeCode == "URINE" }
        urine != null

        def urineRbc = urine.results.find { it.markerCode == "URINE_RBC" }
        urineRbc != null
        urineRbc.valueNumeric == 6.30f
    }

    def "should set valueText and valueNumeric to null for non-numeric values"() {
        given: "a CDA file where INR has a non-numeric ST value (comma)"
        def cdaBytes = loadTestCda()

        when: "uploading the CDA file"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", cdaBytes, "application/xml")
                .post(ANALYZE_ENDPOINT)

        then: "COAGULATION section has INR with null valueNumeric and non-null valueText"
        response.statusCode() == 200
        def sections = response.jsonPath().getList("sections")
        def coagulation = sections.find { it.examTypeCode == "COAGULATION" }
        coagulation != null

        def inr = coagulation.results.find { it.markerCode == "INR" }
        inr != null
        inr.valueNumeric == null
        inr.valueText != null
    }

    def "should confirm CDA draft and create examinations with CDA_IMPORT source"() {
        given: "a CDA file analyzed into a draft"
        def cdaBytes = loadTestCda()
        def analyzeResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", cdaBytes, "application/xml")
                .post(ANALYZE_ENDPOINT)
        def draftId = analyzeResponse.jsonPath().getString("draftId")

        when: "confirming the draft"
        def confirmPath = "${IMPORT_BASE}/${draftId}/confirm"
        def confirmResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, confirmPath)
                .post(confirmPath)

        then: "examinations are created with CDA_IMPORT source"
        confirmResponse.statusCode() == 201
        def exams = confirmResponse.jsonPath().getList(".")
        exams.size() == 3
        exams.every { it.source == "CDA_IMPORT" }
        exams.collect { it.examTypeCode }.containsAll(["MORPHOLOGY", "URINE", "COAGULATION"])

        and: "examinations are retrievable via GET"
        def listResponse = authenticatedGetRequest(DEVICE_ID, TEST_SECRET_BASE64, EXAMS_BASE)
                .get(EXAMS_BASE)
        listResponse.statusCode() == 200
    }

    def "should reject invalid XML with 400"() {
        given: "a file containing invalid XML"
        def invalidXml = "NOT VALID XML".bytes

        when: "uploading the invalid file as CDA"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "broken.cda", invalidXml, "application/xml")
                .post(ANALYZE_ENDPOINT)

        then: "returns 400"
        response.statusCode() == 400
    }

    def "should not treat non-CDA files as CDA and use AI flow instead"() {
        given: "a JPEG file with description (not CDA), and AI returns NOT_MEDICAL"
        TestChatModelConfiguration.setResponse(NOT_MEDICAL_AI_RESPONSE)
        def jpegBytes = [0xFF, 0xD8, 0xFF, 0xE0] as byte[]

        when: "uploading a JPEG file"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "photo.jpg", jpegBytes, "image/jpeg")
                .multiPart("description", "Some medical report")
                .post(ANALYZE_ENDPOINT)

        then: "AI flow is used and returns 400 for non-medical content"
        response.statusCode() == 400

        and: "AI was actually called (not the CDA parser)"
        TestChatModelConfiguration.getCallCount() >= 1
    }
}
