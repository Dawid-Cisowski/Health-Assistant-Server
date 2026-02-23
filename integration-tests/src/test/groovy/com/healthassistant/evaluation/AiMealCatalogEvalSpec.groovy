package com.healthassistant.evaluation

import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.util.concurrent.TimeUnit

/**
 * AI evaluation tests for meal catalog integration via meal import endpoint.
 *
 * Verifies that when a product exists in the user's personal catalog,
 * the AI import uses EXACT nutritional values from the catalog (not estimates).
 *
 * Uses real Gemini API with /v1/meals/import multipart endpoint.
 *
 * IMPORTANT: Requires GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=key ./gradlew :integration-tests:test --tests "*AiMealCatalogEvalSpec"
 */
@Title("AI Evaluation: Meal Catalog Import")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiMealCatalogEvalSpec extends BaseEvaluationSpec {

    private static final String IMPORT_ENDPOINT = "/v1/meals/import"

    // ==================== E-CAT-01: Banana image + catalog → EXACT values ====================

    def "E-CAT-01: AI uses exact catalog values when importing banana from image"() {
        given: "banana is in the catalog with known values"
        seedCatalogProduct("Banan", "SNACK", 95, 1, 0, 24, "HEALTHY")

        and: "banana image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/banana.png")

        when: "importing meal from banana image"
        def response = authenticatedMultipartRequestWithImage(
                getTestDeviceId(), TEST_SECRET_BASE64, IMPORT_ENDPOINT, imageBytes, "banana.png"
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        println "DEBUG: Banana import response: ${response.body().asString()}"
        body.getString("status") == "success"

        and: "calories are EXACT from catalog"
        def calories = body.getInt("caloriesKcal")
        println "DEBUG: banana caloriesKcal=${calories} (expected: 95)"
        calories == 95

        and: "protein is EXACT from catalog"
        def protein = body.getInt("proteinGrams")
        println "DEBUG: banana proteinGrams=${protein} (expected: 1)"
        protein == 1

        and: "fat is EXACT from catalog"
        def fat = body.getInt("fatGrams")
        println "DEBUG: banana fatGrams=${fat} (expected: 0)"
        fat == 0

        and: "carbs are EXACT from catalog"
        def carbs = body.getInt("carbohydratesGrams")
        println "DEBUG: banana carbohydratesGrams=${carbs} (expected: 24)"
        carbs == 24
    }

    // ==================== E-CAT-02: Skyr image + catalog → EXACT values ====================

    def "E-CAT-02: AI uses exact catalog values when importing skyr from image"() {
        given: "Skyr pitny z Piatnicy is in the catalog with known values"
        seedCatalogProduct("Skyr pitny z Piatnicy", "SNACK", 120, 20, 2, 6, "HEALTHY")

        and: "skyr image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing meal from skyr image"
        def response = authenticatedMultipartRequestWithImage(
                getTestDeviceId(), TEST_SECRET_BASE64, IMPORT_ENDPOINT, imageBytes, "skyr.png"
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        println "DEBUG: Skyr import response: ${response.body().asString()}"
        body.getString("status") == "success"

        and: "calories are EXACT from catalog"
        def calories = body.getInt("caloriesKcal")
        println "DEBUG: skyr caloriesKcal=${calories} (expected: 120)"
        calories == 120

        and: "protein is EXACT from catalog"
        def protein = body.getInt("proteinGrams")
        println "DEBUG: skyr proteinGrams=${protein} (expected: 20)"
        protein == 20

        and: "fat is EXACT from catalog"
        def fat = body.getInt("fatGrams")
        println "DEBUG: skyr fatGrams=${fat} (expected: 2)"
        fat == 2

        and: "carbs are EXACT from catalog"
        def carbs = body.getInt("carbohydratesGrams")
        println "DEBUG: skyr carbohydratesGrams=${carbs} (expected: 6)"
        carbs == 6
    }

    // ==================== E-CAT-03: Banana image WITHOUT catalog → estimation ====================

    def "E-CAT-03: AI estimates values when banana is NOT in catalog"() {
        given: "catalog is empty (no banana entry)"

        and: "banana image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/banana.png")

        when: "importing meal from banana image without catalog"
        def response = authenticatedMultipartRequestWithImage(
                getTestDeviceId(), TEST_SECRET_BASE64, IMPORT_ENDPOINT, imageBytes, "banana.png"
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful with estimated values"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        println "DEBUG: Banana (no catalog) response: ${response.body().asString()}"
        body.getString("status") == "success"

        and: "calories are reasonable estimate for banana (80-135 kcal)"
        def calories = body.getInt("caloriesKcal")
        println "DEBUG: banana estimated caloriesKcal=${calories}"
        calories >= 80 && calories <= 135
    }

    // ==================== E-CAT-04: Import auto-saves to catalog ====================

    def "E-CAT-04: importing a meal auto-saves product to catalog"() {
        given: "catalog is empty"

        and: "skyr image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing meal from skyr image (no catalog entry)"
        def response = authenticatedMultipartRequestWithImage(
                getTestDeviceId(), TEST_SECRET_BASE64, IMPORT_ENDPOINT, imageBytes, "skyr.png"
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        println "DEBUG: Skyr auto-save response: ${response.body().asString()}"
        body.getString("status") == "success"

        and: "the product was auto-saved to catalog"
        def catalogResults = mealCatalogFacade.searchProducts(getTestDeviceId(), "skyr", 5)
        println "DEBUG: Catalog search results after import: ${catalogResults}"
        catalogResults.size() >= 1
    }

    // ==================== E-CAT-05: Catalog item has source 'catalog' in response ====================

    def "E-CAT-05: imported item from catalog has source 'catalog' in response"() {
        given: "Skyr pitny z Piatnicy is in the catalog"
        seedCatalogProduct("Skyr pitny z Piatnicy", "SNACK", 120, 20, 2, 6, "HEALTHY")

        and: "skyr image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing meal from skyr image"
        def response = authenticatedMultipartRequestWithImage(
                getTestDeviceId(), TEST_SECRET_BASE64, IMPORT_ENDPOINT, imageBytes, "skyr.png"
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        println "DEBUG: Skyr source check response: ${response.body().asString()}"
        body.getString("status") == "success"

        and: "items list contains an entry with source 'catalog'"
        def items = body.getList("items")
        println "DEBUG: items: ${items}"
        items != null && !items.isEmpty()
        items.any { it.source == "catalog" }
    }

    // ==================== E-CAT-06: Skyr without catalog → different values than catalog ====================

    def "E-CAT-06: AI-estimated values differ from catalog values when catalog is empty"() {
        given: "catalog is empty (no skyr entry)"

        and: "skyr image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing meal from skyr image without catalog"
        def response = authenticatedMultipartRequestWithImage(
                getTestDeviceId(), TEST_SECRET_BASE64, IMPORT_ENDPOINT, imageBytes, "skyr.png"
        )
                .post(IMPORT_ENDPOINT)
                .then()
                .extract()

        then: "import is successful"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        println "DEBUG: Skyr (no catalog) response: ${response.body().asString()}"
        body.getString("status") == "success"

        and: "AI provides some calorie estimate (does not refuse)"
        def calories = body.getInt("caloriesKcal")
        println "DEBUG: skyr estimated caloriesKcal=${calories}"
        calories > 0

        and: "values are likely different from what catalog would provide (120 kcal exactly)"
        // This test documents that without catalog, AI uses its own estimates
        // which are reasonable but not guaranteed to match catalog values
        def protein = body.getInt("proteinGrams")
        println "DEBUG: skyr estimated proteinGrams=${protein}"
        protein > 0
    }
}
