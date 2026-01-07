package com.healthassistant

import spock.lang.Title

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Integration tests for Meal CRUD operations via dedicated meals endpoints
 */
@Title("Feature: Meal CRUD Operations (Create, Update, Delete)")
class MealCrudSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-meal-crud"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def setup() {
        cleanupEventsForDevice(DEVICE_ID)
    }

    // ===================== POST /v1/meals (Create) =====================

    def "Scenario 1: Create meal without occurredAt uses current time"() {
        given: "a valid meal request without occurredAt"
        def request = """
        {
            "title": "Breakfast oatmeal",
            "mealType": "BREAKFAST",
            "caloriesKcal": 350,
            "proteinGrams": 12,
            "fatGrams": 8,
            "carbohydratesGrams": 55,
            "healthRating": "HEALTHY"
        }
        """

        when: "I create the meal"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", request)
                .post("/v1/meals")
                .then()
                .extract()

        then: "response status is 201 Created"
        response.statusCode() == 201

        and: "response contains meal data with eventId"
        def body = response.body().jsonPath()
        body.getString("eventId") != null
        body.getString("title") == "Breakfast oatmeal"
        body.getString("mealType") == "BREAKFAST"
        body.getInt("caloriesKcal") == 350
        body.getInt("proteinGrams") == 12
        body.getInt("fatGrams") == 8
        body.getInt("carbohydratesGrams") == 55
        body.getString("healthRating") == "HEALTHY"
        body.getString("date") != null
        body.getString("occurredAt") != null
    }

    def "Scenario 2: Create meal with backdated occurredAt"() {
        given: "a meal request with occurredAt from yesterday"
        def yesterday = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        def request = """
        {
            "title": "Backdated lunch",
            "mealType": "LUNCH",
            "caloriesKcal": 600,
            "proteinGrams": 35,
            "fatGrams": 20,
            "carbohydratesGrams": 70,
            "healthRating": "NEUTRAL",
            "occurredAt": "${yesterday}"
        }
        """

        when: "I create the meal"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", request)
                .post("/v1/meals")
                .then()
                .extract()

        then: "response status is 201 Created"
        response.statusCode() == 201

        and: "meal is created with the backdated timestamp"
        def body = response.body().jsonPath()
        body.getString("eventId") != null
        body.getString("title") == "Backdated lunch"
    }

    def "Scenario 3: Create meal fails with occurredAt more than 30 days ago"() {
        given: "a meal request with occurredAt more than 30 days ago"
        def tooOld = Instant.now().minus(31, ChronoUnit.DAYS).toString()
        def request = """
        {
            "title": "Too old meal",
            "mealType": "DINNER",
            "caloriesKcal": 500,
            "proteinGrams": 30,
            "fatGrams": 15,
            "carbohydratesGrams": 50,
            "healthRating": "HEALTHY",
            "occurredAt": "${tooOld}"
        }
        """

        when: "I try to create the meal"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", request)
                .post("/v1/meals")
                .then()
                .extract()

        then: "response status is 400 Bad Request"
        response.statusCode() == 400
    }

    def "Scenario 4: Create meal fails with future occurredAt"() {
        given: "a meal request with occurredAt in the future"
        def future = Instant.now().plus(1, ChronoUnit.HOURS).toString()
        def request = """
        {
            "title": "Future meal",
            "mealType": "SNACK",
            "caloriesKcal": 200,
            "proteinGrams": 10,
            "fatGrams": 5,
            "carbohydratesGrams": 25,
            "healthRating": "NEUTRAL",
            "occurredAt": "${future}"
        }
        """

        when: "I try to create the meal"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", request)
                .post("/v1/meals")
                .then()
                .extract()

        then: "response status is 400 Bad Request"
        response.statusCode() == 400
    }

    def "Scenario 5: Create meal fails with missing required fields"() {
        given: "a meal request without title"
        def request = """
        {
            "mealType": "BREAKFAST",
            "caloriesKcal": 350,
            "proteinGrams": 12,
            "fatGrams": 8,
            "carbohydratesGrams": 55,
            "healthRating": "HEALTHY"
        }
        """

        when: "I try to create the meal"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", request)
                .post("/v1/meals")
                .then()
                .extract()

        then: "response status is 400 Bad Request"
        response.statusCode() == 400
    }

    // ===================== DELETE /v1/meals/{eventId} =====================

    def "Scenario 6: Delete existing meal returns 204"() {
        given: "an existing meal"
        def createRequest = """
        {
            "title": "Meal to delete",
            "mealType": "SNACK",
            "caloriesKcal": 150,
            "proteinGrams": 5,
            "fatGrams": 3,
            "carbohydratesGrams": 20,
            "healthRating": "NEUTRAL"
        }
        """
        def createResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", createRequest)
                .post("/v1/meals")
                .then()
                .statusCode(201)
                .extract()
        def eventId = createResponse.body().jsonPath().getString("eventId")

        when: "I delete the meal"
        def deleteResponse = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/${eventId}")
                .delete("/v1/meals/${eventId}")
                .then()
                .extract()

        then: "response status is 204 No Content"
        deleteResponse.statusCode() == 204
    }

    def "Scenario 7: Delete non-existing meal returns 404"() {
        when: "I try to delete a non-existing meal"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/non-existing-id")
                .delete("/v1/meals/non-existing-id")
                .then()
                .extract()

        then: "response status is 404 Not Found"
        response.statusCode() == 404
    }

    def "Scenario 8: Delete meal from different device returns 404"() {
        given: "an existing meal created by a different device"
        // Create meal with one device
        def createRequest = """
        {
            "title": "Other device meal",
            "mealType": "LUNCH",
            "caloriesKcal": 400,
            "proteinGrams": 20,
            "fatGrams": 10,
            "carbohydratesGrams": 45,
            "healthRating": "HEALTHY"
        }
        """
        def createResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", createRequest)
                .post("/v1/meals")
                .then()
                .statusCode(201)
                .extract()
        def eventId = createResponse.body().jsonPath().getString("eventId")

        when: "I try to delete the meal from a different device"
        // Use different-device-id which has a different secret
        def response = authenticatedDeleteRequest("different-device-id", "ZGlmZmVyZW50LXNlY3JldC0xMjM=", "/v1/meals/${eventId}")
                .delete("/v1/meals/${eventId}")
                .then()
                .extract()

        then: "response status is 404 Not Found (meal not visible to other device)"
        response.statusCode() == 404
    }

    // ===================== PUT /v1/meals/{eventId} =====================

    def "Scenario 9: Update existing meal returns 200 with updated data"() {
        given: "an existing meal"
        def createRequest = """
        {
            "title": "Original meal",
            "mealType": "BREAKFAST",
            "caloriesKcal": 300,
            "proteinGrams": 10,
            "fatGrams": 8,
            "carbohydratesGrams": 40,
            "healthRating": "HEALTHY"
        }
        """
        def createResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", createRequest)
                .post("/v1/meals")
                .then()
                .statusCode(201)
                .extract()
        def eventId = createResponse.body().jsonPath().getString("eventId")

        and: "an update request with new values"
        def updateRequest = """
        {
            "title": "Updated meal",
            "mealType": "BRUNCH",
            "caloriesKcal": 500,
            "proteinGrams": 25,
            "fatGrams": 15,
            "carbohydratesGrams": 60,
            "healthRating": "VERY_HEALTHY"
        }
        """

        when: "I update the meal"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/${eventId}", updateRequest)
                .put("/v1/meals/${eventId}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains updated data"
        def body = response.body().jsonPath()
        body.getString("eventId") != null
        body.getString("title") == "Updated meal"
        body.getString("mealType") == "BRUNCH"
        body.getInt("caloriesKcal") == 500
        body.getInt("proteinGrams") == 25
        body.getInt("fatGrams") == 15
        body.getInt("carbohydratesGrams") == 60
        body.getString("healthRating") == "VERY_HEALTHY"
    }

    def "Scenario 10: Update meal with new occurredAt"() {
        given: "an existing meal"
        def createRequest = """
        {
            "title": "Meal to move",
            "mealType": "DINNER",
            "caloriesKcal": 700,
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbohydratesGrams": 80,
            "healthRating": "NEUTRAL"
        }
        """
        def createResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", createRequest)
                .post("/v1/meals")
                .then()
                .statusCode(201)
                .extract()
        def eventId = createResponse.body().jsonPath().getString("eventId")

        and: "an update request with different occurredAt"
        def newOccurredAt = Instant.now().minus(2, ChronoUnit.DAYS).toString()
        def updateRequest = """
        {
            "title": "Meal to move",
            "mealType": "DINNER",
            "caloriesKcal": 700,
            "proteinGrams": 40,
            "fatGrams": 25,
            "carbohydratesGrams": 80,
            "healthRating": "NEUTRAL",
            "occurredAt": "${newOccurredAt}"
        }
        """

        when: "I update the meal"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/${eventId}", updateRequest)
                .put("/v1/meals/${eventId}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains the new occurredAt"
        def body = response.body().jsonPath()
        body.getString("eventId") != null
    }

    def "Scenario 11: Update non-existing meal returns 404"() {
        given: "an update request"
        def updateRequest = """
        {
            "title": "Ghost meal",
            "mealType": "SNACK",
            "caloriesKcal": 100,
            "proteinGrams": 5,
            "fatGrams": 2,
            "carbohydratesGrams": 15,
            "healthRating": "HEALTHY"
        }
        """

        when: "I try to update a non-existing meal"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/non-existing-id", updateRequest)
                .put("/v1/meals/non-existing-id")
                .then()
                .extract()

        then: "response status is 404 Not Found"
        response.statusCode() == 404
    }

    def "Scenario 12: Update meal with occurredAt more than 30 days ago fails"() {
        given: "an existing meal"
        def createRequest = """
        {
            "title": "Recent meal",
            "mealType": "LUNCH",
            "caloriesKcal": 400,
            "proteinGrams": 20,
            "fatGrams": 10,
            "carbohydratesGrams": 50,
            "healthRating": "HEALTHY"
        }
        """
        def createResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", createRequest)
                .post("/v1/meals")
                .then()
                .statusCode(201)
                .extract()
        def eventId = createResponse.body().jsonPath().getString("eventId")

        and: "an update request with occurredAt more than 30 days ago"
        def tooOld = Instant.now().minus(31, ChronoUnit.DAYS).toString()
        def updateRequest = """
        {
            "title": "Recent meal",
            "mealType": "LUNCH",
            "caloriesKcal": 400,
            "proteinGrams": 20,
            "fatGrams": 10,
            "carbohydratesGrams": 50,
            "healthRating": "HEALTHY",
            "occurredAt": "${tooOld}"
        }
        """

        when: "I try to update the meal with too old date"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/${eventId}", updateRequest)
                .put("/v1/meals/${eventId}")
                .then()
                .extract()

        then: "response status is 400 Bad Request"
        response.statusCode() == 400
    }

    // ===================== E2E Flow =====================

    def "Scenario 13: Full CRUD flow - create, update, then delete using original eventId"() {
        given: "a new meal request"
        def createRequest = """
        {
            "title": "Full flow meal",
            "mealType": "BREAKFAST",
            "caloriesKcal": 400,
            "proteinGrams": 15,
            "fatGrams": 12,
            "carbohydratesGrams": 55,
            "healthRating": "HEALTHY"
        }
        """

        when: "I create the meal"
        def createResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", createRequest)
                .post("/v1/meals")
                .then()
                .extract()

        then: "meal is created"
        createResponse.statusCode() == 201
        def eventId = createResponse.body().jsonPath().getString("eventId")
        eventId != null

        when: "I delete the meal using the original event ID"
        def deleteResponse = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/${eventId}")
                .delete("/v1/meals/${eventId}")
                .then()
                .extract()

        then: "meal is deleted"
        deleteResponse.statusCode() == 204
    }

    def "Scenario 13b: Create and update meal works"() {
        given: "a new meal request"
        def createRequest = """
        {
            "title": "Meal for update test",
            "mealType": "BREAKFAST",
            "caloriesKcal": 400,
            "proteinGrams": 15,
            "fatGrams": 12,
            "carbohydratesGrams": 55,
            "healthRating": "HEALTHY"
        }
        """

        when: "I create the meal"
        def createResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", createRequest)
                .post("/v1/meals")
                .then()
                .extract()

        then: "meal is created"
        createResponse.statusCode() == 201
        def eventId = createResponse.body().jsonPath().getString("eventId")
        eventId != null

        when: "I update the meal"
        def updateRequest = """
        {
            "title": "Updated meal",
            "mealType": "BRUNCH",
            "caloriesKcal": 450,
            "proteinGrams": 18,
            "fatGrams": 14,
            "carbohydratesGrams": 60,
            "healthRating": "VERY_HEALTHY"
        }
        """
        def updateResponse = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals/${eventId}", updateRequest)
                .put("/v1/meals/${eventId}")
                .then()
                .extract()

        then: "meal is updated"
        updateResponse.statusCode() == 200
        updateResponse.body().jsonPath().getString("title") == "Updated meal"
        updateResponse.body().jsonPath().getString("mealType") == "BRUNCH"
    }

    def "Scenario 14: Backdated meal appears in daily summary"() {
        given: "a backdated meal from 5 days ago"
        def fiveDaysAgo = Instant.now().minus(5, ChronoUnit.DAYS)
        def createRequest = """
        {
            "title": "Backdated dinner",
            "mealType": "DINNER",
            "caloriesKcal": 800,
            "proteinGrams": 45,
            "fatGrams": 30,
            "carbohydratesGrams": 90,
            "healthRating": "NEUTRAL",
            "occurredAt": "${fiveDaysAgo}"
        }
        """

        when: "I create the backdated meal"
        def createResponse = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", createRequest)
                .post("/v1/meals")
                .then()
                .extract()

        then: "meal is created successfully"
        createResponse.statusCode() == 201
        def body = createResponse.body().jsonPath()
        body.getString("title") == "Backdated dinner"
        body.getInt("caloriesKcal") == 800
    }

    def "Scenario 15: Create meal with all valid meal types"() {
        given: "requests for all meal types"
        def mealTypes = ["BREAKFAST", "BRUNCH", "LUNCH", "DINNER", "SNACK", "DESSERT", "DRINK"]

        when: "I create meals for each type"
        def responses = mealTypes.collect { type ->
            def request = """
            {
                "title": "Test ${type}",
                "mealType": "${type}",
                "caloriesKcal": 300,
                "proteinGrams": 10,
                "fatGrams": 10,
                "carbohydratesGrams": 40,
                "healthRating": "NEUTRAL"
            }
            """
            authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", request)
                    .post("/v1/meals")
                    .then()
                    .extract()
        }

        then: "all meals are created successfully"
        responses.every { it.statusCode() == 201 }
        responses.collect { it.body().jsonPath().getString("mealType") }.containsAll(mealTypes)
    }

    def "Scenario 16: Create meal with all valid health ratings"() {
        given: "requests for all health ratings"
        def healthRatings = ["VERY_HEALTHY", "HEALTHY", "NEUTRAL", "UNHEALTHY", "VERY_UNHEALTHY"]

        when: "I create meals for each rating"
        def responses = healthRatings.collect { rating ->
            def request = """
            {
                "title": "Test ${rating}",
                "mealType": "SNACK",
                "caloriesKcal": 200,
                "proteinGrams": 8,
                "fatGrams": 5,
                "carbohydratesGrams": 25,
                "healthRating": "${rating}"
            }
            """
            authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/meals", request)
                    .post("/v1/meals")
                    .then()
                    .extract()
        }

        then: "all meals are created successfully"
        responses.every { it.statusCode() == 201 }
        responses.collect { it.body().jsonPath().getString("healthRating") }.containsAll(healthRatings)
    }
}
