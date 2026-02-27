package com.healthassistant

import io.restassured.http.ContentType
import spock.lang.Narrative
import spock.lang.Title

@Title("Medical Exam Import - Primary Attachment Selection")
@Narrative("Tests that the most viewable file (PDF > image > CDA fallback) is marked as primary when confirming an import draft")
class MedicalExamImportPrimaryAttachmentSpec extends BaseIntegrationSpec {

    static final String DEVICE_ID = "test-primary-attachment"
    static final String ANALYZE_ENDPOINT = "/v1/medical-exams/import/analyze"
    static final String IMPORT_BASE = "/v1/medical-exams/import"
    static final String EXAMS_BASE = "/v1/medical-exams"

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

    def "should mark PDF as primary when both CDA and PDF are uploaded"() {
        given: "a valid CDA file and a PDF file"
        def cdaBytes = loadTestCda()
        def pdfBytes = "%PDF-1.4 minimal pdf content".bytes

        when: "analyzing with CDA + PDF (CDA provides structured data, PDF is the viewable doc)"
        def analyzeResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", cdaBytes, "application/xml")
                .multiPart("files", "wyniki.pdf", pdfBytes, "application/pdf")
                .post(ANALYZE_ENDPOINT)
        def draftId = analyzeResponse.jsonPath().getString("draftId")

        and: "confirming the draft"
        def confirmPath = "${IMPORT_BASE}/${draftId}/confirm"
        def confirmResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, confirmPath)
                .post(confirmPath)
        def examId = confirmResponse.jsonPath().getList(".")[0].id as String

        and: "fetching attachments for the created examination"
        def attachPath = "${EXAMS_BASE}/${examId}/attachments"
        def attachResponse = authenticatedGetRequest(DEVICE_ID, TEST_SECRET_BASE64, attachPath)
                .get(attachPath)

        then: "analyze and confirm succeeded"
        analyzeResponse.statusCode() == 200
        confirmResponse.statusCode() == 201

        and: "the examination has 2 attachments"
        attachResponse.statusCode() == 200
        def attachments = attachResponse.jsonPath().getList(".")
        attachments.size() == 2

        and: "the PDF attachment is marked as primary (preferred over CDA/XML)"
        def primaryAttachment = attachments.find { it.isPrimary == true }
        primaryAttachment != null
        primaryAttachment.contentType == "application/pdf"
        primaryAttachment.filename == "wyniki.pdf"

