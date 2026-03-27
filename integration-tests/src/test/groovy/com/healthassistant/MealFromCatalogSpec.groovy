package com.healthassistant

import com.healthassistant.mealcatalog.api.dto.SaveProductRequest
import spock.lang.Title

@Title("Feature: Record Meal from Catalog Products")
class MealFromCatalogSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-meal-from-catalog"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String OTHER_DEVICE_ID = "test-device"

    def setup() {
        cleanupMealCatalogForDevice(DEVICE_ID)
        cleanupMealCatalogForDevice(OTHER_DEVICE_ID)
        cleanupEventsForDevice(DEVICE_ID)
    }

    // --- Helper to get product IDs via HTTP catalog browse ---

    private List<Map> browseCatalogProducts() {
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/catalog")
                .get("/v1/meals/catalog")
                .then()
                .extract()
        return response.body().jsonPath().getList("")
    }

    private Long getProductIdByTitle(String title) {
        def products = browseCatalogProducts()
        def product = products.find { it.title == title }
        return product?.id as Long
    }

    def "should create meal from single catalog product with correct macros"() {
        given: "a product in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr pitny z Piatnicy", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))
        def productId = getProductIdByTitle("Skyr pitny z Piatnicy")

        and: "a request to create a meal from that product"
        def request = """
        {
            "products": [
                {"productId": ${productId}}
            ],
            "mealType": "BREAKFAST"
        }
        """

        when: "I create a meal from catalog"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/from-catalog", request)
                .post("/v1/meals/from-catalog")
                .then()
                .extract()

        then: "response status is 201 Created with correct macros"
        response.statusCode() == 201
        def body = response.body().jsonPath()
        body.getInt("caloriesKcal") == 120
        body.getInt("proteinGrams") == 20
        body.getInt("fatGrams") == 2
        body.getInt("carbohydratesGrams") == 6
        body.getString("mealType") == "BREAKFAST"
        body.getString("healthRating") == "HEALTHY"
    }

    def "should sum macros correctly when creating meal from two products"() {
        given: "two products in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr pitny", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Banan", "SNACK", 90, 1, 0, 23, "HEALTHY"
        ))
        def skyrId = getProductIdByTitle("Skyr pitny")
        def bananId = getProductIdByTitle("Banan")

        and: "a request with both products"
        def request = """
        {
            "products": [
                {"productId": ${skyrId}},
                {"productId": ${bananId}}
            ],
            "mealType": "BREAKFAST"
        }
        """

        when: "I create a meal from catalog"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/from-catalog", request)
                .post("/v1/meals/from-catalog")
                .then()
                .extract()

        then: "macros are summed from both products"
        response.statusCode() == 201
        def body = response.body().jsonPath()
        body.getInt("caloriesKcal") == 210   // 120 + 90
        body.getInt("proteinGrams") == 21    // 20 + 1
        body.getInt("fatGrams") == 2         // 2 + 0
        body.getInt("carbohydratesGrams") == 29  // 6 + 23
    }

    def "should halve macros when portionMultiplier is 0.5"() {
        given: "a product in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Ryz brazowy", "LUNCH", 200, 5, 2, 44, "HEALTHY"
        ))
        def productId = getProductIdByTitle("Ryz brazowy")

        and: "a request with portionMultiplier 0.5"
        def request = """
        {
            "products": [
                {"productId": ${productId}, "portionMultiplier": 0.5}
            ],
            "mealType": "LUNCH"
        }
        """

        when: "I create a meal from catalog"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/from-catalog", request)
                .post("/v1/meals/from-catalog")
                .then()
                .extract()

        then: "macros are halved (rounded)"
        response.statusCode() == 201
        def body = response.body().jsonPath()
        body.getInt("caloriesKcal") == 100   // 200 * 0.5
        body.getInt("proteinGrams") == 3     // 5 * 0.5 = 2.5 -> rounded
        body.getInt("fatGrams") == 1         // 2 * 0.5
        body.getInt("carbohydratesGrams") == 22  // 44 * 0.5
    }

    def "should auto-generate title from product names when no title provided"() {
        given: "two products in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr pitny", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Banan", "SNACK", 90, 1, 0, 23, "HEALTHY"
        ))
        def skyrId = getProductIdByTitle("Skyr pitny")
        def bananId = getProductIdByTitle("Banan")

        and: "a request without explicit title"
        def request = """
        {
            "products": [
                {"productId": ${skyrId}},
                {"productId": ${bananId}}
            ],
            "mealType": "BREAKFAST"
        }
        """

        when: "I create a meal from catalog"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/from-catalog", request)
                .post("/v1/meals/from-catalog")
                .then()
                .extract()

        then: "title is auto-generated from product names"
        response.statusCode() == 201
        def title = response.body().jsonPath().getString("title")
        title.contains("Skyr pitny")
        title.contains("Banan")
    }

    def "should use explicit title when provided in request"() {
        given: "a product in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr pitny", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))
        def productId = getProductIdByTitle("Skyr pitny")

        and: "a request with explicit title"
        def request = """
        {
            "products": [
                {"productId": ${productId}}
            ],
            "mealType": "BREAKFAST",
            "title": "My custom breakfast"
        }
        """

        when: "I create a meal from catalog"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/from-catalog", request)
                .post("/v1/meals/from-catalog")
                .then()
                .extract()

        then: "the explicit title is used"
        response.statusCode() == 201
        response.body().jsonPath().getString("title") == "My custom breakfast"
    }

    def "should return 422 when product id does not exist"() {
        given: "a request with non-existent product id"
        def request = """
        {
            "products": [
                {"productId": 999999}
            ],
            "mealType": "BREAKFAST"
        }
        """

        when: "I try to create a meal from catalog"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/from-catalog", request)
                .post("/v1/meals/from-catalog")
                .then()
                .extract()

        then: "response is 422 Unprocessable Entity"
        response.statusCode() == 422
    }

    def "should return 422 when product belongs to another device"() {
        given: "a product belonging to another device"
        mealCatalogFacade.saveProduct(OTHER_DEVICE_ID, new SaveProductRequest(
                "Other device product", "LUNCH", 300, 25, 10, 30, "NEUTRAL"
        ))
        // Get the product id from the other device's catalog via facade
        def otherProducts = mealCatalogFacade.getTopProducts(OTHER_DEVICE_ID, 10)
        def otherProductId = otherProducts[0].id()

        and: "a request referencing that product from my device"
        def request = """
        {
            "products": [
                {"productId": ${otherProductId}}
            ],
            "mealType": "BREAKFAST"
        }
        """

        when: "I try to create a meal from catalog"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/from-catalog", request)
                .post("/v1/meals/from-catalog")
                .then()
                .extract()

        then: "response is 422 Unprocessable Entity (cannot use another device's product)"
        response.statusCode() == 422
    }

    def "should increment usage count after creating meal from catalog product"() {
        given: "a product in the catalog with initial usage count"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr pitny", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))
        def productId = getProductIdByTitle("Skyr pitny")

        and: "initial usage count is 1"
        def initialProducts = browseCatalogProducts()
        def initialUsageCount = initialProducts.find { it.title == "Skyr pitny" }.usageCount as int

        and: "a request to create a meal from that product"
        def request = """
        {
            "products": [
                {"productId": ${productId}}
            ],
            "mealType": "BREAKFAST"
        }
        """

        when: "I create a meal from catalog"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/from-catalog", request)
                .post("/v1/meals/from-catalog")
                .then()
                .extract()

        then: "usage count is incremented"
        def updatedProducts = browseCatalogProducts()
        def updatedUsageCount = updatedProducts.find { it.title == "Skyr pitny" }.usageCount as int
        updatedUsageCount == initialUsageCount + 1
    }
}
