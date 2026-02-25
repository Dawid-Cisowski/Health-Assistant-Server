package com.healthassistant

import io.restassured.http.ContentType
import spock.lang.Narrative
import spock.lang.Title

import java.time.LocalDate

@Title("Medical Exam Bidirectional Linking")
@Narrative("Tests bidirectional linking between related medical examinations")
class MedicalExamLinksSpec extends BaseIntegrationSpec {

    static final String DEVICE_ID = "test-medical-links"
    static final String SECRET = "dGVzdC1zZWNyZXQtMTIz"
    static final String EXAMS_BASE = "/v1/medical-exams"
    static final String IMPORT_BASE = "/v1/medical-exams/import"
    static final String ANALYZE_ENDPOINT = "/v1/medical-exams/import/analyze"

    static final String TODAY = LocalDate.now().toString()

    static final String MORPHOLOGY_AI_RESPONSE = '''
    {
      "isMedicalReport": true,
      "confidence": 0.95,
      "examTypeCode": "MORPHOLOGY",
      "title": "Morfologia krwi - AI",
      "performedAt": "2025-03-15T09:00:00Z",
      "laboratory": "Diagnostyka",
      "orderingDoctor": null,
      "reportText": null,
      "conclusions": null,
      "validationError": null,
      "results": []
    }
    '''

