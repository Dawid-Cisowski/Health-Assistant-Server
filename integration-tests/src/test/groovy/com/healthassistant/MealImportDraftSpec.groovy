package com.healthassistant

import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import io.restassured.http.ContentType
import spock.lang.Title

@Title("Feature: Meal Import Draft Flow with User Confirmation")
class MealImportDraftSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-meal-draft"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String ANALYZE_ENDPOINT = "/v1/meals/import/analyze"
    private static final String JOB_STATUS_TEMPLATE = "/v1/meals/import/jobs/%s"
    private static final String CONFIRM_ENDPOINT_TEMPLATE = "/v1/meals/import/%s/confirm"
    private static final String UPDATE_ENDPOINT_TEMPLATE = "/v1/meals/import/%s"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        jdbcTemplate.update("DELETE FROM meal_import_jobs WHERE device_id = ?", DEVICE_ID)
    }

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    // ==================== ANALYZE ENDPOINT TESTS ====================

    def "Scenario 1: Analyze creates draft without saving to health_events"() {
        given: "a meal description and AI mock returning valid meal data"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I analyze the meal description"
        def draftId = submitAnalyzeAndGetDraftId("Sałatka Cezar z kurczakiem na lunch")

        then: "draft was created with correct data"
        draftId != null

        and: "no event is stored in database yet"
        findEventsForDevice(DEVICE_ID).isEmpty()
    }

    def "Scenario 2: Analyze with low confidence generates clarifying questions"() {
        given: "a meal description and AI mock returning low confidence with questions"
        setupGeminiMealMock(createLowConfidenceWithQuestionsResponse())

        when: "I analyze the meal description and get the result"
        def result = submitAnalyzeAndGetResult("Jakieś danie z kurczakiem")

        then: "result contains clarifying questions"
        result.getString("result.status") == "draft"
        result.getDouble("result.confidence") == 0.65
        def questions = result.getList("result.questions")
        questions.size() > 0
    }

    def "Scenario 3: Analyze returns failure for non-food content"() {
        given: "a description that is not food-related"
        setupGeminiMealMock(createNotMealResponse())

        when: "I analyze the non-food description"
        def submitResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Dzisiejszy trening na siłowni")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()
        def jobId = submitResponse.body().jsonPath().getString("jobId")
        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, jobId)
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, jobStatusEndpoint)
                .get(jobStatusEndpoint)
                .then()
                .extract()

        then: "job shows FAILED status"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "FAILED"
        body.getString("errorMessage") != null
    }

    // ==================== UPDATE ENDPOINT TESTS ====================

    def "Scenario 4: Update draft changes meal data"() {
        given: "an existing draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def draftId = submitAnalyzeAndGetDraftId("Sałatka")
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
        def draftId = submitAnalyzeAndGetDraftId("Śniadanie")
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
        given: "an existing draft created by ${DEVICE_ID}"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def draftId = submitAnalyzeAndGetDraftId("Obiad")
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
        def draftId = submitAnalyzeAndGetDraftId("Lunch")
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
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1
        events.first().eventType() == "MealRecorded.v1"
    }

    def "Scenario 9: Confirm already confirmed draft returns 409"() {
        given: "a confirmed draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def draftId = submitAnalyzeAndGetDraftId("Kolacja")
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
        given: "an existing draft created by ${DEVICE_ID}"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def draftId = submitAnalyzeAndGetDraftId("Śniadanie")
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
        def draftId = submitAnalyzeAndGetDraftId("Obiad z restauracji")

        then: "draft is created"
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
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1
    }

    def "Scenario 13: Old import endpoint now returns 202 with jobId (async)"() {
        given: "a meal description and AI mock"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I use the import endpoint"
        def submitResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, "/v1/meals/import", "Stary endpoint")
                .post("/v1/meals/import")
                .then()
                .extract()

        then: "response is 202 with jobId"
        submitResponse.statusCode() == 202
        submitResponse.body().jsonPath().getString("jobId") != null

        and: "job completes and event is stored"
        def jobId = submitResponse.body().jsonPath().getString("jobId")
        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, jobId)
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, jobStatusEndpoint)
                .get(jobStatusEndpoint)
                .then()
                .extract()
        response.body().jsonPath().getString("status") == "DONE"
        findEventsForDevice(DEVICE_ID).size() == 1
    }

    // ==================== DESCRIPTION FIELD TESTS ====================

    def "Scenario 14: Analyze returns description with component breakdown"() {
        given: "a meal description and AI mock returning valid meal data with description"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I analyze and get the result"
        def result = submitAnalyzeAndGetResult("Sałatka Cezar z kurczakiem")

        then: "result contains description"
        result.getString("result.description") != null
        result.getString("result.description").contains("Sałatka Cezar")
    }

    // ==================== RE-ANALYSIS TESTS ====================

    def "Scenario 15: Update with answers triggers re-analysis and updates values"() {
        given: "an existing draft with low confidence and questions"
        setupGeminiMealMock(createLowConfidenceWithQuestionsResponse())
        def draftId = submitAnalyzeAndGetDraftId("Danie z kurczakiem")
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)

        and: "AI mock for re-analysis returns updated values"
        setupGeminiMealMock(createReAnalyzedLargePortionResponse())

        when: "I update with answers to questions"
        def updateRequest = [
                answers: [
                        [questionId: "q1", answer: "LARGE"],
                        [questionId: "q2", answer: "BAKED"]
                ]
        ]
        def response = authenticatedJsonRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response contains re-analyzed values"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "draft"
        body.getInt("meal.caloriesKcal") == 720
        body.getString("meal.title").contains("duża porcja")
        body.getString("description").contains("skorygowana analiza")
    }

    def "Scenario 16: Update with userFeedback triggers re-analysis"() {
        given: "an existing draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def draftId = submitAnalyzeAndGetDraftId("Danie z kurczakiem")
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)

        and: "AI mock for re-analysis returns corrected values"
        setupGeminiMealMock(createReAnalyzedWithFeedbackResponse())

        when: "I update with user feedback correcting the meal"
        def updateRequest = [
                userFeedback: "To nie był kurczak tylko tofu, porcja była mała"
        ]
        def response = authenticatedJsonRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response contains re-analyzed values based on feedback"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "draft"
        body.getString("meal.title") == "Tofu z warzywami"
        body.getInt("meal.caloriesKcal") == 260
        body.getString("meal.healthRating") == "VERY_HEALTHY"
    }

    def "Scenario 17: Update with userFeedback can change date/time"() {
        given: "an existing draft"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def draftId = submitAnalyzeAndGetDraftId("Sałatka")
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)

        and: "AI mock for re-analysis returns updated time"
        setupGeminiMealMock(createReAnalyzedWithTimeChangeResponse())

        when: "I update with feedback requesting time change"
        def updateRequest = [
                userFeedback: "To było wczoraj o 19:00, nie dzisiaj"
        ]
        def response = authenticatedJsonRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "response contains updated meal type and time"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("meal.mealType") == "DINNER"
    }

    def "Scenario 18: Manual edits override AI re-analysis"() {
        given: "an existing draft"
        setupGeminiMealMock(createLowConfidenceWithQuestionsResponse())
        def draftId = submitAnalyzeAndGetDraftId("Danie")
        def updateEndpoint = String.format(UPDATE_ENDPOINT_TEMPLATE, draftId)

        and: "AI mock for re-analysis"
        setupGeminiMealMock(createReAnalyzedLargePortionResponse())

        when: "I update with both answers (triggers re-analysis) AND manual meal edits"
        def updateRequest = [
                answers: [[questionId: "q1", answer: "LARGE"]],
                meal: [caloriesKcal: 999, title: "Moja własna nazwa"]
        ]
        def response = authenticatedJsonRequest(DEVICE_ID, SECRET_BASE64, updateEndpoint, updateRequest)
                .patch(updateEndpoint)
                .then()
                .extract()

        then: "manual edits override AI values"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getInt("meal.caloriesKcal") == 999
        body.getString("meal.title") == "Moja własna nazwa"
    }

    // ==================== HELPER METHODS ====================

    private String submitAnalyzeAndGetDraftId(String description) {
        def result = submitAnalyzeAndGetResult(description)
        return result.getString("result.draftId")
    }

    private submitAnalyzeAndGetResult(String description) {
        def submitResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, description)
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()
        def jobId = submitResponse.body().jsonPath().getString("jobId")
        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, jobId)
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, jobStatusEndpoint)
                .get(jobStatusEndpoint)
                .then()
                .extract()
        return response.body().jsonPath()
    }

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

    def authenticatedGetRequest(String deviceId, String secretBase64, String path) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("GET", path, timestamp, nonce, deviceId, "", secretBase64)

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
            "description": "Sałatka Cezar z kurczakiem - rozbicie składników:\\n\\n• Sałata rzymska (~100g): ~15 kcal, ~2g węglowodanów\\n• Pierś z kurczaka grillowana (~150g): ~250 kcal, ~30g białka\\n• Parmezan (~30g): ~120 kcal, ~8g białka, ~10g tłuszczu\\n• Grzanki (~30g): ~50 kcal, ~10g węglowodanów\\n• Sos Cezar (~30ml): ~15 kcal, ~2g tłuszczu",
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
            "description": "Danie z kurczakiem - wstępna analiza:\\n\\n• Kurczak (~200g): ~300-400 kcal (zależy od przygotowania)\\n• Dodatki: szacunkowe ~100-200 kcal",
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

    String createReAnalyzedLargePortionResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.90,
            "title": "Danie z kurczakiem (duża porcja)",
            "description": "Danie z kurczakiem - skorygowana analiza (duża porcja):\\n\\n• Kurczak pieczony (~280g): ~420 kcal, ~56g białka\\n• Ryż (~200g): ~260 kcal, ~55g węglowodanów\\n• Warzywa (~100g): ~40 kcal",
            "mealType": "LUNCH",
            "occurredAt": null,
            "caloriesKcal": 720,
            "proteinGrams": 60,
            "fatGrams": 28,
            "carbohydratesGrams": 55,
            "healthRating": "HEALTHY",
            "validationError": null,
            "questions": []
        }"""
    }

    String createReAnalyzedWithFeedbackResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.92,
            "title": "Tofu z warzywami",
            "description": "Tofu z warzywami - skorygowana analiza:\\n\\n• Tofu (~150g): ~180 kcal, ~15g białka\\n• Warzywa mieszane (~200g): ~70 kcal\\n• Sos sojowy (~15ml): ~10 kcal",
            "mealType": "LUNCH",
            "occurredAt": null,
            "caloriesKcal": 260,
            "proteinGrams": 18,
            "fatGrams": 12,
            "carbohydratesGrams": 20,
            "healthRating": "VERY_HEALTHY",
            "validationError": null,
            "questions": []
        }"""
    }

    String createReAnalyzedWithTimeChangeResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.90,
            "title": "Sałatka Cezar z kurczakiem",
            "description": "Sałatka Cezar - analiza bez zmian, zaktualizowany czas",
            "mealType": "DINNER",
            "occurredAt": "2025-01-14T19:00:00Z",
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
