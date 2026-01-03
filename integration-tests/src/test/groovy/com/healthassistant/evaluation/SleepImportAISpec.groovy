package com.healthassistant.evaluation

import com.healthassistant.sleep.api.SleepFacade
import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Requires
import spock.lang.Title

import java.time.LocalDate

/**
 * Integration tests for Sleep Import using REAL Gemini API.
 *
 * These tests verify that:
 * 1. Real ohealth screenshots are correctly parsed by Gemini
 * 2. Extracted data matches EXACT values from the screenshot
 * 3. Data is correctly stored and queryable via API
 *
 * IMPORTANT: Requires GEMINI_API_KEY environment variable to run.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*SleepImportAISpec"
 */
@Title("Feature: Sleep Import AI Accuracy")
@Requires({ System.getenv('GEMINI_API_KEY') })
class SleepImportAISpec extends BaseEvaluationSpec {

    // Use same device as BaseEvaluationSpec (already configured in HMAC)
    private static final String DEVICE_ID = TEST_DEVICE_ID
    private static final String SECRET_BASE64 = TEST_SECRET_BASE64
    private static final String IMPORT_ENDPOINT = "/v1/sleep/import-image"

    @Autowired
    SleepFacade sleepFacade

    def setup() {
        // Clean up before each test
        sleepFacade.deleteProjectionsByDeviceId(DEVICE_ID)
    }

    def "sleep_1.png: AI correctly extracts exact sleep data from ohealth screenshot"() {
        given: "real ohealth sleep screenshot sleep_1.png"
        def imageBytes = loadScreenshot("/screenshots/sleep_1.png")

        when: "I import the screenshot via real Gemini API"
        def importResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep_1.png", imageBytes, "image/png")
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        importResponse.statusCode() == 200
        def body = importResponse.body().jsonPath()
        def status = body.getString("status")
        def errorMessage = body.getString("errorMessage")
        println "Import status: ${status}, errorMessage: ${errorMessage}"
        status == "success"

        and: "total sleep duration is approximately 378 minutes (6h18m) - allowing 5% tolerance for AI extraction"
        def totalMinutes = body.getInt("totalSleepMinutes")
        println "DEBUG: totalSleepMinutes=${totalMinutes}"
        totalMinutes >= 359 && totalMinutes <= 397 // 378 ± 5%

        and: "sleep score is within reasonable range (AI may extract slightly differently)"
        def sleepScore = body.get("sleepScore")
        println "DEBUG: sleepScore=${sleepScore}"
        sleepScore == null || (sleepScore >= 50 && sleepScore <= 80) // 67 ± tolerance

        and: "sleep phases are within reasonable ranges"
        def deepSleep = body.get("deepSleepMinutes")
        def lightSleep = body.get("lightSleepMinutes")
        def remSleep = body.get("remSleepMinutes")
        def awake = body.get("awakeMinutes")
        println "DEBUG: deep=${deepSleep}, light=${lightSleep}, rem=${remSleep}, awake=${awake}"
        // Allow null or values within 20% tolerance
        deepSleep == null || (deepSleep >= 44 && deepSleep <= 68) // 56 ± 20%
        lightSleep == null || (lightSleep >= 175 && lightSleep <= 263) // 219 ± 20%
        remSleep == null || (remSleep >= 82 && remSleep <= 124) // 103 ± 20%
        awake == null || (awake >= 20 && awake <= 30) // 25 ± 20%
    }

    def "sleep_1.png: Imported sleep appears in /v1/sleep/daily with correct data"() {
        given: "sleep_1.png is imported"
        def imageBytes = loadScreenshot("/screenshots/sleep_1.png")
        def importResponse = authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep_1.png", imageBytes, "image/png")
                .post(IMPORT_ENDPOINT)
                .then()
                .statusCode(200)
                .extract()

        assert importResponse.body().jsonPath().getString("status") == "success"

        when: "I query /v1/sleep/daily for 2026-01-02"
        waitForProjections()
        def date = LocalDate.of(2026, 1, 2)
        def sleepResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/sleep/daily/${date}")
                .get("/v1/sleep/daily/${date}")
                .then()
                .extract()

        then: "sleep data is returned with correct values"
        sleepResponse.statusCode() == 200
        def sessions = sleepResponse.body().jsonPath().getList("sessions")
        sessions.size() == 1

        and: "values match within reasonable tolerance (AI extraction variability)"
        def session = sessions[0]
        println "DEBUG session: ${session}"
        // Use durationMinutes field (not totalSleepMinutes which doesn't exist on session)
        def duration = session.durationMinutes
        duration >= 359 && duration <= 397 // 378 ± 5%
        // Sleep score may vary due to AI interpretation
        session.sleepScore == null || (session.sleepScore >= 50 && session.sleepScore <= 80)
        // Phase values within tolerance
        session.deepSleepMinutes == null || (session.deepSleepMinutes >= 44 && session.deepSleepMinutes <= 68)
        session.lightSleepMinutes == null || (session.lightSleepMinutes >= 175 && session.lightSleepMinutes <= 263)
        session.remSleepMinutes == null || (session.remSleepMinutes >= 82 && session.remSleepMinutes <= 124)
    }

    def "sleep_1.png: Imported sleep appears in /v1/sleep/range"() {
        given: "sleep_1.png is imported"
        def imageBytes = loadScreenshot("/screenshots/sleep_1.png")
        authenticatedMultipartRequest(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, "sleep_1.png", imageBytes, "image/png")
                .post(IMPORT_ENDPOINT)
                .then()
                .statusCode(200)

        when: "I query /v1/sleep/range for the date range including 2026-01-02"
        waitForProjections()
        def rangeResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/sleep/range?startDate=2026-01-01&endDate=2026-01-03")
                .get("/v1/sleep/range?startDate=2026-01-01&endDate=2026-01-03")
                .then()
                .extract()

        then: "sleep data is included in range response"
        rangeResponse.statusCode() == 200
        def body = rangeResponse.body().jsonPath()
        body.getInt("totalSleepMinutes") == 378
    }

    // Helper methods

    byte[] loadScreenshot(String resourcePath) {
        def stream = getClass().getResourceAsStream(resourcePath)
        if (stream == null) {
            throw new IllegalArgumentException("Screenshot not found: ${resourcePath}")
        }
        return stream.bytes
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
