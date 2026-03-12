package com.healthassistant.evaluation

import io.restassured.RestAssured
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.util.concurrent.TimeUnit

@Title("Feature: Meal Import AI Accuracy")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class MealImportAISpec extends BaseEvaluationSpec {

    private static final String SECRET_BASE64 = TEST_SECRET_BASE64
    private static final String IMPORT_ENDPOINT = "/v1/meals/import"
    private static final String JOB_STATUS_TEMPLATE = "/v1/meals/import/jobs/%s"

    def setup() {
    }

    def "AI correctly estimates banana nutritional values"() {
        given: "a simple banana description"
        def description = "banan"

        when: "importing via Gemini API and polling for result"
        def result = submitImportAndGetResult(description)

        then: "import is successful"
        result.getString("result.status") == "success"

        and: "calories are reasonable for banana (~112 kcal +/-20%: 90-135 kcal)"
        def calories = result.getInt("result.caloriesKcal")
        println "DEBUG: banana caloriesKcal=${calories}"
        calories >= 90 && calories <= 135

        and: "carbs are dominant macro (~28.5g +/-20%: 23-34g)"
        def carbs = result.getInt("result.carbohydratesGrams")
        println "DEBUG: banana carbohydratesGrams=${carbs}"
        carbs >= 23 && carbs <= 34

        and: "low protein (~1.25g +/-20%: 0-2g)"
        def protein = result.getInt("result.proteinGrams")
        println "DEBUG: banana proteinGrams=${protein}"
        protein >= 0 && protein <= 2

        and: "very low fat (~0.4g +/-20%: 0-1g)"
        def fat = result.getInt("result.fatGrams")
        println "DEBUG: banana fatGrams=${fat}"
        fat >= 0 && fat <= 1

        and: "meal type is SNACK or BREAKFAST (banana can be either)"
        def mealType = result.getString("result.mealType")
        println "DEBUG: banana mealType=${mealType}"
        mealType in ["SNACK", "BREAKFAST", "BRUNCH"]

        and: "health rating is healthy"
        def healthRating = result.getString("result.healthRating")
        println "DEBUG: banana healthRating=${healthRating}"
        healthRating in ["HEALTHY", "VERY_HEALTHY"]
    }

    def "AI correctly estimates black coffee nutritional values"() {
        given: "a coffee description"
        def description = "kubek czarnej kawy"

        when: "importing via Gemini API and polling for result"
        def result = submitImportAndGetResult(description)

        then: "import is successful"
        result.getString("result.status") == "success"

        and: "calories are very low for black coffee (0-15 kcal)"
        def calories = result.getInt("result.caloriesKcal")
        println "DEBUG: coffee caloriesKcal=${calories}"
        calories >= 0 && calories <= 20

        and: "meal type is DRINK"
        def mealType = result.getString("result.mealType")
        println "DEBUG: coffee mealType=${mealType}"
        mealType == "DRINK"
    }

    def "AI correctly estimates grilled chicken with broccoli and rice"() {
        given: "a detailed healthy meal description"
        def description = "200g grillowanego kurczaka, 200g brokuła i 100g ugotowanego ryżu"

        when: "importing via Gemini API and polling for result"
        def result = submitImportAndGetResult(description)

        then: "import is successful"
        result.getString("result.status") == "success"

        and: "calories are reasonable for this meal (400-650 kcal)"
        def calories = result.getInt("result.caloriesKcal")
        println "DEBUG: chicken meal caloriesKcal=${calories}"
        calories >= 350 && calories <= 750

        and: "protein is high from chicken (40-80g)"
        def protein = result.getInt("result.proteinGrams")
        println "DEBUG: chicken meal proteinGrams=${protein}"
        protein >= 35 && protein <= 90

        and: "carbs are moderate from rice and broccoli (30-60g)"
        def carbs = result.getInt("result.carbohydratesGrams")
        println "DEBUG: chicken meal carbohydratesGrams=${carbs}"
        carbs >= 25 && carbs <= 70

        and: "fat is low for grilled chicken (5-20g)"
        def fat = result.getInt("result.fatGrams")
        println "DEBUG: chicken meal fatGrams=${fat}"
        fat >= 3 && fat <= 25

        and: "meal type is reasonable for a main dish"
        def mealType = result.getString("result.mealType")
        println "DEBUG: chicken meal mealType=${mealType}"
        mealType in ["LUNCH", "DINNER", "SNACK", "BRUNCH"]

        and: "health rating is healthy"
        def healthRating = result.getString("result.healthRating")
        println "DEBUG: chicken meal healthRating=${healthRating}"
        healthRating in ["HEALTHY", "VERY_HEALTHY"]
    }

    // Helper methods

    private submitImportAndGetResult(String description) {
        def submitResponse = authenticatedMultipartRequestWithDescription(getTestDeviceId(), SECRET_BASE64, IMPORT_ENDPOINT, description)
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()
        assert submitResponse.statusCode() == 202
        def jobId = submitResponse.body().jsonPath().getString("jobId")

        def jobStatusEndpoint = String.format(JOB_STATUS_TEMPLATE, jobId)
        org.awaitility.Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until({
                    def response = authenticatedGetRequest(getTestDeviceId(), SECRET_BASE64, jobStatusEndpoint)
                            .get(jobStatusEndpoint)
                            .then()
                            .extract()
                    def status = response.body().jsonPath().getString("status")
                    return status == "DONE" || status == "FAILED"
                })

        def response = authenticatedGetRequest(getTestDeviceId(), SECRET_BASE64, jobStatusEndpoint)
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
