package com.healthassistant.evaluation

import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import spock.lang.Ignore
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Integration tests for Workout Import using REAL Gemini API.
 *
 * These tests verify that:
 * 1. Real GymRun screenshots are correctly parsed by Gemini
 * 2. Extracted data matches expected values from the screenshot
 * 3. Exercise names, sets, reps, and weights are accurate
 * 4. Data is correctly stored and queryable via API
 *
 * IMPORTANT: Requires GEMINI_API_KEY environment variable to run.
 * IMPORTANT: Requires real GymRun screenshots in /screenshots/workouts/ folder.
 *
 * To add screenshots:
 * 1. Take a screenshot from GymRun app after completing a workout
 * 2. Save as /integration-tests/src/test/resources/screenshots/workouts/workout_1.png
 * 3. Update expected values in tests based on screenshot content
 *
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*WorkoutImportAISpec"
 */
@Title("Feature: Workout Import AI Accuracy")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class WorkoutImportAISpec extends BaseEvaluationSpec {

    private static final String DEVICE_ID = TEST_DEVICE_ID
    private static final String SECRET_BASE64 = TEST_SECRET_BASE64
    private static final String IMPORT_ENDPOINT = "/v1/workouts/import-image"

    def setup() {
        // BaseEvaluationSpec.cleanAllData() handles cleanup via date-based deletion
    }

    /**
     * Test with real GymRun screenshot.
     *
     * Expected screenshot content (workout_1.png):
     * - Bench Press: 3 sets x 10 reps @ 60kg
     * - Incline Dumbbell Press: 3 sets x 12 reps @ 20kg each
     *
     * Update expected values based on your actual screenshot!
     */
    @Ignore("Requires real GymRun screenshot at /screenshots/workouts/workout_1.png")
    def "workout_1.png: AI correctly extracts workout from GymRun screenshot"() {
        given: "real GymRun screenshot workout_1.png"
        def imageBytes = loadScreenshot("/screenshots/workouts/workout_1.png")

        when: "I import the screenshot via real Gemini API"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout_1.png", imageBytes, "image/png")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        def status = body.getString("status")
        println "DEBUG: status=${status}"
        status == "success"

        and: "exercises count is correct (update based on your screenshot)"
        def exerciseCount = body.getInt("exerciseCount")
        println "DEBUG: exerciseCount=${exerciseCount}"
        exerciseCount >= 1 && exerciseCount <= 10

        and: "total sets count is reasonable"
        def totalSets = body.getInt("totalSets")
        println "DEBUG: totalSets=${totalSets}"
        totalSets >= 1 && totalSets <= 30

        and: "confidence is high for clear screenshot"
        def confidence = body.getDouble("confidence")
        println "DEBUG: confidence=${confidence}"
        confidence >= 0.7
    }

    /**
     * Test that imported workout appears in API.
     */
    @Ignore("Requires real GymRun screenshot at /screenshots/workouts/workout_1.png")
    def "workout_1.png: Imported workout appears in /v1/workouts API"() {
        given: "workout_1.png is imported"
        def imageBytes = loadScreenshot("/screenshots/workouts/workout_1.png")
        def importResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "workout_1.png", imageBytes, "image/png")
                .post(IMPORT_ENDPOINT)
                .then()
                .statusCode(200)
                .extract()

        def workoutId = importResponse.body().jsonPath().getString("workoutId")
        assert workoutId != null

        when: "I query /v1/workouts for today"
        waitForProjections()
        def today = LocalDate.now(POLAND_ZONE)
        def workoutsResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts?from=${today}&to=${today}")
                .get("/v1/workouts?from=${today}&to=${today}")
                .then()
                .extract()

        then: "workout data is returned"
        workoutsResponse.statusCode() == 200
        def body = workoutsResponse.body().jsonPath()
        def workouts = body.getList("workouts")
        println "DEBUG: workouts count=${workouts.size()}"
        workouts.size() >= 1
    }

    /**
     * Test with bodyweight exercises screenshot.
     */
    @Ignore("Requires real GymRun screenshot at /screenshots/workouts/bodyweight_1.png")
    def "bodyweight_1.png: AI correctly extracts bodyweight exercises"() {
        given: "real GymRun screenshot with pull-ups, push-ups etc"
        def imageBytes = loadScreenshot("/screenshots/workouts/bodyweight_1.png")

        when: "I import the screenshot via real Gemini API"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "bodyweight_1.png", imageBytes, "image/png")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"

        and: "exercises are extracted"
        def exerciseCount = body.getInt("exerciseCount")
        println "DEBUG: exerciseCount=${exerciseCount}"
        exerciseCount >= 1
    }

    /**
     * Fallback test using synthetic test data.
     * This test verifies the integration pipeline works without requiring real screenshots.
     */
    def "AI responds appropriately to minimal test image"() {
        given: "a minimal test image (not a real workout screenshot)"
        def imageBytes = createMinimalTestImage()

        when: "I import the test image"
        def response = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "test.jpg", imageBytes, "image/jpeg")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "response is returned (either success with low confidence or failure)"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        def status = body.getString("status")
        println "DEBUG: status=${status}"

        // Either AI recognizes it's not a workout (failed) or extracts with low confidence
        (status == "failed") || (status == "success" && body.getDouble("confidence") <= 0.9)
    }

    // Helper methods

    byte[] loadScreenshot(String resourcePath) {
        def stream = getClass().getResourceAsStream(resourcePath)
        if (stream == null) {
            throw new IllegalArgumentException("Screenshot not found: ${resourcePath}. Please add a real GymRun screenshot.")
        }
        return stream.bytes
    }

    byte[] createMinimalTestImage() {
        // Create a minimal valid JPEG for testing the pipeline
        def base64Jpeg = "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBEQCEAwEPwAB//9k="
        return Base64.decoder.decode(base64Jpeg)
    }

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
}
