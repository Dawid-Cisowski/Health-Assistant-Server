package com.healthassistant

import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import spock.lang.Title

/**
 * Integration tests for Meal Import from Description/Images (AI extraction) endpoint
 */
@Title("Feature: Meal Import from Description and/or Images via AI")
class MealImportSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-meal-import"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String IMPORT_ENDPOINT = "/v1/meals/import"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
    }

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    def "Scenario 1: Valid meal description is extracted and stored successfully"() {
        given: "a meal description and AI mock returning valid meal data"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit the meal description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Sałatka Cezar z kurczakiem na lunch")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getString("mealId") != null
        body.getString("eventId") != null
        body.getString("title") == "Sałatka Cezar z kurczakiem"
        body.getString("mealType") == "LUNCH"
        body.getInt("caloriesKcal") == 450
        body.getInt("proteinGrams") == 35
        body.getInt("fatGrams") == 22
        body.getInt("carbohydratesGrams") == 28
        body.getString("healthRating") == "HEALTHY"
        body.getDouble("confidence") == 0.85
    }

    def "Scenario 2: Valid meal image is extracted and stored successfully"() {
        given: "a meal image and AI mock returning valid meal data"
        def imageBytes = createTestImageBytes()
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit the meal image"
        def response = authenticatedMultipartRequestWithImages(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, [createImageSpec("meal.jpg", imageBytes, "image/jpeg")])
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getString("mealId") != null
    }

    def "Scenario 3: Both description and images can be submitted together"() {
        given: "a meal description with images and AI mock"
        def imageBytes = createTestImageBytes()
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit both description and images"
        def response = authenticatedMultipartRequestWithDescriptionAndImages(
                DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT,
                "Zdrowy lunch - sałatka z kurczakiem",
                [createImageSpec("meal1.jpg", imageBytes, "image/jpeg")]
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
    }

    def "Scenario 4: Multiple images can be submitted"() {
        given: "multiple meal images and AI mock"
        def imageBytes = createTestImageBytes()
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit multiple images"
        def response = authenticatedMultipartRequestWithImages(
                DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT,
                [
                        createImageSpec("meal1.jpg", imageBytes, "image/jpeg"),
                        createImageSpec("meal2.jpg", imageBytes, "image/jpeg"),
                        createImageSpec("meal3.png", imageBytes, "image/png")
                ]
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status"
        response.body().jsonPath().getString("status") == "success"
    }

    def "Scenario 5: Meal event is stored in database after import"() {
        given: "a meal description and AI mock"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit the meal description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Pizza Margherita na kolację")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"

        and: "event is stored in database"
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1
        events.first().eventType() == "MealRecorded.v1"
    }

    def "Scenario 6: Each meal import creates a new event (no idempotency)"() {
        given: "a meal description and AI mock"
        setupGeminiMealMock(createValidMealExtractionResponse())

        and: "I submit the description first time"
        def firstResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Jajecznica na śniadanie")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        def firstMealId = firstResponse.body().jsonPath().getString("mealId")

        when: "I submit the same description again"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def secondResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Jajecznica na śniadanie")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        def secondMealId = secondResponse.body().jsonPath().getString("mealId")

        then: "both responses have different mealIds (no idempotency)"
        secondResponse.statusCode() == 200
        secondMealId != firstMealId

        and: "two events are stored in database"
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 2
    }

    def "Scenario 7: Non-food content returns failure status"() {
        given: "a description that is not food-related"
        setupGeminiMealMock(createNotMealResponse())

        when: "I submit the non-food description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Dzisiejszy trening na siłowni")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200 but with failed status"
        response.statusCode() == 200

        and: "response indicates extraction failure"
        def body = response.body().jsonPath()
        body.getString("status") == "failed"
        body.getString("errorMessage").contains("Not food-related")

        and: "no event is stored in database"
        findEventsForDevice(DEVICE_ID).isEmpty()
    }

    def "Scenario 8: Request with blank description and no images returns 400"() {
        when: "I submit with blank description and no images"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "   ")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400

        and: "response contains error message"
        def body = response.body().jsonPath()
        body.getString("status") == "failed"
        body.getString("errorMessage").contains("Either description or images required")
    }

    def "Scenario 9: Empty image file returns 400 Bad Request"() {
        given: "an empty image file"
        def emptyBytes = new byte[0]

        when: "I submit the empty file"
        def response = authenticatedMultipartRequestWithImages(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, [createImageSpec("empty.jpg", emptyBytes, "image/jpeg")])
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400

        and: "response contains error message"
        def body = response.body().jsonPath()
        body.getString("status") == "failed"
        body.getString("errorMessage").contains("empty")
    }

    def "Scenario 10: Invalid content type returns 400 Bad Request"() {
        given: "a file with invalid content type"
        def textBytes = "this is not an image".getBytes()

        when: "I submit the invalid file"
        def response = authenticatedMultipartRequestWithImages(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, [createImageSpec("document.pdf", textBytes, "application/pdf")])
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400

        and: "response contains error about invalid image type"
        def body = response.body().jsonPath()
        body.getString("status") == "failed"
        body.getString("errorMessage").contains("Invalid image type")
    }

    def "Scenario 11: Request without HMAC authentication returns 401"() {
        given: "a valid description but no HMAC headers"

        when: "I submit without authentication"
        def response = RestAssured.given()
                .multiPart("description", "Zdrowy lunch")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 401"
        response.statusCode() == 401
    }

    def "Scenario 12: Meal macros are extracted correctly"() {
        given: "a meal description with detailed macros"
        setupGeminiMealMock(createDetailedMealExtractionResponse())

        when: "I submit the meal description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Duży obiad: kurczak z ryżem i warzywami")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200

        and: "macros are extracted correctly"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("caloriesKcal") == 750
        body.getInt("proteinGrams") == 55
        body.getInt("fatGrams") == 28
        body.getInt("carbohydratesGrams") == 65
        body.getString("healthRating") == "VERY_HEALTHY"
    }

    def "Scenario 13: Meal type is extracted correctly for breakfast"() {
        given: "a breakfast description"
        setupGeminiMealMock(createBreakfastMealResponse())

        when: "I submit the breakfast description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Owsianka z owocami na śniadanie")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response contains correct meal type"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("mealType") == "BREAKFAST"
    }

    def "Scenario 14: Unhealthy meal gets appropriate health rating"() {
        given: "an unhealthy meal description"
        setupGeminiMealMock(createUnhealthyMealResponse())

        when: "I submit the unhealthy meal description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Podwójny cheeseburger z frytkami i colą")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response contains unhealthy rating"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("healthRating") == "UNHEALTHY"
    }

    def "Scenario 15: AI error returns failure status"() {
        given: "AI returns an error"
        setupGeminiMealMockError()

        when: "I submit the meal description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Jakiś posiłek")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response indicates failure"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "failed"
        body.getString("errorMessage") != null
    }

    def "Scenario 16: PNG image is accepted"() {
        given: "a valid PNG image"
        def imageBytes = createTestImageBytes()
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit the PNG image"
        def response = authenticatedMultipartRequestWithImages(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, [createImageSpec("meal.png", imageBytes, "image/png")])
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
    }

    def "Scenario 17: WebP image is accepted"() {
        given: "a valid WebP image"
        def imageBytes = createTestImageBytes()
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit the WebP image"
        def response = authenticatedMultipartRequestWithImages(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, [createImageSpec("meal.webp", imageBytes, "image/webp")])
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
    }

    def "Scenario 18: Snack meal type is extracted correctly"() {
        given: "a snack description"
        setupGeminiMealMock(createSnackMealResponse())

        when: "I submit the snack description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Jabłko z masłem orzechowym")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response contains correct meal type"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("mealType") == "SNACK"
        body.getString("healthRating") == "HEALTHY"
    }

    def "Scenario 19: Drink meal type is extracted correctly"() {
        given: "a drink description"
        setupGeminiMealMock(createDrinkMealResponse())

        when: "I submit the drink description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Latte z mlekiem owsianym")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response contains correct meal type"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("mealType") == "DRINK"
    }

    def "Scenario 20: Confidence score is returned in response"() {
        given: "a meal with low confidence"
        setupGeminiMealMock(createLowConfidenceMealResponse())

        when: "I submit the meal description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Coś niezidentyfikowanego")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response contains confidence score"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getDouble("confidence") == 0.6
    }

    // Helper methods

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

    def authenticatedMultipartRequestWithImages(String deviceId, String secretBase64, String path, List imageSpecs) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", secretBase64)

        def request = RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)

        imageSpecs.each { spec ->
            request.multiPart(spec)
        }

        return request
    }

    def authenticatedMultipartRequestWithDescriptionAndImages(String deviceId, String secretBase64, String path, String description, List imageSpecs) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", secretBase64)

        def request = RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart("description", description)

        imageSpecs.each { spec ->
            request.multiPart(spec)
        }

        return request
    }

    def createImageSpec(String fileName, byte[] content, String mimeType) {
        return new MultiPartSpecBuilder(content)
                .fileName(fileName)
                .controlName("images")
                .mimeType(mimeType)
                .build()
    }

    byte[] createTestImageBytes() {
        // Create a minimal valid JPEG header for testing
        def base64Jpeg = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBEQCEAwEPwAB//9k="
        return Base64.decoder.decode(base64Jpeg)
    }

    void setupGeminiMealMock(String jsonResponse) {
        TestChatModelConfiguration.setResponse(jsonResponse)
    }

    void setupGeminiMealMockError() {
        TestChatModelConfiguration.setResponse("Internal server error")
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
            "validationError": null
        }"""
    }

    String createNotMealResponse() {
        return """{
            "isMeal": false,
            "confidence": 0.9,
            "validationError": "Not food-related content - appears to be about exercise"
        }"""
    }

    String createDetailedMealExtractionResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.92,
            "title": "Kurczak z ryżem i warzywami",
            "mealType": "LUNCH",
            "occurredAt": null,
            "caloriesKcal": 750,
            "proteinGrams": 55,
            "fatGrams": 28,
            "carbohydratesGrams": 65,
            "healthRating": "VERY_HEALTHY",
            "validationError": null
        }"""
    }

    String createBreakfastMealResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.88,
            "title": "Owsianka z owocami",
            "mealType": "BREAKFAST",
            "occurredAt": null,
            "caloriesKcal": 350,
            "proteinGrams": 12,
            "fatGrams": 8,
            "carbohydratesGrams": 55,
            "healthRating": "VERY_HEALTHY",
            "validationError": null
        }"""
    }

    String createUnhealthyMealResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.95,
            "title": "Podwójny cheeseburger z frytkami",
            "mealType": "LUNCH",
            "occurredAt": null,
            "caloriesKcal": 1200,
            "proteinGrams": 45,
            "fatGrams": 65,
            "carbohydratesGrams": 95,
            "healthRating": "UNHEALTHY",
            "validationError": null
        }"""
    }

    String createSnackMealResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.88,
            "title": "Jabłko z masłem orzechowym",
            "mealType": "SNACK",
            "occurredAt": null,
            "caloriesKcal": 250,
            "proteinGrams": 6,
            "fatGrams": 16,
            "carbohydratesGrams": 25,
            "healthRating": "HEALTHY",
            "validationError": null
        }"""
    }

    String createDrinkMealResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.9,
            "title": "Latte z mlekiem owsianym",
            "mealType": "DRINK",
            "occurredAt": null,
            "caloriesKcal": 120,
            "proteinGrams": 3,
            "fatGrams": 5,
            "carbohydratesGrams": 15,
            "healthRating": "NEUTRAL",
            "validationError": null
        }"""
    }

    String createLowConfidenceMealResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.6,
            "title": "Nieznany posiłek",
            "mealType": "SNACK",
            "occurredAt": null,
            "caloriesKcal": 300,
            "proteinGrams": 10,
            "fatGrams": 15,
            "carbohydratesGrams": 30,
            "healthRating": "NEUTRAL",
            "validationError": null
        }"""
    }
}
