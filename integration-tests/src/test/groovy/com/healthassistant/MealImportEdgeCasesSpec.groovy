package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import javax.sql.DataSource
import java.sql.Timestamp
import java.time.Instant

/**
 * Integration tests for meal import edge cases:
 * - Expired drafts returning 410
 * - Device isolation for drafts
 * - Concurrent draft access
 */
@Title("Feature: Meal Import Edge Cases")
class MealImportEdgeCasesSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-mealedge"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String ANALYZE_ENDPOINT = "/v1/meals/import/analyze"
    private static final String CONFIRM_ENDPOINT_TEMPLATE = "/v1/meals/import/%s/confirm"
    private static final String UPDATE_ENDPOINT_TEMPLATE = "/v1/meals/import/%s"

    @Autowired
    DataSource dataSource

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        cleanupDraftsForDevice(DEVICE_ID)
    }

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    // ==================== EXPIRED DRAFT TESTS ====================

    def "Scenario 1: Confirm expired draft returns 410 Gone"() {
        given: "a draft that has been created"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Sałatka")
                .post(ANALYZE_ENDPOINT)
                .then()
                .statusCode(200)
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")

        and: "the draft is expired (manually set expiresAt in the past)"
        expireDraft(draftId)

        when: "I try to confirm the expired draft"
        def confirmEndpoint = String.format(CONFIRM_ENDPOINT_TEMPLATE, draftId)
        def response = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .extract()

        then: "response status is 410 Gone"
        response.statusCode() == 410
    }

    def "Scenario 2: Update expired draft returns 410 Gone"() {
        given: "a draft that has been created"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Obiad")
                .post(ANALYZE_ENDPOINT)
                .then()
                .statusCode(200)
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")

        and: "the draft is expired"
        expireDraft(draftId)

        when: "I try to update the expired draft"
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)
        def updateRequest = [meal: [caloriesKcal: 500]]
        def response = authenticatedPatchRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response status is 410 Gone"
        response.statusCode() == 410
    }

    // ==================== DEVICE ISOLATION TESTS ====================

    def "Scenario 3: Device cannot access another device's draft"() {
        given: "device 1 creates a draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Śniadanie")
                .post(ANALYZE_ENDPOINT)
                .then()
                .statusCode(200)
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")

        when: "device 2 tries to confirm device 1's draft"
        def confirmEndpoint = String.format(CONFIRM_ENDPOINT_TEMPLATE, draftId)
        def response = authenticatedPostRequest("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .extract()

        then: "response status is 404 (draft not found for this device)"
        response.statusCode() == 404
    }

    def "Scenario 4: Device cannot update another device's draft"() {
        given: "device 1 creates a draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Lunch")
                .post(ANALYZE_ENDPOINT)
                .then()
                .statusCode(200)
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")

        when: "device 2 tries to update device 1's draft"
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)
        def updateRequest = [meal: [caloriesKcal: 999]]
        def response = authenticatedPatchRequest("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response status is 404 (draft not found for this device)"
        response.statusCode() == 404
    }

    // ==================== DRAFT STATE TESTS ====================

    def "Scenario 5: Draft expires after 24 hours"() {
        given: "a draft is created"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I analyze a meal"
        def analyzeResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Kolacja")
                .post(ANALYZE_ENDPOINT)
                .then()
                .statusCode(200)
                .extract()

        then: "draft has expiresAt set to approximately 24 hours from now"
        def expiresAt = analyzeResponse.body().jsonPath().getString("expiresAt")
        expiresAt != null
        def expiresInstant = Instant.parse(expiresAt)
        def now = Instant.now()
        // ExpiresAt should be ~24 hours from now (with some tolerance for test execution time)
        def expectedExpiry = now.plusSeconds(24 * 60 * 60)
        Math.abs(expiresInstant.epochSecond - expectedExpiry.epochSecond) < 60
    }

    def "Scenario 6: Confirmed draft cannot be updated"() {
        given: "a draft that has been confirmed"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Deser")
                .post(ANALYZE_ENDPOINT)
                .then()
                .statusCode(200)
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")

        and: "the draft is confirmed"
        def confirmEndpoint = String.format(CONFIRM_ENDPOINT_TEMPLATE, draftId)
        authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .statusCode(200)

        when: "I try to update the confirmed draft"
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)
        def updateRequest = [meal: [caloriesKcal: 999]]
        def response = authenticatedPatchRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response status is 409 Conflict (draft already confirmed)"
        response.statusCode() == 409
    }

    // ==================== HELPER METHODS ====================

    void expireDraft(String draftId) {
        def connection = dataSource.getConnection()
        try {
            def stmt = connection.prepareStatement(
                "UPDATE meal_import_drafts SET expires_at = ? WHERE id = ?::uuid"
            )
            stmt.setTimestamp(1, Timestamp.from(Instant.now().minusSeconds(3600))) // 1 hour ago
            stmt.setString(2, draftId)
            stmt.executeUpdate()
            stmt.close()
        } finally {
            connection.close()
        }
    }

    void cleanupDraftsForDevice(String deviceId) {
        def connection = dataSource.getConnection()
        try {
            def stmt = connection.prepareStatement(
                "DELETE FROM meal_import_drafts WHERE device_id = ?"
            )
            stmt.setString(1, deviceId)
            stmt.executeUpdate()
            stmt.close()
        } finally {
            connection.close()
        }
    }

    def authenticatedMultipartRequest(String deviceId, String secretBase64, String path, String description) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", secretBase64)

        return RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart("description", description)
    }

    def authenticatedPatchRequest(String deviceId, String secretBase64, String path, Map body) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String bodyJson = new groovy.json.JsonBuilder(body).toString()
        String signature = generateHmacSignature("PATCH", path, timestamp, nonce, deviceId, bodyJson, secretBase64)

        return RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .contentType(ContentType.JSON)
                .body(bodyJson)
    }

    void setupGeminiMealMock(String jsonResponse) {
        TestChatModelConfiguration.setResponse(jsonResponse)
    }

    String createValidMealExtractionResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.85,
            "title": "Test Meal",
            "description": "Test meal description with components",
            "mealType": "LUNCH",
            "occurredAt": null,
            "caloriesKcal": 450,
            "proteinGrams": 35,
            "fatGrams": 22,
            "carbohydratesGrams": 28,
            "healthRating": "HEALTHY",
            "validationError": null,
            "questions": []
        }"""
    }
}
