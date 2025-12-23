package com.healthassistant

import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import io.restassured.http.ContentType
import spock.lang.Title

/**
 * Integration tests for Meal Import Draft Flow (analyze -> update -> confirm)
 */
@Title("Feature: Meal Import Draft Flow with User Confirmation")
class MealImportDraftSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String ANALYZE_ENDPOINT = "/v1/meals/import/analyze"
    private static final String CONFIRM_ENDPOINT_TEMPLATE = "/v1/meals/import/%s/confirm"
    private static final String UPDATE_ENDPOINT_TEMPLATE = "/v1/meals/import/%s"

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    // ==================== ANALYZE ENDPOINT TESTS ====================

    def "Scenario 1: Analyze creates draft without saving to health_events"() {
        given: "a meal description and AI mock returning valid meal data"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I analyze the meal description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Sałatka Cezar z kurczakiem na lunch")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200 with draft status"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "draft"
        body.getString("draftId") != null
        body.getString("meal.title") == "Sałatka Cezar z kurczakiem"
        body.getString("meal.mealType") == "LUNCH"
        body.getInt("meal.caloriesKcal") == 450
        body.getDouble("confidence") == 0.85
        body.getString("expiresAt") != null

        and: "no event is stored in database yet"
        findAllEvents().isEmpty()
    }

    def "Scenario 2: Analyze with low confidence generates clarifying questions"() {
        given: "a meal description and AI mock returning low confidence with questions"
        setupGeminiMealMock(createLowConfidenceWithQuestionsResponse())

        when: "I analyze the meal description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Jakieś danie z kurczakiem")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()

        then: "response contains clarifying questions"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "draft"
        body.getDouble("confidence") == 0.65
        def questions = body.getList("questions")
        questions.size() > 0
        questions[0].questionId != null
        questions[0].questionText != null
        questions[0].questionType != null
    }

    def "Scenario 3: Analyze returns failure for non-food content"() {
        given: "a description that is not food-related"
        setupGeminiMealMock(createNotMealResponse())

        when: "I analyze the non-food description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Dzisiejszy trening na siłowni")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200 but with failed status"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "failed"
        body.getString("errorMessage").contains("Not food-related")
    }

    // ==================== UPDATE ENDPOINT TESTS ====================

    def "Scenario 4: Update draft changes meal data"() {
        given: "an existing draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Sałatka")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)

        when: "I update the draft with new calories"
        def updateRequest = [
                meal: [
                        caloriesKcal: 600,
                        title: "Sałatka Cezar z kurczakiem (duża porcja)"
                ]
        ]
        def response = authenticatedJsonRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response contains updated data"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "draft"
        body.getInt("meal.caloriesKcal") == 600
        body.getString("meal.title") == "Sałatka Cezar z kurczakiem (duża porcja)"
    }

    def "Scenario 5: Update draft changes occurredAt timestamp"() {
        given: "an existing draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Śniadanie")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)

        when: "I update the draft with different timestamp"
        def newTimestamp = "2025-01-15T08:00:00Z"
        def updateRequest = [
                occurredAt: newTimestamp
        ]
        def response = authenticatedJsonRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
    }

    def "Scenario 6: Update with wrong deviceId returns 401 (HMAC fails first)"() {
        given: "an existing draft created by test-device"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Obiad")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)

        when: "I try to update with different device (not in HMAC config)"
        def updateRequest = [meal: [caloriesKcal: 500]]
        def response = authenticatedJsonRequest("other-device", SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response status is 401 (HMAC authentication fails before device check)"
        response.statusCode() == 401
    }

    def "Scenario 7: Update non-existent draft returns 404"() {
        given: "a non-existent draft ID"
        def nonExistentDraftId = "00000000-0000-0000-0000-000000000000"
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, nonExistentDraftId)

        when: "I try to update non-existent draft"
        def updateRequest = [meal: [caloriesKcal: 500]]
        def response = authenticatedJsonRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response status is 404"
        response.statusCode() == 404
    }

    // ==================== CONFIRM ENDPOINT TESTS ====================

    def "Scenario 8: Confirm draft saves meal event and returns success"() {
        given: "an existing draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Lunch")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")
        def confirmEndpoint = String.format(CONFIRM_ENDPOINT_TEMPLATE, draftId)

        when: "I confirm the draft"
        def response = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getString("mealId") != null
        body.getString("eventId") != null

        and: "event is now stored in database"
        def events = findAllEvents()
        events.size() == 1
        events.first().eventType() == "MealRecorded.v1"
    }

    def "Scenario 9: Confirm already confirmed draft returns 409"() {
        given: "a confirmed draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Kolacja")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")
        def confirmEndpoint = String.format(CONFIRM_ENDPOINT_TEMPLATE, draftId)

        and: "I confirm it first time"
        authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .statusCode(200)

        when: "I try to confirm again"
        def response = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .extract()

        then: "response status is 409 Conflict"
        response.statusCode() == 409
        response.body().jsonPath().getString("status") == "failed"
        response.body().jsonPath().getString("errorMessage").contains("already confirmed")
    }

    def "Scenario 10: Confirm non-existent draft returns 404"() {
        given: "a non-existent draft ID"
        def nonExistentDraftId = "00000000-0000-0000-0000-000000000000"
        def confirmEndpoint = String.format(CONFIRM_ENDPOINT_TEMPLATE, nonExistentDraftId)

        when: "I try to confirm non-existent draft"
        def response = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .extract()

        then: "response status is 404"
        response.statusCode() == 404
    }

    def "Scenario 11: Confirm with wrong deviceId returns 401 (HMAC fails first)"() {
        given: "an existing draft created by test-device"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def analyzeResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Śniadanie")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")
        def confirmEndpoint = String.format(CONFIRM_ENDPOINT_TEMPLATE, draftId)

        when: "I try to confirm with different device (not in HMAC config)"
        def response = authenticatedPostRequest("other-device", SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .extract()

        then: "response status is 401 (HMAC authentication fails before device check)"
        response.statusCode() == 401
    }

    // ==================== FULL FLOW TESTS ====================

    def "Scenario 12: Full flow - analyze, update, confirm"() {
        given: "a meal description"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I analyze the meal"
        def analyzeResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Obiad z restauracji")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()

        then: "draft is created"
        analyzeResponse.statusCode() == 200
        def draftId = analyzeResponse.body().jsonPath().getString("draftId")
        draftId != null

        when: "I update the calories"
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)
        def updateRequest = [meal: [caloriesKcal: 800]]
        def updateResponse = authenticatedJsonRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "update is successful"
        updateResponse.statusCode() == 200
        updateResponse.body().jsonPath().getInt("meal.caloriesKcal") == 800

        when: "I confirm the draft"
        def confirmEndpoint = String.format(CONFIRM_ENDPOINT_TEMPLATE, draftId)
        def confirmResponse = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .extract()

        then: "meal is saved with updated calories"
        confirmResponse.statusCode() == 200
        confirmResponse.body().jsonPath().getString("status") == "success"
        confirmResponse.body().jsonPath().getInt("caloriesKcal") == 800

        and: "event is stored in database"
        def events = findAllEvents()
        events.size() == 1
    }

    def "Scenario 13: Old import endpoint still works (backwards compatibility)"() {
        given: "a meal description and AI mock"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I use the old import endpoint"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, "/v1/meals/import", "Stary endpoint")
                .post("/v1/meals/import")
                .then()
                .extract()

        then: "response is successful and event is immediately stored"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
        findAllEvents().size() == 1
    }

    // ==================== HELPER METHODS ====================

    def authenticatedMultipartRequestWithDescription(String deviceId, String secretBase64, String path, String description) {
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

    def authenticatedJsonRequest(String deviceId, String secretBase64, String path, Map body) {
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

    def authenticatedPostRequest(String deviceId, String secretBase64, String path) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", secretBase64)

        return RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
    }

    void setupGeminiMealMock(String jsonResponse) {
        TestChatModelConfiguration.setResponse(jsonResponse)
    }

    String createValidMealExtractionResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.85,
            "title": "Sałatka Cezar z kurczakiem",
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

    String createLowConfidenceWithQuestionsResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.65,
            "title": "Danie z kurczakiem",
            "mealType": "LUNCH",
            "occurredAt": null,
            "caloriesKcal": 500,
            "proteinGrams": 40,
            "fatGrams": 20,
            "carbohydratesGrams": 35,
            "healthRating": "NEUTRAL",
            "validationError": null,
            "questions": [
                {
                    "questionId": "q1",
                    "questionText": "Czy porcja była mała, średnia czy duża?",
                    "questionType": "SINGLE_CHOICE",
                    "options": ["SMALL", "MEDIUM", "LARGE"],
                    "affectedFields": ["caloriesKcal", "proteinGrams", "fatGrams", "carbohydratesGrams"]
                },
                {
                    "questionId": "q2",
                    "questionText": "Czy kurczak był smażony czy pieczony?",
                    "questionType": "SINGLE_CHOICE",
                    "options": ["FRIED", "BAKED"],
                    "affectedFields": ["caloriesKcal", "fatGrams", "healthRating"]
                }
            ]
        }"""
    }

    String createNotMealResponse() {
        return """{
            "isMeal": false,
            "confidence": 0.9,
            "validationError": "Not food-related content - appears to be about exercise"
        }"""
    }
}
