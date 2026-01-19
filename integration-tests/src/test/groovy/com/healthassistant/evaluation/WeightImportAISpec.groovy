package com.healthassistant.evaluation

import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import spock.lang.Requires
import spock.lang.Title

@Title("Feature: Weight Import AI Accuracy")
@Requires({ System.getenv('GEMINI_API_KEY') })
class WeightImportAISpec extends BaseEvaluationSpec {

    private static final String SECRET_BASE64 = TEST_SECRET_BASE64
    private static final String IMPORT_ENDPOINT = "/v1/weight/import-image"

    def setup() {
        // BaseEvaluationSpec.cleanAllData() handles cleanup via date-based deletion
    }

    def "AI correctly extracts ALL body composition data from two-part scale screenshot"() {
        given: "two parts of scale screenshot (scrolled view)"
        def part1 = loadScreenshot("/screenshots/weight/weight_part1.jpeg")
        def part2 = loadScreenshot("/screenshots/weight/weight_part2.jpeg")

        when: "I import both images via real Gemini API"
        def importResponse = authenticatedMultipartRequestMultipleImages(
                getTestDeviceId(), SECRET_BASE64, IMPORT_ENDPOINT,
                ["weight_part1.jpeg": part1, "weight_part2.jpeg": part2]
        )
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

        and: "all values match EXACTLY what's on screenshots"
        println "DEBUG: Full response: ${importResponse.body().asString()}"

        def weightKg = body.getDouble("weightKg")
        def bmi = body.getDouble("bmi")
        def score = body.get("score")
        println "DEBUG: weightKg=${weightKg}, bmi=${bmi}, score=${score}"
        weightKg == 72.6
        bmi == 23.4
        score == 92 || score == 92.3

        and: "body composition values are exact"
        def bodyFatPercent = body.getDouble("bodyFatPercent")
        def musclePercent = body.getDouble("musclePercent")
        def hydrationPercent = body.getDouble("hydrationPercent")
        def boneMassKg = body.getDouble("boneMassKg")
        println "DEBUG: bodyFat=${bodyFatPercent}, muscle=${musclePercent}, hydration=${hydrationPercent}, bone=${boneMassKg}"
        bodyFatPercent == 21.0
        musclePercent == 52.1
        hydrationPercent == 57.8
        boneMassKg == 2.9

        and: "metabolism values are exact"
        def bmrKcal = body.getInt("bmrKcal")
        def visceralFatLevel = body.getInt("visceralFatLevel")
        def metabolicAge = body.getInt("metabolicAge")
        println "DEBUG: bmr=${bmrKcal}, visceral=${visceralFatLevel}, metabolicAge=${metabolicAge}"
        bmrKcal == 1555
        visceralFatLevel == 8
        metabolicAge == 31

        and: "confidence is above threshold"
        def confidence = body.getDouble("confidence")
        println "DEBUG: confidence=${confidence}"
        confidence >= 0.7
    }

    def "Imported weight data appears in /v1/weight/latest with exact values"() {
        given: "two-part screenshot is imported"
        def part1 = loadScreenshot("/screenshots/weight/weight_part1.jpeg")
        def part2 = loadScreenshot("/screenshots/weight/weight_part2.jpeg")
        authenticatedMultipartRequestMultipleImages(
                getTestDeviceId(), SECRET_BASE64, IMPORT_ENDPOINT,
                ["weight_part1.jpeg": part1, "weight_part2.jpeg": part2]
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .statusCode(200)

        when: "I query /v1/weight/latest"
        waitForProjections()
        def latestResponse = authenticatedGetRequest(getTestDeviceId(), SECRET_BASE64, "/v1/weight/latest")
                .get("/v1/weight/latest")
                .then()
                .extract()

        then: "exact values are stored"
        latestResponse.statusCode() == 200
        def body = latestResponse.body().jsonPath()
        println "DEBUG: /v1/weight/latest response: ${latestResponse.body().asString()}"

        def weightKg = body.getDouble("measurement.weightKg")
        def bmi = body.getDouble("measurement.bmi")
        def bodyFatPercent = body.getDouble("measurement.bodyFatPercent")
        def musclePercent = body.getDouble("measurement.musclePercent")
        def bmrKcal = body.get("measurement.bmrKcal")
        def visceralFatLevel = body.get("measurement.visceralFatLevel")

        println "DEBUG: weightKg=${weightKg}, bmi=${bmi}, bodyFat=${bodyFatPercent}, muscle=${musclePercent}"
        weightKg == 72.6
        bmi == 23.4
        bodyFatPercent == 21.0
        musclePercent == 52.1
        bmrKcal == 1555
        visceralFatLevel == 8
    }

    def "Imported weight appears in /v1/weight/range with correct date"() {
        given: "screenshot is imported"
        def part1 = loadScreenshot("/screenshots/weight/weight_part1.jpeg")
        def part2 = loadScreenshot("/screenshots/weight/weight_part2.jpeg")
        authenticatedMultipartRequestMultipleImages(
                getTestDeviceId(), SECRET_BASE64, IMPORT_ENDPOINT,
                ["weight_part1.jpeg": part1, "weight_part2.jpeg": part2]
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .statusCode(200)

        when: "I query /v1/weight/range for 2026-01-12"
        waitForProjections()
        def rangeResponse = authenticatedGetRequest(getTestDeviceId(), SECRET_BASE64, "/v1/weight/range?startDate=2026-01-12&endDate=2026-01-12")
                .get("/v1/weight/range?startDate=2026-01-12&endDate=2026-01-12")
                .then()
                .extract()

        then: "measurement is found with exact date"
        rangeResponse.statusCode() == 200
        def body = rangeResponse.body().jsonPath()
        println "DEBUG: /v1/weight/range response: ${rangeResponse.body().asString()}"

        body.getInt("measurementCount") == 1
        body.getString("startDate") == "2026-01-12"
        body.getDouble("averageWeight") == 72.6
    }

    // Helper methods

    byte[] loadScreenshot(String resourcePath) {
        def stream = getClass().getResourceAsStream(resourcePath)
        if (stream == null) {
            throw new IllegalArgumentException("Screenshot not found: ${resourcePath}")
        }
        return stream.bytes
    }

    def authenticatedMultipartRequestMultipleImages(String deviceId, String secretBase64, String path, Map<String, byte[]> images) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", secretBase64)

        def request = RestAssured.given()
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)

        images.each { fileName, content ->
            def multiPart = new MultiPartSpecBuilder(content)
                    .fileName(fileName)
                    .controlName("images")
                    .mimeType("image/jpeg")
                    .build()
            request = request.multiPart(multiPart)
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
}
