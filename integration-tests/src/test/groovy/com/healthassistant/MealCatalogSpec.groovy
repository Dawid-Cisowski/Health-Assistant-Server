package com.healthassistant

import com.healthassistant.mealcatalog.api.dto.SaveProductRequest
import spock.lang.Title

@Title("Feature: Meal Catalog - Personal Product Catalog")
class MealCatalogSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-meal-catalog"
    private static final String OTHER_DEVICE_ID = "test-device"

    def setup() {
        cleanupMealCatalogForDevice(DEVICE_ID)
        cleanupMealCatalogForDevice(OTHER_DEVICE_ID)
    }

    def "Scenario 1: Save new product and find it by search"() {
        given: "a new product to save"
        def request = new SaveProductRequest(
                "Skyr pitny z Piątnicy",
                "SNACK",
                120, 20, 2, 6,
                "HEALTHY"
        )

        when: "I save the product to catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, request)

        and: "I search for it"
        def results = mealCatalogFacade.searchProducts(DEVICE_ID, "Skyr", 10)

        then: "the product is found with correct values"
        results.size() == 1
        results[0].title() == "Skyr pitny z Piątnicy"
        results[0].mealType() == "SNACK"
        results[0].caloriesKcal() == 120
        results[0].proteinGrams() == 20
        results[0].fatGrams() == 2
        results[0].carbohydratesGrams() == 6
        results[0].healthRating() == "HEALTHY"
        results[0].usageCount() == 1
        results[0].lastUsedAt() != null
    }

    def "Scenario 2: Save duplicate product increments usage count and updates values"() {
        given: "a product already in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Baton proteinowy Olimp",
                "SNACK",
                200, 20, 8, 18,
                "NEUTRAL"
        ))

        when: "I save the same product again with updated values"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Baton proteinowy Olimp",
                "SNACK",
                210, 22, 7, 19,
                "HEALTHY"
        ))

        and: "I search for it"
        def results = mealCatalogFacade.searchProducts(DEVICE_ID, "Baton proteinowy", 10)

        then: "usage count is incremented and values are updated"
        results.size() == 1
        results[0].usageCount() == 2
        results[0].caloriesKcal() == 210
        results[0].proteinGrams() == 22
        results[0].fatGrams() == 7
        results[0].carbohydratesGrams() == 19
        results[0].healthRating() == "HEALTHY"
    }

    def "Scenario 3: Search returns results ordered by usage count DESC"() {
        given: "multiple products with different usage counts"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product A", "LUNCH", 300, 25, 10, 30, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product B", "LUNCH", 400, 30, 15, 40, "NEUTRAL"
        ))
        // Save Product B twice more to increase usage count
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product B", "LUNCH", 400, 30, 15, 40, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product B", "LUNCH", 400, 30, 15, 40, "NEUTRAL"
        ))

        when: "I search for 'Product'"
        def results = mealCatalogFacade.searchProducts(DEVICE_ID, "Product", 10)

        then: "Product B (usage count 3) comes before Product A (usage count 1)"
        results.size() == 2
        results[0].title() == "Product B"
        results[0].usageCount() == 3
        results[1].title() == "Product A"
        results[1].usageCount() == 1
    }

    def "Scenario 4: Search is case-insensitive and supports partial match"() {
        given: "a product in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Jogurt naturalny Danone", "SNACK", 80, 5, 3, 8, "HEALTHY"
        ))

        when: "I search with different cases and partial text"
        def resultsLower = mealCatalogFacade.searchProducts(DEVICE_ID, "jogurt", 10)
        def resultsUpper = mealCatalogFacade.searchProducts(DEVICE_ID, "JOGURT", 10)
        def resultsPartial = mealCatalogFacade.searchProducts(DEVICE_ID, "danone", 10)

        then: "all searches find the product"
        resultsLower.size() == 1
        resultsUpper.size() == 1
        resultsPartial.size() == 1
    }

    def "Scenario 5: Empty result when nothing matches"() {
        given: "a product in the catalog"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr pitny z Piątnicy", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))

        when: "I search for something that doesn't exist"
        def results = mealCatalogFacade.searchProducts(DEVICE_ID, "pizza", 10)

        then: "no results are returned"
        results.isEmpty()
    }

    def "Scenario 6: Products are isolated per device_id"() {
        given: "products for two different devices"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Product for device 1", "LUNCH", 300, 25, 10, 30, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(OTHER_DEVICE_ID, new SaveProductRequest(
                "Product for device 2", "DINNER", 500, 35, 20, 50, "NEUTRAL"
        ))

        when: "I search from device 1"
        def results1 = mealCatalogFacade.searchProducts(DEVICE_ID, "Product", 10)

        and: "I search from device 2"
        def results2 = mealCatalogFacade.searchProducts(OTHER_DEVICE_ID, "Product", 10)

        then: "each device only sees its own products"
        results1.size() == 1
        results1[0].title() == "Product for device 1"
        results2.size() == 1
        results2[0].title() == "Product for device 2"
    }

    def "Scenario 7: getTopProducts returns most used products, capped at limit"() {
        given: "several products with varying usage counts"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Low usage", "SNACK", 100, 5, 2, 10, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Medium usage", "LUNCH", 400, 30, 15, 40, "NEUTRAL"
        ))
        // Increment medium usage to 2
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Medium usage", "LUNCH", 400, 30, 15, 40, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "High usage", "DINNER", 600, 40, 20, 60, "NEUTRAL"
        ))
        // Increment high usage to 3
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "High usage", "DINNER", 600, 40, 20, 60, "NEUTRAL"
        ))
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "High usage", "DINNER", 600, 40, 20, 60, "NEUTRAL"
        ))

        when: "I get top 2 products"
        def results = mealCatalogFacade.getTopProducts(DEVICE_ID, 2)

        then: "only top 2 by usage count are returned"
        results.size() == 2
        results[0].title() == "High usage"
        results[0].usageCount() == 3
        results[1].title() == "Medium usage"
        results[1].usageCount() == 2
    }

    def "Scenario 8: Saving product with blank title is silently ignored"() {
        when: "I save a product with blank title"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "", "SNACK", 100, 5, 2, 10, "NEUTRAL"
        ))

        and: "I save a product with null title"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                null, "SNACK", 100, 5, 2, 10, "NEUTRAL"
        ))

        then: "no products are in the catalog"
        mealCatalogFacade.getTopProducts(DEVICE_ID, 10).isEmpty()
    }

    def "Scenario 9: Duplicate detection is case-insensitive via normalized title"() {
        given: "a product saved with mixed case"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "Skyr Pitny z Piątnicy", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))

        when: "I save the same product with different casing"
        mealCatalogFacade.saveProduct(DEVICE_ID, new SaveProductRequest(
                "skyr pitny z piątnicy", "SNACK", 120, 20, 2, 6, "HEALTHY"
        ))

        then: "usage count is incremented (not a new product)"
        def results = mealCatalogFacade.searchProducts(DEVICE_ID, "skyr", 10)
        results.size() == 1
        results[0].usageCount() == 2
    }
}
