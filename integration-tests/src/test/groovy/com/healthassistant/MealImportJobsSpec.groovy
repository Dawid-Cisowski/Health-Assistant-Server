package com.healthassistant

import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import io.restassured.http.ContentType
import spock.lang.Title

@Title("Feature: Async Meal Import with Job-based Polling")
class MealImportJobsSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-meal-jobs"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String IMPORT_ENDPOINT = "/v1/meals/import"
    private static final String ANALYZE_ENDPOINT = "/v1/meals/import/analyze"
    private static final String JOB_STATUS_TEMPLATE = "/v1/meals/import/jobs/%s"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
        cleanupMealImportJobsForDevice(DEVICE_ID)
    }

    def cleanup() {
        TestChatModelConfiguration.resetResponse()
    }

    def "Scenario 1: POST /import with description returns 202 with jobId"() {
        given: "a meal description and AI mock returning valid meal data"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit the meal description"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Sałatka Cezar z kurczakiem na lunch")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 202 Accepted"
        response.statusCode() == 202

        and: "response contains a jobId"
        def body = response.body().jsonPath()
        body.getString("jobId") != null
        UUID.fromString(body.getString("jobId")) != null
    }

    def "Scenario 2: POST /import with image returns 202 with jobId"() {
        given: "a meal image and AI mock returning valid meal data"
        def imageBytes = createTestImageBytes()
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit the meal image"
        def response = authenticatedMultipartRequestWithImages(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, [createImageSpec("meal.jpg", imageBytes, "image/jpeg")])
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 202 Accepted"
        response.statusCode() == 202

        and: "response contains a jobId"
        response.body().jsonPath().getString("jobId") != null
    }

    def "Scenario 3: POST /import without description and images returns 400"() {
        when: "I submit without description and images"
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

    def "Scenario 4: GET job status after IMPORT completes returns DONE with result and event stored"() {
        given: "a meal description and AI mock returning valid meal data"
        setupGeminiMealMock(createValidMealExtractionResponse())

        and: "I submit the import job"
        def submitResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Sałatka Cezar z kurczakiem na lunch")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()
        def jobId = submitResponse.body().jsonPath().getString("jobId")

        when: "I poll for job status"
        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, jobId)
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, jobStatusEndpoint)
                .get(jobStatusEndpoint)
                .then()
                .extract()

        then: "response status is 200 with DONE status"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "DONE"
        body.getString("jobType") == "IMPORT"
        body.getString("jobId") == jobId

        and: "result contains meal data"
        body.getString("result.status") == "success"
        body.getString("result.title") == "Sałatka Cezar z kurczakiem"
        body.getInt("result.caloriesKcal") == 450

        and: "health event is stored in database"
        def events = findEventsForDevice(DEVICE_ID)
        events.size() == 1
        events.first().eventType() == "MealRecorded.v1"
    }

    def "Scenario 5: GET job status after ANALYZE completes returns DONE with draftId"() {
        given: "a meal description and AI mock returning valid meal data"
        setupGeminiMealMock(createValidMealExtractionResponse())

        and: "I submit the analyze job"
        def submitResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Sałatka Cezar z kurczakiem na lunch")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()
        def jobId = submitResponse.body().jsonPath().getString("jobId")

        when: "I poll for job status"
        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, jobId)
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, jobStatusEndpoint)
                .get(jobStatusEndpoint)
                .then()
                .extract()

        then: "response status is 200 with DONE status"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "DONE"
        body.getString("jobType") == "ANALYZE"
        body.getString("jobId") == jobId

        and: "result contains draft data with draftId"
        body.getString("result.status") == "draft"
        body.getString("result.draftId") != null

        and: "no health event is stored in database yet"
        findEventsForDevice(DEVICE_ID).isEmpty()

        and: "draftId can be used for confirm flow"
        def draftId = body.getString("result.draftId")
        def confirmEndpoint = "/v1/meals/import/${draftId}/confirm"
        def confirmResponse = authenticatedPostRequest(DEVICE_ID, SECRET_BASE64, confirmEndpoint)
                .post(confirmEndpoint)
                .then()
                .extract()
        confirmResponse.statusCode() == 200
        confirmResponse.body().jsonPath().getString("status") == "success"
        findEventsForDevice(DEVICE_ID).size() == 1
    }

    def "Scenario 6: GET job status for non-existent jobId returns 404"() {
        given: "a non-existent job ID"
        def nonExistentJobId = "00000000-0000-0000-0000-000000000000"
        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, nonExistentJobId)

        when: "I poll for job status"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, jobStatusEndpoint)
                .get(jobStatusEndpoint)
                .then()
                .extract()

        then: "response status is 404"
        response.statusCode() == 404
    }

    def "Scenario 7: GET job status for another device's job returns 404 (device isolation)"() {
        given: "a job created by test-meal-jobs device"
        setupGeminiMealMock(createValidMealExtractionResponse())
        def submitResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Obiad")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()
        def jobId = submitResponse.body().jsonPath().getString("jobId")

        when: "a different device tries to access the job"
        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, jobId)
        def response = authenticatedGetRequest("test-meal-import", "dGVzdC1zZWNyZXQtMTIz", jobStatusEndpoint)
                .get(jobStatusEndpoint)
                .then()
                .extract()

        then: "response status is 404 (device isolation)"
        response.statusCode() == 404
    }

    def "Scenario 8: AI exception results in FAILED job with safe error message"() {
        given: "AI returns an error"
        TestChatModelConfiguration.setThrowOnAllCalls()

        when: "I submit the meal description"
        def submitResponse = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "Jakiś posiłek")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()
        def jobId = submitResponse.body().jsonPath().getString("jobId")

        and: "I poll for job status"
        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, jobId)
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, jobStatusEndpoint)
                .get(jobStatusEndpoint)
                .then()
                .extract()

        then: "response shows FAILED status with safe error message"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "FAILED"
        body.getString("errorMessage") != null
        !body.getString("errorMessage").contains("Simulated")
        !body.getString("errorMessage").contains("Exception")
    }

    def "Scenario 9: POST /import/analyze with description returns 202 with jobId"() {
        given: "a meal description and AI mock returning valid meal data"
        setupGeminiMealMock(createValidMealExtractionResponse())

        when: "I submit the analyze request"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, ANALYZE_ENDPOINT, "Sałatka Cezar z kurczakiem")
                .post(ANALYZE_ENDPOINT)
                .then()
                .extract()

        then: "response status is 202 Accepted"
        response.statusCode() == 202

        and: "response contains a jobId"
        def body = response.body().jsonPath()
        body.getString("jobId") != null
    }

    def "Scenario 10: GET job status without HMAC returns 401"() {
        given: "a job ID"
        def jobId = UUID.randomUUID().toString()
        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, jobId)

        when: "I request without authentication"
        def response = RestAssured.given()
                .get(jobStatusEndpoint)
                .then()
                .extract()

        then: "response status is 401"
        response.statusCode() == 401
    }

    def "Scenario 11: Invalid image returns 400 without creating a job"() {
        given: "an empty image file"
        def emptyBytes = new byte[0]

        when: "I submit the empty file"
        def response = authenticatedMultipartRequestWithImages(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, [createImageSpec("empty.jpg", emptyBytes, "image/jpeg")])
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }

    def "Scenario 12: Invalid content type returns 400 without creating a job"() {
        given: "a file with invalid content type"
        def textBytes = "this is not an image".getBytes()

        when: "I submit the invalid file"
        def response = authenticatedMultipartRequestWithImages(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, [createImageSpec("document.pdf", textBytes, "application/pdf")])
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
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

    def createImageSpec(String fileName, byte[] content, String mimeType) {
        return new MultiPartSpecBuilder(content)
                .fileName(fileName)
                .controlName("images")
                .mimeType(mimeType)
                .build()
    }

    byte[] createTestImageBytes() {
        def base64Jpeg = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBEQCEAwEPwAB//9k="
        return Base64.decoder.decode(base64Jpeg)
    }

    void setupGeminiMealMock(String jsonResponse) {
        TestChatModelConfiguration.setResponse(jsonResponse)
    }

    void cleanupMealImportJobsForDevice(String deviceId) {
        jdbcTemplate.update("DELETE FROM meal_import_jobs WHERE device_id = ?", deviceId)
    }

    String createValidMealExtractionResponse() {
        return """{
            "isMeal": true,
            "confidence": 0.85,
            "title": "Sałatka Cezar z kurczakiem",
            "description": "Sałatka Cezar z kurczakiem - rozbicie składników",
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
