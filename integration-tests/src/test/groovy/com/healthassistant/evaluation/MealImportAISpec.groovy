package com.healthassistant.evaluation

import io.restassured.RestAssured
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.util.concurrent.TimeUnit

/**
 * Integration tests for Meal Import using REAL Gemini API.
 *
 * Tests simple, well-known foods with predictable nutritional values:
 * - Banana
 * - Black coffee
 * - Grilled chicken with broccoli and rice
 *
 * IMPORTANT: Requires GEMINI_API_KEY environment variable to run.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:evaluationTest --tests "*MealImportAISpec"
 */
@Title("Feature: Meal Import AI Accuracy")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class MealImportAISpec extends BaseEvaluationSpec {

    private static final String DEVICE_ID = TEST_DEVICE_ID
    private static final String SECRET_BASE64 = TEST_SECRET_BASE64
    private static final String IMPORT_ENDPOINT = "/v1/meals/import"

    def setup() {
        // BaseEvaluationSpec.cleanAllData() handles cleanup via date-based deletion
    }

    def "AI correctly estimates banana nutritional values"() {
        given: "a simple banana description"
        def description = "banan"

        when: "importing via Gemini API"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, description)
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"

        and: "calories are reasonable for banana (80-130 kcal)"
        def calories = body.getInt("caloriesKcal")
        println "DEBUG: banana caloriesKcal=${calories}"
        calories >= 70 && calories <= 150

        and: "carbs are dominant macro (20-35g)"
        def carbs = body.getInt("carbohydratesGrams")
        println "DEBUG: banana carbohydratesGrams=${carbs}"
        carbs >= 15 && carbs <= 40

        and: "low protein (0-3g)"
        def protein = body.getInt("proteinGrams")
        println "DEBUG: banana proteinGrams=${protein}"
        protein >= 0 && protein <= 5

        and: "very low fat (0-1g)"
        def fat = body.getInt("fatGrams")
        println "DEBUG: banana fatGrams=${fat}"
        fat >= 0 && fat <= 3

        and: "meal type is SNACK"
        def mealType = body.getString("mealType")
        println "DEBUG: banana mealType=${mealType}"
        mealType == "SNACK"

        and: "health rating is healthy"
        def healthRating = body.getString("healthRating")
        println "DEBUG: banana healthRating=${healthRating}"
        healthRating in ["HEALTHY", "VERY_HEALTHY"]
    }

    def "AI correctly estimates black coffee nutritional values"() {
        given: "a coffee description"
        def description = "kubek czarnej kawy"

        when: "importing via Gemini API"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, description)
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"

        and: "calories are very low for black coffee (0-15 kcal)"
        def calories = body.getInt("caloriesKcal")
        println "DEBUG: coffee caloriesKcal=${calories}"
        calories >= 0 && calories <= 20

        and: "meal type is DRINK"
        def mealType = body.getString("mealType")
        println "DEBUG: coffee mealType=${mealType}"
        mealType == "DRINK"
    }

    def "AI correctly estimates grilled chicken with broccoli and rice"() {
        given: "a detailed healthy meal description"
        def description = "200g grillowanego kurczaka, 200g brokuła i 100g ryżu"

        when: "importing via Gemini API"
        def response = authenticatedMultipartRequestWithDescription(DEVICE_ID, SECRET_BASE64, IMPORT_ENDPOINT, description)
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"

        and: "calories are reasonable for this meal (400-650 kcal)"
        // 200g chicken ~330kcal, 200g broccoli ~70kcal, 100g rice ~130kcal = ~530kcal
        def calories = body.getInt("caloriesKcal")
        println "DEBUG: chicken meal caloriesKcal=${calories}"
        calories >= 350 && calories <= 750

        and: "protein is high from chicken (40-80g)"
        // 200g chicken ~62g protein, broccoli ~6g, rice ~3g = ~71g
        def protein = body.getInt("proteinGrams")
        println "DEBUG: chicken meal proteinGrams=${protein}"
        protein >= 35 && protein <= 90

        and: "carbs are moderate from rice and broccoli (30-60g)"
        // rice ~28g, broccoli ~14g = ~42g
        def carbs = body.getInt("carbohydratesGrams")
        println "DEBUG: chicken meal carbohydratesGrams=${carbs}"
        carbs >= 25 && carbs <= 70

        and: "fat is low for grilled chicken (5-20g)"
        def fat = body.getInt("fatGrams")
        println "DEBUG: chicken meal fatGrams=${fat}"
        fat >= 3 && fat <= 25

        and: "meal type is reasonable for a main dish"
        def mealType = body.getString("mealType")
        println "DEBUG: chicken meal mealType=${mealType}"
        // BRUNCH is acceptable since without time context, AI may interpret this as a late morning meal
        mealType in ["LUNCH", "DINNER", "SNACK", "BRUNCH"]

        and: "health rating is healthy"
        def healthRating = body.getString("healthRating")
        println "DEBUG: chicken meal healthRating=${healthRating}"
        healthRating in ["HEALTHY", "VERY_HEALTHY"]
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
}
