package com.healthassistant

import com.healthassistant.sleep.api.SleepFacade
import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.Instant
import java.time.LocalDate

/**
 * Integration tests for Sleep Import from ohealth Screenshot via AI endpoint
 */
@Title("Feature: Sleep Import from ohealth Screenshot via AI")
class SleepImportSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String IMPORT_ENDPOINT = "/v1/sleep/import-image"

    @Autowired
    SleepFacade sleepFacade

    def cleanup() {
        sleepFacade?.deleteAllProjections()
        TestChatModelConfiguration.resetResponse()
    }

    def "Scenario 1: Valid sleep image is extracted and stored successfully"() {
        given: "a valid sleep image and AI mock returning valid sleep data"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidSleepExtractionResponse())

        when: "I submit the sleep image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getString("sleepId") != null
        body.getString("eventId") != null
        body.getInt("totalSleepMinutes") == 427
        body.getInt("sleepScore") == 83
        body.getDouble("confidence") == 0.95
    }

    def "Scenario 2: Sleep event is stored in database after import"() {
        given: "a valid sleep image and AI mock"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidSleepExtractionResponse())

        when: "I submit the sleep image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"

        and: "event is stored in database"
        def events = findAllEvents()
        events.size() == 1
        events.first().eventType() == "SleepSessionRecorded.v1"
        events.first().payload().source() == "OHEALTH_SCREENSHOT"
    }

    def "Scenario 3: Import overwrites Health Connect sleep with same sleepStart"() {
        given: "a Health Connect sleep event already exists"
        def sleepStart = Instant.parse("2025-12-24T23:08:00Z")
        def sleepEnd = Instant.parse("2025-12-25T06:15:00Z")
        submitSleepEvent(DEVICE_ID, "hc-sleep-1", sleepStart, sleepEnd, 427, "com.google.android.apps.fitness")

        and: "I have a sleep import image with same start time but with phases"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidSleepExtractionResponse())

        when: "I submit the sleep import"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful and indicates overwrite"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getBoolean("overwrote") == true

        and: "only one sleep event exists (overwritten)"
        def events = findAllEvents()
        events.size() == 1

        and: "the event now has phase data from import"
        def payload = events.first().payload()
        payload.lightSleepMinutes() == 180
        payload.deepSleepMinutes() == 120
        payload.remSleepMinutes() == 90
        payload.awakeMinutes() == 37
        payload.sleepScore() == 83
    }

    def "Scenario 4: Different sleepStart creates new sleep record"() {
        given: "a Health Connect sleep event exists"
        def existingSleepStart = Instant.parse("2025-12-23T22:00:00Z")
        def existingSleepEnd = Instant.parse("2025-12-24T06:00:00Z")
        submitSleepEvent(DEVICE_ID, "hc-sleep-1", existingSleepStart, existingSleepEnd, 480, "com.google.android.apps.fitness")

        and: "I have a sleep import image with DIFFERENT start time"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidSleepExtractionResponse()) // This has sleepStart at 23:08

        when: "I submit the sleep import"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful and indicates NO overwrite"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getBoolean("overwrote") == false

        and: "two sleep events exist (both Health Connect and imported)"
        def events = findAllEvents()
        events.size() == 2
    }

    def "Scenario 5: Non-sleep image returns failure status"() {
        given: "an image that is not a sleep screenshot"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createNotSleepResponse())

        when: "I submit the non-sleep image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "photo.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200 but with failed status"
        response.statusCode() == 200

        and: "response indicates extraction failure"
        def body = response.body().jsonPath()
        body.getString("status") == "failed"
        body.getString("errorMessage").contains("Not a sleep screenshot")

        and: "no event is stored in database"
        findAllEvents().isEmpty()
    }

    def "Scenario 6: Empty file returns 400 Bad Request"() {
        given: "an empty image file"
        def emptyBytes = new byte[0]

        when: "I submit the empty file"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "empty.jpg", emptyBytes, "image/jpeg")
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

    def "Scenario 7: Invalid content type returns 400 Bad Request"() {
        given: "a file with invalid content type"
        def textBytes = "this is not an image".getBytes()

        when: "I submit the invalid file"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "document.pdf", textBytes, "application/pdf")
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

    def "Scenario 8: Request without HMAC authentication returns 401"() {
        given: "a valid image but no HMAC headers"
        def imageBytes = createTestImageBytes()

        when: "I submit without authentication"
        def response = RestAssured.given()
                .multiPart("image", "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 401"
        response.statusCode() == 401
    }

    def "Scenario 9: Sleep phases are correctly extracted and returned"() {
        given: "a valid sleep image with phase data"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidSleepExtractionResponse())

        when: "I submit the sleep image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response contains all phase data"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("lightSleepMinutes") == 180
        body.getInt("deepSleepMinutes") == 120
        body.getInt("remSleepMinutes") == 90
        body.getInt("awakeMinutes") == 37
    }

    def "Scenario 10: Sleep score is extracted correctly"() {
        given: "a sleep image with score"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createSleepWithHighScoreResponse())

        when: "I submit the sleep image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response contains the score"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getInt("sleepScore") == 95
    }

    def "Scenario 11: Duplicate import (same image) overwrites"() {
        given: "a valid sleep image"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidSleepExtractionResponse())

        and: "I submit the image first time"
        def firstResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        def firstSleepId = firstResponse.body().jsonPath().getString("sleepId")

        when: "I submit the same image again"
        setupGeminiVisionMock(createValidSleepExtractionResponse())
        def secondResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "second response is successful and overwrites"
        secondResponse.statusCode() == 200
        secondResponse.body().jsonPath().getString("sleepId") == firstSleepId
        secondResponse.body().jsonPath().getBoolean("overwrote") == true

        and: "only one event is stored in database"
        def events = findAllEvents()
        events.size() == 1
    }

    def "Scenario 12: PNG image is accepted"() {
        given: "a valid PNG image"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidSleepExtractionResponse())

        when: "I submit the PNG image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.png", imageBytes, "image/png")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
    }

    def "Scenario 13: Sleep projection includes score after import"() {
        given: "a valid sleep import"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidSleepExtractionResponse())

        when: "I submit the sleep image"
        def importResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        importResponse.statusCode() == 200
        importResponse.body().jsonPath().getString("status") == "success"

        when: "I query sleep data for that date"
        def date = LocalDate.of(2025, 12, 25)
        def sleepResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/sleep/daily/${date}")
                .get("/v1/sleep/daily/${date}")
                .then()
                .extract()

        then: "sleep data includes phases and score"
        sleepResponse.statusCode() == 200
        def sessions = sleepResponse.body().jsonPath().getList("sessions")
        sessions.size() == 1
        sessions[0].sleepScore == 83
        sessions[0].lightSleepMinutes == 180
        sessions[0].deepSleepMinutes == 120
        sessions[0].remSleepMinutes == 90
    }

    // Helper methods

    def authenticatedMultipartRequest(String deviceId, String secretBase64, String path, String fileName, byte[] fileContent, String mimeType) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", secretBase64)

        def multiPartSpec = new MultiPartSpecBuilder(fileContent)
                .fileName(fileName)
                .controlName("image")
                .mimeType(mimeType)
                .build()

        return RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart(multiPartSpec)
    }

    byte[] createTestImageBytes() {
        // Create a minimal valid JPEG header for testing
        def base64Jpeg = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBEQCEAwEPwAB//9k="
        return Base64.decoder.decode(base64Jpeg)
    }

    void setupGeminiVisionMock(String jsonResponse) {
        TestChatModelConfiguration.setResponse(jsonResponse)
    }

    void submitSleepEvent(String deviceId, String sleepId, Instant sleepStart, Instant sleepEnd, int totalMinutes, String originPackage) {
        def body = """
        {
            "deviceId": "${deviceId}",
            "events": [
                {
                    "idempotencyKey": "${deviceId}|sleep|${sleepId}",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "${sleepEnd.toString()}",
                    "payload": {
                        "sleepId": "${sleepId}",
                        "sleepStart": "${sleepStart.toString()}",
                        "sleepEnd": "${sleepEnd.toString()}",
                        "totalMinutes": ${totalMinutes},
                        "originPackage": "${originPackage}"
                    }
                }
            ]
        }
        """.stripIndent().trim()

        authenticatedPostRequestWithBody(deviceId, SECRET_BASE64, "/v1/health-events", body)
                .post("/v1/health-events")
                .then()
                .statusCode(200)
    }

    String createValidSleepExtractionResponse() {
        return """{
            "isSleepScreenshot": true,
            "confidence": 0.95,
            "sleepDate": "2025-12-25",
            "sleepStart": "00:08",
            "wakeTime": "07:15",
            "totalSleepMinutes": 427,
            "sleepScore": 83,
            "phases": {
                "lightSleepMinutes": 180,
                "deepSleepMinutes": 120,
                "remSleepMinutes": 90,
                "awakeMinutes": 37
            },
            "qualityLabel": "Normalny"
        }"""
    }

    String createNotSleepResponse() {
        return """{
            "isSleepScreenshot": false,
            "confidence": 0.9,
            "validationError": "Not a sleep screenshot - appears to be a regular photo"
        }"""
    }

    String createSleepWithHighScoreResponse() {
        return """{
            "isSleepScreenshot": true,
            "confidence": 0.92,
            "sleepDate": "2025-12-25",
            "sleepStart": "22:30",
            "wakeTime": "06:30",
            "totalSleepMinutes": 480,
            "sleepScore": 95,
            "phases": {
                "lightSleepMinutes": 160,
                "deepSleepMinutes": 150,
                "remSleepMinutes": 130,
                "awakeMinutes": 40
            },
            "qualityLabel": "Doskonały"
        }"""
    }
}
