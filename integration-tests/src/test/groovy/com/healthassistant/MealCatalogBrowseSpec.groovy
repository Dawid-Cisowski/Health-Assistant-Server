package com.healthassistant

import com.healthassistant.mealcatalog.api.dto.SaveProductRequest
import spock.lang.Title

@Title("Feature: Browse Meal Catalog via HTTP API")
class MealCatalogBrowseSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-catalog-browse"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    private static final String OTHER_DEVICE_ID = "test-device"

    def setup() {
        cleanupMealCatalogForDevice(DEVICE_ID)
        cleanupMealCatalogForDevice(OTHER_DEVICE_ID)
    }

    def "should return empty list when catalog has no products"() {
        when: "I browse the catalog"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/catalog")
                .get("/v1/meals/catalog")
                .then()
                .extract()

        then: "response is 200 with an empty list"
        response.statusCode() == 200
        response.body().jsonPath().getList("") != null
        response.body().jsonPath().getList("").isEmpty()
    }

    def "should return product with id field when one product exists"() {
        given: "one product saved in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr pitny z Piatnicy", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))

        when: "I browse the catalog"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/catalog")
                .get("/v1/meals/catalog")
                .then()
                .extract()

        then: "the product is returned with all fields including id"
        response.statusCode() == 200
        def products = response.body().jsonPath().getList("")
        products.size() == 1
        response.body().jsonPath().getLong("[0].id") != null
        response.body().jsonPath().getString("[0].title") == "Skyr pitny z Piatnicy"
        response.body().jsonPath().getString("[0].mealType") == "SNACK"
        response.body().jsonPath().getInt("[0].caloriesKcal") == 120
        response.body().jsonPath().getInt("[0].proteinGrams") == 20
        response.body().jsonPath().getInt("[0].fatGrams") == 2
        response.body().jsonPath().getInt("[0].carbohydratesGrams") == 6
        response.body().jsonPath().getString("[0].healthRating") == "HEALTHY"
        response.body().jsonPath().getInt("[0].usageCount") == 1
    }

    def "should order products by usage count descending when sort is USAGE"() {
        given: "products with different usage counts"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Low usage product", "LUNCH", 300, 25, 10, 30, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "High usage product", "DINNER", 500, 35, 15, 50, "NEUTRAL"
        ))
        // Increment high usage to 3
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "High usage product", "DINNER", 500, 35, 15, 50, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "High usage product", "DINNER", 500, 35, 15, 50, "NEUTRAL"
        ))

        when: "I browse with sort=USAGE"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/catalog?sort=USAGE")
                .get("/v1/meals/catalog?sort=USAGE")
                .then()
                .extract()

        then: "products are ordered by usage count descending"
        response.statusCode() == 200
        def products = response.body().jsonPath().getList("")
        products.size() == 2
        response.body().jsonPath().getString("[0].title") == "High usage product"
        response.body().jsonPath().getInt("[0].usageCount") == 3
        response.body().jsonPath().getString("[1].title") == "Low usage product"
        response.body().jsonPath().getInt("[1].usageCount") == 1
    }

    def "should order products alphabetically by title when sort is ALPHABETICAL"() {
        given: "products with titles in non-alphabetical order"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Chleb razowy", "BREAKFAST", 200, 8, 2, 40, "HEALTHY"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Awokado", "LUNCH", 160, 2, 15, 9, "VERY_HEALTHY"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Banan", "SNACK", 90, 1, 0, 23, "HEALTHY"
        ))

        when: "I browse with sort=ALPHABETICAL"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/catalog?sort=ALPHABETICAL")
                .get("/v1/meals/catalog?sort=ALPHABETICAL")
                .then()
                .extract()

        then: "products are ordered alphabetically by title"
        response.statusCode() == 200
        def products = response.body().jsonPath().getList("")
        products.size() == 3
        response.body().jsonPath().getString("[0].title") == "Awokado"
        response.body().jsonPath().getString("[1].title") == "Banan"
        response.body().jsonPath().getString("[2].title") == "Chleb razowy"
    }

    def "should filter products matching query when q parameter is provided"() {
        given: "multiple products in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr pitny z Piatnicy", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Jogurt naturalny", "SNACK", 80, 5, 3, 8, "HEALTHY"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr naturalny", "SNACK", 100, 18, 0, 4, "HEALTHY"
        ))

        when: "I search with q=sky"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/catalog?q=sky")
                .get("/v1/meals/catalog?q=sky")
                .then()
                .extract()

        then: "only products matching 'sky' are returned"
        response.statusCode() == 200
        def products = response.body().jsonPath().getList("")
        products.size() == 2
        products.every { it.title.toLowerCase().contains("sky") || it.title.toLowerCase().contains("skyr") }
    }

    def "should return all products when q parameter is blank"() {
        given: "multiple products in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product A", "LUNCH", 300, 25, 10, 30, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product B", "DINNER", 500, 35, 15, 50, "NEUTRAL"
        ))

        when: "I browse with empty q parameter"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/catalog?q=")
                .get("/v1/meals/catalog?q=")
                .then()
                .extract()

        then: "all products are returned"
        response.statusCode() == 200
        response.body().jsonPath().getList("").size() == 2
    }

    def "should limit results when limit parameter is provided"() {
        given: "three products in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product A", "LUNCH", 300, 25, 10, 30, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product B", "DINNER", 500, 35, 15, 50, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product C", "SNACK", 100, 5, 2, 10, "HEALTHY"
        ))

        when: "I browse with limit=2"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/catalog?limit=2")
                .get("/v1/meals/catalog?limit=2")
                .then()
                .extract()

        then: "at most 2 results are returned"
        response.statusCode() == 200
        response.body().jsonPath().getList("").size() == 2
    }

    def "should not return products belonging to another device"() {
        given: "products for two different devices"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "My product", "LUNCH", 300, 25, 10, 30, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(OTHER_DEVICE_ID, new SaveProductRequest(
                "Other device product", "DINNER", 500, 35, 15, 50, "NEUTRAL"
        ))

        when: "I browse my catalog"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/catalog")
                .get("/v1/meals/catalog")
                .then()
                .extract()

        then: "only my products are returned"
        response.statusCode() == 200
        def products = response.body().jsonPath().getList("")
        products.size() == 1
        response.body().jsonPath().getString("[0].title") == "My product"
    }
}
