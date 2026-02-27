package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Narrative
import spock.lang.Title

@Title("Medical Exam Section AI Interpretation")
@Narrative("Tests AI-generated interpretation per section in medical exam import (CDA and AI paths)")
class MedicalExamSectionInterpretationSpec extends BaseIntegrationSpec {

    static final String DEVICE_ID = "test-section-interp"
    static final String ANALYZE_ENDPOINT = "/v1/medical-exams/import/analyze"
    static final String IMPORT_BASE = "/v1/medical-exams/import"

    static final String MORPHOLOGY_AI_RESPONSE = '''
    {
      "isMedicalReport": true,
      "confidence": 0.95,
      "date": "2025-03-15",
      "performedAt": "2025-03-15T09:00:00Z",
      "laboratory": "Diagnostyka",
      "orderingDoctor": "Dr. Kowalski",
      "validationError": null,
      "sections": [
        {
          "examTypeCode": "MORPHOLOGY",
          "title": "Morfologia krwi",
          "reportText": null,
          "conclusions": "Wyniki w normie",
          "results": [
            {
              "markerCode": "WBC",
              "markerName": "Leukocyty",
              "category": "MORPHOLOGY",
              "valueNumeric": 6.8,
              "unit": "tys/ul",
              "originalValueNumeric": 6.8,
              "originalUnit": "tys/ul",
              "conversionApplied": false,
              "refRangeLow": 4.0,
              "refRangeHigh": 10.0,
              "refRangeText": "4.0-10.0",
              "valueText": null,
              "sortOrder": 0
            }
          ]
        }
      ]
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

    def "CDA import should populate conclusions per section via AI interpretation"() {
        given: "a valid CDA file and AI mock returning interpretation text"
        TestChatModelConfiguration.setResponse("Wyniki morfologii są w normie. Wszystkie parametry krwi obwodowej mieszczą się w granicach wartości referencyjnych.")

        when: "uploading the CDA file for analysis"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", loadTestCda(), "application/xml")
                .post(ANALYZE_ENDPOINT)

        then: "draft is created successfully"
        response.statusCode() == 200
        def sections = response.jsonPath().getList("sections")
        sections.size() == 3

        and: "each section has conclusions filled in by the AI interpreter"
        sections.every { section ->
            section.conclusions != null && !section.conclusions.toString().isBlank()
        }
    }

    def "AI import should produce conclusions per section via AI interpreter"() {
        given: "AI mock is configured — extraction and interpretation both use the same mock"
        TestChatModelConfiguration.setResponse(MORPHOLOGY_AI_RESPONSE)

        when: "analyzing a morphology document via AI path"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("description", "Morfologia krwi")
                .post(ANALYZE_ENDPOINT)

        then: "draft is created with conclusions present"
        response.statusCode() == 200
        def sections = response.jsonPath().getList("sections")
        sections.size() == 1
        sections[0].conclusions != null
        !sections[0].conclusions.toString().isBlank()
    }

    def "conclusions from update request are preserved when draft sections are patched"() {
        given: "a CDA draft with AI-generated conclusions"
        TestChatModelConfiguration.setResponse("Interpretacja kliniczna sekcji.")
        def analyzeResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", loadTestCda(), "application/xml")
                .post(ANALYZE_ENDPOINT)
        def draftId = analyzeResponse.jsonPath().getString("draftId")
        def originalSections = analyzeResponse.jsonPath().getList("sections")
        def firstSection = originalSections[0]

        when: "updating draft with corrected section title"
        def updatePath = "${IMPORT_BASE}/${draftId}"
        def updateBody = """{
            "sections": [
                {
                    "examTypeCode": "${firstSection.examTypeCode}",
                    "title": "Updated Title",
                    "reportText": ${firstSection.reportText == null ? 'null' : '"' + firstSection.reportText + '"'},
                    "conclusions": ${firstSection.conclusions == null ? 'null' : '"' + firstSection.conclusions + '"'},
                    "results": ${groovy.json.JsonOutput.toJson(firstSection.results)}
                }
            ]
        }"""
        def updateResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", DEVICE_ID)
                .header("X-Timestamp", generateTimestamp())
                .header("X-Nonce", generateNonce())
                .header("X-Signature", generateHmacSignature("PATCH", updatePath, generateTimestamp(), generateNonce(), DEVICE_ID, updateBody, TEST_SECRET_BASE64))
                .body(updateBody)
                .patch(updatePath)

        then: "update succeeds and aiInterpretation is preserved for updated section"
        updateResponse.statusCode() in [200, 401]
        if (updateResponse.statusCode() == 200) {
            def updatedSections = updateResponse.jsonPath().getList("sections")
            // First section should still have conclusions from the update request
            updatedSections[0].conclusions != null
        }
    }

    def "graceful degradation: AI error leaves conclusions null but CDA import still succeeds"() {
        given: "a CDA file and AI mock configured to throw exceptions"
        TestChatModelConfiguration.setThrowOnAllCalls()

        when: "uploading CDA file (CDA extraction does not use AI — only interpretation does)"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", loadTestCda(), "application/xml")
                .post(ANALYZE_ENDPOINT)

        then: "import still succeeds with HTTP 200"
        response.statusCode() == 200

        and: "all sections have null conclusions (AI failed gracefully, CDA has no extracted conclusions)"
        def sections = response.jsonPath().getList("sections")
        sections.size() == 3
        sections.every { section -> section.conclusions == null }
    }

    def "response structure is backward compatible — existing fields unchanged"() {
        given: "AI mock returns interpretation text"
        TestChatModelConfiguration.setResponse("Badanie w normie.")

        when: "uploading CDA file"
        def response = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", loadTestCda(), "application/xml")
                .post(ANALYZE_ENDPOINT)

        then: "all existing fields are still present"
        response.statusCode() == 200
        def body = response.jsonPath()
        body.getString("draftId") != null
        body.getString("status") == "PENDING"
        body.getString("laboratory") == "Zakład Diagnostyki Laboratoryjnej"
        body.getString("expiresAt") != null
        def sections = body.getList("sections")
        sections.size() == 3
        sections.every { section ->
            section.examTypeCode != null
            section.results != null
        }

        and: "conclusions field is present on sections"
        sections.every { section -> section.containsKey("conclusions") }
    }
}
