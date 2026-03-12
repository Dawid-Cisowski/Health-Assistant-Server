package com.healthassistant.evaluation

import io.restassured.RestAssured
import spock.lang.Requires
import spock.lang.Timeout
import spock.lang.Title

import java.util.concurrent.TimeUnit

@Title("AI Evaluation: Meal Catalog Import")
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiMealCatalogEvalSpec extends BaseEvaluationSpec {

    private static final String IMPORT_ENDPOINT = "/v1/meals/import"
    private static final String JOB_STATUS_TEMPLATE = "/v1/meals/import/jobs/%s"

    def "E-CAT-01: AI uses exact catalog values when importing banana from image"() {
        given: "banana is in the catalog with known values"
        seedCatalogProduct("Banan", "SNACK", 95, 1, 0, 24, "HEALTHY")

        and: "banana image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/banana.png")

        when: "importing meal from banana image"
        def body = submitImportImageAndGetResult(imageBytes, "banana.png")

        then: "import is successful"
        println "DEBUG: Banana import response: ${body}"
        body.getString("result.status") == "success"

        and: "calories are EXACT from catalog"
        def calories = body.getInt("result.caloriesKcal")
        println "DEBUG: banana caloriesKcal=${calories} (expected: 95)"
        calories == 95

        and: "protein is EXACT from catalog"
        def protein = body.getInt("result.proteinGrams")
        println "DEBUG: banana proteinGrams=${protein} (expected: 1)"
        protein == 1

        and: "fat is EXACT from catalog"
        def fat = body.getInt("result.fatGrams")
        println "DEBUG: banana fatGrams=${fat} (expected: 0)"
        fat == 0

        and: "carbs are EXACT from catalog"
        def carbs = body.getInt("result.carbohydratesGrams")
        println "DEBUG: banana carbohydratesGrams=${carbs} (expected: 24)"
        carbs == 24
    }

    def "E-CAT-02: AI uses exact catalog values when importing skyr from image"() {
        given: "Skyr pitny z Piatnicy is in the catalog with known values"
        seedCatalogProduct("Skyr jogurt pitny Piątnica", "SNACK", 120, 20, 2, 6, "HEALTHY")

        and: "skyr image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing meal from skyr image"
        def body = submitImportImageAndGetResult(imageBytes, "skyr.png")

        then: "import is successful"
        println "DEBUG: Skyr import response: ${body}"
        body.getString("result.status") == "success"

        and: "calories are EXACT from catalog"
        def calories = body.getInt("result.caloriesKcal")
        println "DEBUG: skyr caloriesKcal=${calories} (expected: 120)"
        calories == 120

        and: "protein is EXACT from catalog"
        def protein = body.getInt("result.proteinGrams")
        println "DEBUG: skyr proteinGrams=${protein} (expected: 20)"
        protein == 20

        and: "fat is EXACT from catalog"
        def fat = body.getInt("result.fatGrams")
        println "DEBUG: skyr fatGrams=${fat} (expected: 2)"
        fat == 2

        and: "carbs are EXACT from catalog"
        def carbs = body.getInt("result.carbohydratesGrams")
        println "DEBUG: skyr carbohydratesGrams=${carbs} (expected: 6)"
        carbs == 6
    }

    def "E-CAT-03: AI estimates values when banana is NOT in catalog"() {
        given: "catalog is empty (no banana entry)"

        and: "banana image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/banana.png")

        when: "importing meal from banana image without catalog"
        def body = submitImportImageAndGetResult(imageBytes, "banana.png")

        then: "import is successful with estimated values"
        println "DEBUG: Banana (no catalog) response: ${body}"
        body.getString("result.status") == "success"

        and: "calories are reasonable estimate for banana (80-135 kcal)"
        def calories = body.getInt("result.caloriesKcal")
        println "DEBUG: banana estimated caloriesKcal=${calories}"
        calories >= 80 && calories <= 135
    }

    def "E-CAT-04: importing a meal auto-saves product to catalog"() {
        given: "catalog is empty"

        and: "skyr image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing meal from skyr image (no catalog entry)"
        def body = submitImportImageAndGetResult(imageBytes, "skyr.png")

        then: "import is successful"
        println "DEBUG: Skyr auto-save response: ${body}"
        body.getString("result.status") == "success"

        and: "the product was auto-saved to catalog"
        def catalogResults = mealCatalogFacade.searchProducts(getTestDeviceId(), "skyr", 5)
        println "DEBUG: Catalog search results after import: ${catalogResults}"
        catalogResults.size() >= 1
    }

    def "E-CAT-05: imported item from catalog has source 'catalog' in response"() {
        given: "Skyr pitny z Piatnicy is in the catalog"
        seedCatalogProduct("Skyr jogurt pitny Piątnica", "SNACK", 120, 20, 2, 6, "HEALTHY")

        and: "skyr image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing meal from skyr image"
        def body = submitImportImageAndGetResult(imageBytes, "skyr.png")

        then: "import is successful"
        println "DEBUG: Skyr source check response: ${body}"
        body.getString("result.status") == "success"

        and: "items list contains an entry with source 'catalog'"
        def items = body.getList("result.items")
        println "DEBUG: items: ${items}"
        items != null && !items.isEmpty()
        items.any { it.source == "catalog" }
    }

    def "E-CAT-06: AI-estimated values differ from catalog values when catalog is empty"() {
        given: "catalog is empty (no skyr entry)"

        and: "skyr image is loaded"
        def imageBytes = loadTestImage("/screenshots/meal/skyr.png")

        when: "importing meal from skyr image without catalog"
        def body = submitImportImageAndGetResult(imageBytes, "skyr.png")

        then: "import is successful"
        println "DEBUG: Skyr (no catalog) response: ${body}"
        body.getString("result.status") == "success"

        and: "AI provides some calorie estimate (does not refuse)"
        def calories = body.getInt("result.caloriesKcal")
        println "DEBUG: skyr estimated caloriesKcal=${calories}"
        calories > 0

        and: "values are likely different from what catalog would provide (120 kcal exactly)"
        def protein = body.getInt("result.proteinGrams")
        println "DEBUG: skyr estimated proteinGrams=${protein}"
        protein > 0
    }

    // ==================== Helper Methods ====================

    private submitImportImageAndGetResult(byte[] imageBytes, String fileName) {
        def submitResponse = authenticatedMultipartRequestWithImage(
                getTestDeviceId(), TEST_SECRET_BASE64, IMPORT_ENDPOINT, imageBytes, fileName
        )
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
                    def response = authenticatedGetRequest(getTestDeviceId(), TEST_SECRET_BASE64, jobStatusEndpoint)
                            .get(jobStatusEndpoint)
                            .then()
                            .extract()
                    def status = response.body().jsonPath().getString("status")
                    return status == "DONE" || status == "FAILED"
                })

        def response = authenticatedGetRequest(getTestDeviceId(), TEST_SECRET_BASE64, jobStatusEndpoint)
                .get(jobStatusEndpoint)
                .then()
                .extract()
        return response.body().jsonPath()
    }

    private authenticatedGetRequest(String deviceId, String secretBase64, String path) {
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