        and: "the CDA attachment is not primary"
        def cdaAttachment = attachments.find { it.filename == "wyniki.cda" }
        cdaAttachment != null
        cdaAttachment.isPrimary == false
    }

    def "should use CDA as primary fallback when only CDA is uploaded"() {
        given: "only a CDA file is uploaded"
        def cdaBytes = loadTestCda()

        when: "analyzing with CDA only"
        def analyzeResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", cdaBytes, "application/xml")
                .post(ANALYZE_ENDPOINT)
        def draftId = analyzeResponse.jsonPath().getString("draftId")

        and: "confirming the draft"
        def confirmPath = "${IMPORT_BASE}/${draftId}/confirm"
        def confirmResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, confirmPath)
                .post(confirmPath)
        def examId = confirmResponse.jsonPath().getList(".")[0].id as String

        and: "fetching attachments"
        def attachPath = "${EXAMS_BASE}/${examId}/attachments"
        def attachResponse = authenticatedGetRequest(DEVICE_ID, TEST_SECRET_BASE64, attachPath)
                .get(attachPath)

        then: "analyze and confirm succeeded"
        analyzeResponse.statusCode() == 200
        confirmResponse.statusCode() == 201

        and: "the examination has 1 attachment"
        attachResponse.statusCode() == 200
        def attachments = attachResponse.jsonPath().getList(".")
        attachments.size() == 1

        and: "the CDA attachment is marked as primary (fallback — no better option)"
        attachments[0].isPrimary == true
        attachments[0].filename == "wyniki.cda"
    }

    def "should mark PNG image as primary when CDA and PNG are uploaded"() {
        given: "a valid CDA file and a PNG scan image"
        def cdaBytes = loadTestCda()
        def pngBytes = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A] as byte[]

        when: "analyzing with CDA + PNG"
        def analyzeResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", cdaBytes, "application/xml")
                .multiPart("files", "scan.png", pngBytes, "image/png")
                .post(ANALYZE_ENDPOINT)
        def draftId = analyzeResponse.jsonPath().getString("draftId")

        and: "confirming the draft"
        def confirmPath = "${IMPORT_BASE}/${draftId}/confirm"
        def confirmResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, confirmPath)
                .post(confirmPath)
        def examId = confirmResponse.jsonPath().getList(".")[0].id as String

        and: "fetching attachments"
        def attachPath = "${EXAMS_BASE}/${examId}/attachments"
        def attachResponse = authenticatedGetRequest(DEVICE_ID, TEST_SECRET_BASE64, attachPath)
                .get(attachPath)

        then: "analyze and confirm succeeded"
        analyzeResponse.statusCode() == 200
        confirmResponse.statusCode() == 201

        and: "the examination has 2 attachments"
        attachResponse.statusCode() == 200
        def attachments = attachResponse.jsonPath().getList(".")
        attachments.size() == 2

        and: "the PNG attachment is marked as primary (image preferred over CDA)"
        def primaryAttachment = attachments.find { it.isPrimary == true }
        primaryAttachment != null
        primaryAttachment.contentType == "image/png"
        primaryAttachment.filename == "scan.png"

        and: "the CDA attachment is not primary"
        attachments.find { it.filename == "wyniki.cda" }.isPrimary == false
    }

    def "should prefer PDF over image when both are uploaded alongside CDA"() {
        given: "a CDA file, a PNG image, and a PDF — PDF should win"
        def cdaBytes = loadTestCda()
        def pngBytes = [0x89, 0x50, 0x4E, 0x47] as byte[]
        def pdfBytes = "%PDF-1.4 report content".bytes

        when: "analyzing with CDA + PNG + PDF"
        def analyzeResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, ANALYZE_ENDPOINT)
                .contentType(ContentType.MULTIPART)
                .multiPart("files", "wyniki.cda", cdaBytes, "application/xml")
                .multiPart("files", "scan.png", pngBytes, "image/png")
                .multiPart("files", "report.pdf", pdfBytes, "application/pdf")
                .post(ANALYZE_ENDPOINT)
        def draftId = analyzeResponse.jsonPath().getString("draftId")

        and: "confirming the draft"
        def confirmPath = "${IMPORT_BASE}/${draftId}/confirm"
        def confirmResponse = authenticatedPostRequest(DEVICE_ID, TEST_SECRET_BASE64, confirmPath)
                .post(confirmPath)
        def examId = confirmResponse.jsonPath().getList(".")[0].id as String

        and: "fetching attachments"
        def attachPath = "${EXAMS_BASE}/${examId}/attachments"
        def attachResponse = authenticatedGetRequest(DEVICE_ID, TEST_SECRET_BASE64, attachPath)
                .get(attachPath)

        then: "analyze and confirm succeeded"
        analyzeResponse.statusCode() == 200
        confirmResponse.statusCode() == 201

        and: "the examination has 3 attachments"
        attachResponse.statusCode() == 200
        def attachments = attachResponse.jsonPath().getList(".")
        attachments.size() == 3

        and: "the PDF is preferred as primary over both PNG and CDA"
        def primaryAttachment = attachments.find { it.isPrimary == true }
        primaryAttachment != null
        primaryAttachment.contentType == "application/pdf"
        primaryAttachment.filename == "report.pdf"

        and: "only one attachment is marked as primary"
        attachments.count { it.isPrimary == true } == 1
    }
}