    def setup() {
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DEVICE_ID)
        TestChatModelConfiguration.resetResponse()
    }

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    // ---- helpers ----

    private String createExam(String examTypeCode, String title) {
        def request = """{"examTypeCode": "${examTypeCode}", "title": "${title}", "date": "${TODAY}"}"""
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, EXAMS_BASE, request)
                .post(EXAMS_BASE)
        assert response.statusCode() == 201
        return response.jsonPath().getString("id")
    }

    private String linkPath(String examId) {
        return "${EXAMS_BASE}/${examId}/links"
    }

    private String unlinkPath(String examId, String linkedExamId) {
        return "${EXAMS_BASE}/${examId}/links/${linkedExamId}"
    }

    // ===================== Linking =====================

    def "should create bidirectional link between two examinations"() {
        given: "two distinct examinations"
        def colonoscopyId = createExam("COLONOSCOPY", "Kolonoskopia")
        def histopathologyId = createExam("HISTOPATHOLOGY", "Badanie histopatologiczne")

        when: "linking colonoscopy to histopathology"
        def linkBody = """{"linkedExaminationId": "${histopathologyId}"}"""
        def linkResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(colonoscopyId), linkBody)
                .post(linkPath(colonoscopyId))

        then: "link is created with 201"
        linkResponse.statusCode() == 201

        and: "colonoscopy response shows histopathology in linkedExaminations"
        def linked = linkResponse.jsonPath().getList("linkedExaminations")
        linked.size() == 1
        linked[0].id == histopathologyId

        and: "histopathology also shows colonoscopy (bidirectional)"
        def histoPath = "${EXAMS_BASE}/${histopathologyId}"
        def histoResponse = authenticatedGetRequest(DEVICE_ID, SECRET, histoPath)
                .get(histoPath)
        histoResponse.statusCode() == 200
        def histoLinked = histoResponse.jsonPath().getList("linkedExaminations")
        histoLinked.size() == 1
        histoLinked[0].id == colonoscopyId
    }

    def "should return 409 when creating duplicate link"() {
        given: "two examinations already linked"
        def examAId = createExam("MORPHOLOGY", "Morfologia A")
        def examBId = createExam("MORPHOLOGY", "Morfologia B")
        def linkBody = """{"linkedExaminationId": "${examBId}"}"""
        def firstLink = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(examAId), linkBody)
                .post(linkPath(examAId))
        firstLink.statusCode() == 201

        when: "trying to link again (A→B)"
        def duplicateResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(examAId), linkBody)
                .post(linkPath(examAId))

        then: "returns 409 Conflict"
        duplicateResponse.statusCode() == 409
    }

    def "should return 409 when creating reversed duplicate link (B→A when A→B exists)"() {
        given: "examinations already linked A→B"
        def examAId = createExam("MORPHOLOGY", "Morfologia A")
        def examBId = createExam("MORPHOLOGY", "Morfologia B")
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(examAId),
                """{"linkedExaminationId": "${examBId}"}""")
                .post(linkPath(examAId)).statusCode() == 201

        when: "trying to link B→A"
        def reversedBody = """{"linkedExaminationId": "${examAId}"}"""
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(examBId), reversedBody)
                .post(linkPath(examBId))

        then: "returns 409 Conflict (same link)"
        response.statusCode() == 409
    }

    def "should return 400 when trying to link examination to itself"() {
        given: "an examination"
        def examId = createExam("MORPHOLOGY", "Morfologia")

        when: "trying to link to itself"
        def selfLinkBody = """{"linkedExaminationId": "${examId}"}"""
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(examId), selfLinkBody)
                .post(linkPath(examId))

        then: "returns 400 Bad Request"
        response.statusCode() == 400
    }

    def "should return 404 when linking to examination owned by different device"() {
        given: "an examination owned by another device"
        def myExamId = createExam("MORPHOLOGY", "Moja morfologia")
        def otherDeviceExamBody = """{"examTypeCode": "MORPHOLOGY", "title": "Obce badanie", "date": "${TODAY}"}"""
        def otherExamResponse = authenticatedPostRequestWithBody("test-medical-exams", SECRET, EXAMS_BASE, otherDeviceExamBody)
                .post(EXAMS_BASE)
        def otherExamId = otherExamResponse.jsonPath().getString("id")

        when: "trying to link to other device's examination"
        def linkBody = """{"linkedExaminationId": "${otherExamId}"}"""
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(myExamId), linkBody)
                .post(linkPath(myExamId))

        then: "returns 404 Not Found"
        response.statusCode() == 404
    }

    def "should return 404 when linking to non-existent examination"() {
        given: "an examination and a random non-existent UUID"
        def examId = createExam("MORPHOLOGY", "Morfologia")
        def nonExistentId = UUID.randomUUID().toString()

        when: "trying to link to non-existent examination"
        def linkBody = """{"linkedExaminationId": "${nonExistentId}"}"""
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(examId), linkBody)
                .post(linkPath(examId))

        then: "returns 404 Not Found"
        response.statusCode() == 404
    }

    // ===================== Unlinking =====================

    def "should remove link between examinations"() {
        given: "two linked examinations"
        def examAId = createExam("MORPHOLOGY", "Morfologia A")
        def examBId = createExam("MORPHOLOGY", "Morfologia B")
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(examAId),
                """{"linkedExaminationId": "${examBId}"}""")
                .post(linkPath(examAId))

        when: "removing the link"
        def deletePath = unlinkPath(examAId, examBId)
        def deleteResponse = authenticatedDeleteRequest(DEVICE_ID, SECRET, deletePath)
                .delete(deletePath)

        then: "returns 204 No Content"
        deleteResponse.statusCode() == 204

        and: "examination A no longer shows linked examinations"
        def examAPath = "${EXAMS_BASE}/${examAId}"
        def examAResponse = authenticatedGetRequest(DEVICE_ID, SECRET, examAPath)
                .get(examAPath)
        examAResponse.jsonPath().getList("linkedExaminations").size() == 0

        and: "examination B no longer shows linked examinations"
        def examBPath = "${EXAMS_BASE}/${examBId}"
        def examBResponse = authenticatedGetRequest(DEVICE_ID, SECRET, examBPath)
                .get(examBPath)
        examBResponse.jsonPath().getList("linkedExaminations").size() == 0
    }

    def "should return 404 when removing non-existent link"() {
        given: "two unlinked examinations"
        def examAId = createExam("MORPHOLOGY", "Morfologia A")
        def examBId = createExam("MORPHOLOGY", "Morfologia B")

        when: "trying to remove a link that doesn't exist"
        def deletePath = unlinkPath(examAId, examBId)
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET, deletePath)
                .delete(deletePath)

        then: "returns 404 Not Found"
        response.statusCode() == 404
    }

    // ===================== Multiple links =====================

    def "should support one examination linked to multiple others"() {
        given: "one main examination and two related ones"
        def mainId = createExam("COLONOSCOPY", "Kolonoskopia")
        def histo1Id = createExam("HISTOPATHOLOGY", "Histopatologia - wycinek 1")
        def histo2Id = createExam("HISTOPATHOLOGY", "Histopatologia - wycinek 2")

        when: "linking main to both histopathologies"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(mainId),
                """{"linkedExaminationId": "${histo1Id}"}""")
                .post(linkPath(mainId)).statusCode() == 201
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(mainId),
                """{"linkedExaminationId": "${histo2Id}"}""")
                .post(linkPath(mainId)).statusCode() == 201

        def mainPath = "${EXAMS_BASE}/${mainId}"
        def mainResponse = authenticatedGetRequest(DEVICE_ID, SECRET, mainPath)
                .get(mainPath)

        then: "main examination shows 2 linked examinations"
        mainResponse.statusCode() == 200
        mainResponse.jsonPath().getList("linkedExaminations").size() == 2
    }

    // ===================== Cascade delete =====================

    def "should remove links when an examination is deleted"() {
        given: "two linked examinations"
        def examAId = createExam("MORPHOLOGY", "Morfologia A")
        def examBId = createExam("MORPHOLOGY", "Morfologia B")
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET, linkPath(examAId),
                """{"linkedExaminationId": "${examBId}"}""")
                .post(linkPath(examAId))

        when: "deleting examination A"
        def deletePath = "${EXAMS_BASE}/${examAId}"
        def deleteResponse = authenticatedDeleteRequest(DEVICE_ID, SECRET, deletePath)
                .delete(deletePath)

        then: "delete returns 204"
        deleteResponse.statusCode() == 204

        and: "examination B no longer shows linked examinations"
        def examBPath = "${EXAMS_BASE}/${examBId}"
        def examBResponse = authenticatedGetRequest(DEVICE_ID, SECRET, examBPath)
                .get(examBPath)
        examBResponse.jsonPath().getList("linkedExaminations").size() == 0
    }

    // ===================== Import + Link integration =====================

    def "should auto-link new examination when confirming draft with relatedExaminationId"() {
        given: "an existing examination to link to"
        def existingExamId = createExam("COLONOSCOPY", "Kolonoskopia")

        and: "AI returns morphology response for the import"
        TestChatModelConfiguration.setResponse(MORPHOLOGY_AI_RESPONSE)

        and: "a pending import draft"
        def analyzeResponse = authenticatedPostRequest(DEVICE_ID, SECRET, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("description", "Morfologia po kolonoskopii")
                .post(ANALYZE_ENDPOINT)
        analyzeResponse.statusCode() == 200
        def draftId = analyzeResponse.jsonPath().getString("draftId")

        when: "confirming draft with relatedExaminationId"
        def confirmPath = "${IMPORT_BASE}/${draftId}/confirm"
        def confirmBody = """{"relatedExaminationId": "${existingExamId}"}"""
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, confirmPath, confirmBody)
                .post(confirmPath)

        then: "examination is created with 201"
        response.statusCode() == 201
        def newExamId = response.jsonPath().getString("id")
        newExamId != null

        and: "new examination shows the existing exam in linkedExaminations"
        def linked = response.jsonPath().getList("linkedExaminations")
        linked.size() == 1
        linked[0].id == existingExamId

        and: "existing examination shows new exam in linkedExaminations (bidirectional)"
        def existingPath = "${EXAMS_BASE}/${existingExamId}"
        def existingResponse = authenticatedGetRequest(DEVICE_ID, SECRET, existingPath)
                .get(existingPath)
        existingResponse.statusCode() == 200
        def existingLinked = existingResponse.jsonPath().getList("linkedExaminations")
        existingLinked.size() == 1
        existingLinked[0].id == newExamId
    }
}
