package com.healthassistant

import io.restassured.RestAssured
import spock.lang.Title

import java.time.LocalDate

@Title("Feature: Medical Exam Attachments")
class MedicalExamAttachmentSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-medical-attach"
    private static final String SECRET = "dGVzdC1zZWNyZXQtMTIz"
    private static final String BASE_PATH = "/v1/medical-exams"

    def setup() {
        jdbcTemplate.update("DELETE FROM examinations WHERE device_id = ?", DEVICE_ID)
    }

    // ==================== POST /{examId}/attachments ====================

    def "should upload PDF attachment and return 201 with attachment data"() {
        given: "an existing examination"
        def examId = createExam("MORPHOLOGY", "Morfologia z PDF")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"

        when: "I upload a PDF file"
        def response = uploadAttachment(DEVICE_ID, attachmentsPath, minimalPdfBytes(), "report.pdf", "application/pdf")

        then: "response status is 201"
        response.statusCode() == 201

        and: "response contains attachment metadata"
        def body = response.body().jsonPath()
        body.getString("id") != null
        body.getString("filename") == "report.pdf"
        body.getString("contentType") == "application/pdf"
        body.getLong("fileSizeBytes") > 0
        body.getString("storageProvider") != null
        body.getString("attachmentType") == "DOCUMENT"
        body.getBoolean("isPrimary") == false
    }

    def "should upload PNG image attachment and return 201"() {
        given: "an existing examination"
        def examId = createExam("ABDOMINAL_USG", "USG z obrazem")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"

        when: "I upload a PNG image"
        def response = uploadAttachment(DEVICE_ID, attachmentsPath, minimalPngBytes(), "scan.png", "image/png")

        then: "response status is 201"
        response.statusCode() == 201

        and: "content type is preserved"
        response.body().jsonPath().getString("contentType") == "image/png"
    }

    def "should upload attachment with isPrimary flag and description"() {
        given: "an existing examination"
        def examId = createExam("CHEST_XRAY", "RTG z primary")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"

        when: "I upload attachment with isPrimary=true and custom description"
        def response = uploadAttachmentWithParams(DEVICE_ID, attachmentsPath,
                minimalPngBytes(), "xray.png", "image/png",
                "DOCUMENT", "Main X-ray image", true)

        then: "response status is 201"
        response.statusCode() == 201

        and: "isPrimary and description are persisted"
        def body = response.body().jsonPath()
        body.getBoolean("isPrimary") == true
        body.getString("description") == "Main X-ray image"
    }

    def "should upload JPEG image attachment"() {
        given: "an existing examination"
        def examId = createExam("MORPHOLOGY", "Morfologia z JPEG")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"

        when: "I upload a JPEG image"
        def response = uploadAttachment(DEVICE_ID, attachmentsPath, [0xFF, 0xD8, 0xFF] as byte[], "photo.jpg", "image/jpeg")

        then: "response status is 201"
        response.statusCode() == 201
        response.body().jsonPath().getString("contentType") == "image/jpeg"
    }

    // ==================== GET /{examId}/attachments ====================

    def "should list all attachments for an examination"() {
        given: "an examination with two attachments"
        def examId = createExam("MORPHOLOGY", "Morfologia z dwoma plikami")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"
        uploadAttachment(DEVICE_ID, attachmentsPath, minimalPdfBytes(), "report1.pdf", "application/pdf")
        uploadAttachment(DEVICE_ID, attachmentsPath, minimalPngBytes(), "image1.png", "image/png")

        when: "I list attachments"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, attachmentsPath)
                .get(attachmentsPath)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "both attachments are returned"
        def attachments = response.body().jsonPath().getList("")
        attachments.size() == 2
        attachments.any { it.filename == "report1.pdf" }
        attachments.any { it.filename == "image1.png" }
    }

    def "should return empty list when examination has no attachments"() {
        given: "an examination without attachments"
        def examId = createExam("MORPHOLOGY", "Morfologia bez plików")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"

        when: "I list attachments"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, attachmentsPath)
                .get(attachmentsPath)
                .then()
                .extract()

        then: "response is 200 with empty list"
        response.statusCode() == 200
        response.body().jsonPath().getList("").size() == 0
    }

    // ==================== DELETE /{examId}/attachments/{attachmentId} ====================

    def "should delete attachment and return 204"() {
        given: "an examination with an attachment"
        def examId = createExam("MORPHOLOGY", "Morfologia do usunięcia pliku")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"
        def uploadResponse = uploadAttachment(DEVICE_ID, attachmentsPath, minimalPdfBytes(), "report.pdf", "application/pdf")
        def attachmentId = uploadResponse.body().jsonPath().getString("id")

        when: "I delete the attachment"
        def deletePath = "${attachmentsPath}/${attachmentId}"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET, deletePath)
                .delete(deletePath)
                .then()
                .extract()

        then: "response status is 204"
        response.statusCode() == 204

        and: "attachment list is empty"
        authenticatedGetRequest(DEVICE_ID, SECRET, attachmentsPath)
                .get(attachmentsPath)
                .then()
                .extract()
                .body().jsonPath().getList("").size() == 0
    }

    def "should return 404 when deleting non-existent attachment"() {
        given: "an existing examination and a random attachment ID"
        def examId = createExam("MORPHOLOGY", "Test missing attachment")
        def fakePath = "${BASE_PATH}/${examId}/attachments/${UUID.randomUUID()}"

        when: "I try to delete the non-existent attachment"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET, fakePath)
                .delete(fakePath)
                .then()
                .extract()

        then: "response status is 404"
        response.statusCode() == 404
    }

    // ==================== Validation tests ====================

    def "should return 400 when file size exceeds 15MB limit"() {
        given: "an existing examination"
        def examId = createExam("MORPHOLOGY", "Test file size limit")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"

        and: "a file exceeding 15MB"
        def oversizedBytes = new byte[15 * 1024 * 1024 + 1]

        when: "I try to upload the oversized file"
        def response = uploadAttachment(DEVICE_ID, attachmentsPath, oversizedBytes, "big.pdf", "application/pdf")

        then: "response status is 400"
        response.statusCode() == 400
    }

    def "should return 400 when content type is not allowed"() {
        given: "an existing examination"
        def examId = createExam("MORPHOLOGY", "Test unsupported content type")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"

        when: "I try to upload a file with unsupported MIME type"
        def response = uploadAttachment(DEVICE_ID, attachmentsPath, "not-a-video".bytes, "video.mp4", "video/mp4")

        then: "response status is 400"
        response.statusCode() == 400
    }

    def "should return 400 when filename contains path traversal sequence"() {
        given: "an existing examination"
        def examId = createExam("MORPHOLOGY", "Test path traversal")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"

        when: "I try to upload a file with path traversal in filename"
        def response = uploadAttachment(DEVICE_ID, attachmentsPath, minimalPdfBytes(), "../evil.pdf", "application/pdf")

        then: "response status is 400"
        response.statusCode() == 400
    }

    // ==================== 404 / isolation tests ====================

    def "should return 404 when uploading to non-existent examination"() {
        given: "a random exam ID"
        def fakeExamId = UUID.randomUUID().toString()
        def attachmentsPath = "${BASE_PATH}/${fakeExamId}/attachments"

        when: "I try to upload to non-existent examination"
        def response = uploadAttachment(DEVICE_ID, attachmentsPath, minimalPdfBytes(), "report.pdf", "application/pdf")

        then: "response status is 404"
        response.statusCode() == 404
    }

    def "should return 404 when accessing attachments of another device's examination"() {
        given: "an examination owned by DEVICE_ID"
        def examId = createExam("MORPHOLOGY", "Other device exam")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"

        when: "another device tries to upload an attachment"
        def response = uploadAttachment(
                "different-device-id", attachmentsPath,
                minimalPdfBytes(), "evil.pdf", "application/pdf",
                "ZGlmZmVyZW50LXNlY3JldC0xMjM=")

        then: "response status is 404 (device isolation)"
        response.statusCode() == 404
    }

    // ==================== Cascade delete ====================

    def "should cascade delete attachments when examination is deleted"() {
        given: "an examination with an attachment"
        def examId = createExam("MORPHOLOGY", "Exam to cascade delete")
        def attachmentsPath = "${BASE_PATH}/${examId}/attachments"
        uploadAttachment(DEVICE_ID, attachmentsPath, minimalPdfBytes(), "report.pdf", "application/pdf")

        when: "I delete the examination"
        authenticatedDeleteRequest(DEVICE_ID, SECRET, "${BASE_PATH}/${examId}")
                .delete("${BASE_PATH}/${examId}")
                .then()
                .statusCode(204)

        then: "attachment rows are deleted from the database"
        def count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM examination_attachments WHERE examination_id = ?::uuid",
                Long.class, examId)
        count == 0
    }

    // ==================== Helper methods ====================

    private String createExam(String examTypeCode, String title) {
        def request = """
        {
            "examTypeCode": "${examTypeCode}",
            "title": "${title}",
            "date": "${LocalDate.now()}"
        }
        """
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET, BASE_PATH, request)
                .post(BASE_PATH)
                .then()
                .statusCode(201)
                .extract()
        return response.body().jsonPath().getString("id")
    }

    private def uploadAttachment(String deviceId, String path, byte[] fileBytes, String filename, String contentType) {
        return uploadAttachment(deviceId, path, fileBytes, filename, contentType, SECRET)
    }

    private def uploadAttachment(String deviceId, String path, byte[] fileBytes, String filename, String contentType, String secret) {
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", secret)

        return RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart("file", filename, fileBytes, contentType)
                .post(path)
                .then()
                .extract()
    }

    private def uploadAttachmentWithParams(String deviceId, String path, byte[] fileBytes, String filename,
                                           String contentType, String attachmentType, String description,
                                           boolean isPrimary) {
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", SECRET)

        return RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart("file", filename, fileBytes, contentType)
                .param("attachmentType", attachmentType)
                .param("description", description)
                .param("isPrimary", isPrimary.toString())
                .post(path)
                .then()
                .extract()
    }

    /** Minimal valid-looking PDF bytes (starts with %PDF magic bytes). */
    private byte[] minimalPdfBytes() {
        return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\nxref\n0 2\n%%EOF".bytes
    }

    /** Minimal 1x1 transparent PNG as byte array. */
    private byte[] minimalPngBytes() {
        return [
            (byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47, (byte)0x0D, (byte)0x0A, (byte)0x1A, (byte)0x0A,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0D, (byte)0x49, (byte)0x48, (byte)0x44, (byte)0x52,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01,
            (byte)0x08, (byte)0x02, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x90, (byte)0x77, (byte)0x53,
            (byte)0xDE, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x0C, (byte)0x49, (byte)0x44, (byte)0x41,
            (byte)0x54, (byte)0x08, (byte)0xD7, (byte)0x63, (byte)0xF8, (byte)0xCF, (byte)0xC0, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x02, (byte)0x00, (byte)0x01, (byte)0xE2, (byte)0x21, (byte)0xBC,
            (byte)0x33, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x49, (byte)0x45, (byte)0x4E,
            (byte)0x44, (byte)0xAE, (byte)0x42, (byte)0x60, (byte)0x82
        ] as byte[]
    }
}
