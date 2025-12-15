package com.healthassistant

import com.healthassistant.workout.WorkoutProjectionJpaRepository
import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

/**
 * Integration tests for Workout Import from Image (AI extraction) endpoint
 */
@Title("Feature: Workout Import from GymRun Screenshot via AI")
class WorkoutImportSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String IMPORT_ENDPOINT = "/v1/workouts/import-image"

    @Autowired
    WorkoutProjectionJpaRepository workoutProjectionRepository

    def cleanup() {
        if (workoutProjectionRepository != null) {
            workoutProjectionRepository.deleteAll()
        }
        TestChatModelConfiguration.resetResponse()
    }

    def "Scenario 1: Valid workout image is extracted and stored successfully"() {
        given: "a valid workout image and AI mock returning valid workout data"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidWorkoutExtractionResponse())

        when: "I submit the workout image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getString("workoutId") != null
        body.getString("eventId") != null
        body.getInt("exerciseCount") == 2
        body.getInt("totalSets") == 5
        body.getDouble("confidence") == 0.95
    }

    def "Scenario 2: Workout event is stored in database after import"() {
        given: "a valid workout image and AI mock"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidWorkoutExtractionResponse())

        when: "I submit the workout image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"

        and: "event is stored in database"
        def events = eventRepository.findAll()
        events.size() == 1
        events.first().eventType == "WorkoutRecorded.v1"
        events.first().payload.get("source") == "GYMRUN_SCREENSHOT"
    }

    def "Scenario 3: Duplicate image upload returns same workoutId"() {
        given: "a valid workout image and AI mock"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidWorkoutExtractionResponse())

        and: "I submit the image first time"
        def firstResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        def firstWorkoutId = firstResponse.body().jsonPath().getString("workoutId")

        when: "I submit the same image again"
        setupGeminiVisionMock(createValidWorkoutExtractionResponse())
        def secondResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "both responses have same workoutId (idempotent)"
        secondResponse.statusCode() == 200
        secondResponse.body().jsonPath().getString("workoutId") == firstWorkoutId

        and: "only one event is stored in database"
        def events = eventRepository.findAll()
        events.size() == 1
    }

    def "Scenario 4: Non-workout image returns failure status"() {
        given: "an image that is not a workout screenshot"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createNotWorkoutResponse())

        when: "I submit the non-workout image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "photo.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 200 but with failed status"
        response.statusCode() == 200

        and: "response indicates extraction failure"
        def body = response.body().jsonPath()
        body.getString("status") == "failed"
        body.getString("errorMessage").contains("Not a workout screenshot")

        and: "no event is stored in database"
        eventRepository.findAll().isEmpty()
    }

    def "Scenario 5: Empty file returns 400 Bad Request"() {
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

    def "Scenario 6: Invalid content type returns 400 Bad Request"() {
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

    def "Scenario 7: Request without HMAC authentication returns 401"() {
        given: "a valid image but no HMAC headers"
        def imageBytes = createTestImageBytes()

        when: "I submit without authentication"
        def response = RestAssured.given()
                .multiPart("image", "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response status is 401"
        response.statusCode() == 401
    }

    def "Scenario 8: Extracted workout has correct exercises and sets"() {
        given: "a valid workout image with detailed extraction"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createDetailedWorkoutExtractionResponse())

        when: "I submit the workout image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"

        and: "exercise count matches"
        body.getInt("exerciseCount") == 3

        and: "total sets count matches"
        body.getInt("totalSets") == 9

        and: "event payload has correct structure"
        def events = eventRepository.findAll()
        def payload = events.first().payload
        def exercises = payload.get("exercises") as List
        exercises.size() == 3
        exercises[0].name == "Podciąganie się nachwytem"
        (exercises[0].sets as List).size() == 3
    }

    def "Scenario 9: Workout note is extracted correctly"() {
        given: "a workout image with note"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createWorkoutWithNoteResponse())

        when: "I submit the workout image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response contains the note"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("note") == "Plecy i biceps"

        and: "event payload has note"
        def events = eventRepository.findAll()
        events.first().payload.get("note") == "Plecy i biceps"
    }

    def "Scenario 10: Bodyweight exercises have weight 0"() {
        given: "a workout image with bodyweight exercise"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createBodyweightExerciseResponse())

        when: "I submit the workout image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200

        and: "event has bodyweight exercise with weight 0"
        def events = eventRepository.findAll()
        def exercises = events.first().payload.get("exercises") as List
        def sets = exercises[0].sets as List
        sets[0].weightKg == 0.0
    }

    def "Scenario 11: Confidence score is returned in response"() {
        given: "a workout image with specific confidence"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createLowConfidenceResponse())

        when: "I submit the workout image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response contains confidence score"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getDouble("confidence") == 0.75
    }

    def "Scenario 12: AI error returns failure status"() {
        given: "AI returns an error"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMockError()

        when: "I submit the workout image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response indicates failure"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "failed"
        body.getString("errorMessage") != null
    }

    def "Scenario 13: PNG image is accepted"() {
        given: "a valid PNG image"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidWorkoutExtractionResponse())

        when: "I submit the PNG image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.png", imageBytes, "image/png")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
    }

    def "Scenario 14: WebP image is accepted"() {
        given: "a valid WebP image"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createValidWorkoutExtractionResponse())

        when: "I submit the WebP image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.webp", imageBytes, "image/webp")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
    }

    def "Scenario 15: Exercise order is preserved"() {
        given: "a workout image with multiple exercises in specific order"
        def imageBytes = createTestImageBytes()
        setupGeminiVisionMock(createDetailedWorkoutExtractionResponse())

        when: "I submit the workout image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is successful"
        response.statusCode() == 200

        and: "exercises are in correct order"
        def events = eventRepository.findAll()
        def exercises = events.first().payload.get("exercises") as List
        exercises[0].orderInWorkout == 1
        exercises[1].orderInWorkout == 2
        exercises[2].orderInWorkout == 3
    }

    // Helper methods

    def authenticatedMultipartRequest(String deviceId, String secretBase64, String path, String fileName, byte[] fileContent, String mimeType) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        // For multipart requests, body is empty in HMAC signature
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
        // This is a 1x1 pixel white JPEG
        def base64Jpeg = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBEQCEAwEPwAB//9k="
        return Base64.decoder.decode(base64Jpeg)
    }

    void setupGeminiVisionMock(String jsonResponse) {
        // Configure the test ChatModel to return this response
        TestChatModelConfiguration.setResponse(jsonResponse)
    }

    void setupGeminiVisionMockError() {
        // Configure the test ChatModel to return an error message
        // The actual error handling is tested by returning invalid JSON
        TestChatModelConfiguration.setResponse("Internal server error")
    }

    String createValidWorkoutExtractionResponse() {
        return """{
            "isWorkoutScreenshot": true,
            "confidence": 0.95,
            "performedAt": "2025-12-15T18:00:00Z",
            "note": "Plecy i biceps",
            "exercises": [
                {
                    "name": "Podciąganie się nachwytem",
                    "muscleGroup": "Plecy",
                    "orderInWorkout": 1,
                    "sets": [
                        {"setNumber": 1, "weightKg": 0, "reps": 12, "isWarmup": false},
                        {"setNumber": 2, "weightKg": 0, "reps": 10, "isWarmup": false},
                        {"setNumber": 3, "weightKg": 0, "reps": 8, "isWarmup": false}
                    ]
                },
                {
                    "name": "Wiosłowanie sztangielkami",
                    "muscleGroup": "Plecy",
                    "orderInWorkout": 2,
                    "sets": [
                        {"setNumber": 1, "weightKg": 18, "reps": 12, "isWarmup": false},
                        {"setNumber": 2, "weightKg": 18, "reps": 12, "isWarmup": false}
                    ]
                }
            ]
        }"""
    }

    String createNotWorkoutResponse() {
        return """{
            "isWorkoutScreenshot": false,
            "confidence": 0.9,
            "validationError": "Not a workout screenshot - appears to be a regular photo"
        }"""
    }

    String createDetailedWorkoutExtractionResponse() {
        return """{
            "isWorkoutScreenshot": true,
            "confidence": 0.95,
            "performedAt": "2025-12-15T18:00:00Z",
            "note": "Plecy i biceps",
            "exercises": [
                {
                    "name": "Podciąganie się nachwytem",
                    "muscleGroup": "Plecy",
                    "orderInWorkout": 1,
                    "sets": [
                        {"setNumber": 1, "weightKg": 0, "reps": 12, "isWarmup": false},
                        {"setNumber": 2, "weightKg": 0, "reps": 10, "isWarmup": false},
                        {"setNumber": 3, "weightKg": 0, "reps": 8, "isWarmup": false}
                    ]
                },
                {
                    "name": "Wiosłowanie sztangielkami",
                    "muscleGroup": "Plecy",
                    "orderInWorkout": 2,
                    "sets": [
                        {"setNumber": 1, "weightKg": 18, "reps": 12, "isWarmup": false},
                        {"setNumber": 2, "weightKg": 18, "reps": 12, "isWarmup": false},
                        {"setNumber": 3, "weightKg": 20, "reps": 10, "isWarmup": false}
                    ]
                },
                {
                    "name": "Uginanie przedramion",
                    "muscleGroup": "Biceps",
                    "orderInWorkout": 3,
                    "sets": [
                        {"setNumber": 1, "weightKg": 10, "reps": 12, "isWarmup": false},
                        {"setNumber": 2, "weightKg": 10, "reps": 12, "isWarmup": false},
                        {"setNumber": 3, "weightKg": 12, "reps": 10, "isWarmup": false}
                    ]
                }
            ]
        }"""
    }

    String createWorkoutWithNoteResponse() {
        return """{
            "isWorkoutScreenshot": true,
            "confidence": 0.95,
            "performedAt": "2025-12-15T18:00:00Z",
            "note": "Plecy i biceps",
            "exercises": [
                {
                    "name": "Podciąganie się",
                    "orderInWorkout": 1,
                    "sets": [
                        {"setNumber": 1, "weightKg": 0, "reps": 10, "isWarmup": false}
                    ]
                }
            ]
        }"""
    }

    String createBodyweightExerciseResponse() {
        return """{
            "isWorkoutScreenshot": true,
            "confidence": 0.9,
            "performedAt": "2025-12-15T18:00:00Z",
            "exercises": [
                {
                    "name": "Pompki",
                    "muscleGroup": "Klatka",
                    "orderInWorkout": 1,
                    "sets": [
                        {"setNumber": 1, "weightKg": 0, "reps": 20, "isWarmup": false}
                    ]
                }
            ]
        }"""
    }

    String createLowConfidenceResponse() {
        return """{
            "isWorkoutScreenshot": true,
            "confidence": 0.75,
            "performedAt": "2025-12-15T18:00:00Z",
            "exercises": [
                {
                    "name": "Unknown exercise",
                    "orderInWorkout": 1,
                    "sets": [
                        {"setNumber": 1, "weightKg": 50, "reps": 10, "isWarmup": false}
                    ]
                }
            ]
        }"""
    }
}
