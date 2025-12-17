package com.healthassistant

import com.healthassistant.meals.api.MealsFacade
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

/**
 * Integration tests for Meal Projections
 */
@Title("Feature: Meal Projections and Query API")
class MealProjectionSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-device"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    @Autowired
    MealsFacade mealsFacade

    def setup() {
        mealsFacade?.deleteAllProjections()
    }

    def "Scenario 1: MealRecorded event creates meal and daily projections"() {
        given: "a meal event"
        def date = "2025-11-20"
        def occurredAt = "2025-11-20T12:30:00Z"
        def request = """
        {
            "events": [{
                "idempotencyKey": "test-device|meal|${occurredAt}",
                "type": "MealRecorded.v1",
                "occurredAt": "${occurredAt}",
                "payload": {
                    "title": "Grilled chicken salad",
                    "mealType": "LUNCH",
                    "caloriesKcal": 450,
                    "proteinGrams": 35,
                    "fatGrams": 15,
                    "carbohydratesGrams": 30,
                    "healthRating": "HEALTHY"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit the meal event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projections are created (verified via API)"
        def response = waitForApiResponse("/v1/meals/daily/${date}")
        def meals = response.getList("meals")
        meals.size() == 1
        meals[0].mealNumber == 1
        meals[0].title == "Grilled chicken salad"
        meals[0].mealType == "LUNCH"
        meals[0].caloriesKcal == 450
        meals[0].proteinGrams == 35
        meals[0].fatGrams == 15
        meals[0].carbohydratesGrams == 30
        meals[0].healthRating == "HEALTHY"

        and: "daily projection is created with totals"
        response.getInt("totalMealCount") == 1
        response.getInt("totalCaloriesKcal") == 450
        response.getInt("totalProteinGrams") == 35
        response.getInt("totalFatGrams") == 15
        response.getInt("totalCarbohydratesGrams") == 30
        response.getInt("mealTypeCounts.lunch") == 1
        response.getInt("healthRatingCounts.healthy") == 1
    }

    def "Scenario 2: Multiple meals in same day are tracked with meal_number"() {
        given: "three meals on the same day"
        def date = "2025-11-21"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|meal|breakfast",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-21T07:30:00Z",
                    "payload": {
                        "title": "Oatmeal with berries",
                        "mealType": "BREAKFAST",
                        "caloriesKcal": 350,
                        "proteinGrams": 12,
                        "fatGrams": 8,
                        "carbohydratesGrams": 55,
                        "healthRating": "VERY_HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|lunch",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-21T12:30:00Z",
                    "payload": {
                        "title": "Turkey sandwich",
                        "mealType": "LUNCH",
                        "caloriesKcal": 500,
                        "proteinGrams": 30,
                        "fatGrams": 18,
                        "carbohydratesGrams": 45,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|dinner",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-21T19:00:00Z",
                    "payload": {
                        "title": "Grilled salmon with vegetables",
                        "mealType": "DINNER",
                        "caloriesKcal": 600,
                        "proteinGrams": 40,
                        "fatGrams": 25,
                        "carbohydratesGrams": 35,
                        "healthRating": "HEALTHY"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit all three meal events"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "three meal projections are created with sequential numbers (verified via API)"
        def response = waitForApiResponse("/v1/meals/daily/${date}")
        def meals = response.getList("meals")
        meals.size() == 3
        meals[0].mealNumber == 1
        meals[0].mealType == "BREAKFAST"
        meals[1].mealNumber == 2
        meals[1].mealType == "LUNCH"
        meals[2].mealNumber == 3
        meals[2].mealType == "DINNER"

        and: "daily projection aggregates all meals"
        response.getInt("totalMealCount") == 3
        response.getInt("mealTypeCounts.breakfast") == 1
        response.getInt("mealTypeCounts.lunch") == 1
        response.getInt("mealTypeCounts.dinner") == 1
    }

    def "Scenario 3: Daily projection aggregates macros correctly"() {
        given: "two meals with different macros"
        def date = "2025-11-22"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|meal|meal1",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-22T08:00:00Z",
                    "payload": {
                        "title": "Eggs and toast",
                        "mealType": "BREAKFAST",
                        "caloriesKcal": 400,
                        "proteinGrams": 20,
                        "fatGrams": 15,
                        "carbohydratesGrams": 40,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|meal2",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-22T13:00:00Z",
                    "payload": {
                        "title": "Pasta with meatballs",
                        "mealType": "LUNCH",
                        "caloriesKcal": 700,
                        "proteinGrams": 35,
                        "fatGrams": 25,
                        "carbohydratesGrams": 80,
                        "healthRating": "NEUTRAL"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit both meals"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "daily projection has correct macro totals (verified via API)"
        def response = waitForApiResponse("/v1/meals/daily/${date}")
        response.getInt("totalCaloriesKcal") == 1100  // 400 + 700
        response.getInt("totalProteinGrams") == 55   // 20 + 35
        response.getInt("totalFatGrams") == 40       // 15 + 25
        response.getInt("totalCarbohydratesGrams") == 120  // 40 + 80
        response.getInt("averageCaloriesPerMeal") == 550   // 1100 / 2
    }

    def "Scenario 4: Meal type counts are calculated correctly"() {
        given: "meals of different types including multiple snacks"
        def date = "2025-11-23"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|meal|breakfast-2025-11-23",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-23T07:00:00Z",
                    "payload": {
                        "title": "Yogurt",
                        "mealType": "BREAKFAST",
                        "caloriesKcal": 200,
                        "proteinGrams": 15,
                        "fatGrams": 5,
                        "carbohydratesGrams": 25,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|snack1-2025-11-23",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-23T10:00:00Z",
                    "payload": {
                        "title": "Apple",
                        "mealType": "SNACK",
                        "caloriesKcal": 95,
                        "proteinGrams": 0,
                        "fatGrams": 0,
                        "carbohydratesGrams": 25,
                        "healthRating": "VERY_HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|snack2-2025-11-23",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-23T15:00:00Z",
                    "payload": {
                        "title": "Protein bar",
                        "mealType": "SNACK",
                        "caloriesKcal": 180,
                        "proteinGrams": 20,
                        "fatGrams": 8,
                        "carbohydratesGrams": 15,
                        "healthRating": "NEUTRAL"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|dessert-2025-11-23",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-23T20:00:00Z",
                    "payload": {
                        "title": "Ice cream",
                        "mealType": "DESSERT",
                        "caloriesKcal": 300,
                        "proteinGrams": 5,
                        "fatGrams": 15,
                        "carbohydratesGrams": 35,
                        "healthRating": "UNHEALTHY"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit all meals"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "meal type counts are correct (verified via API)"
        def response = waitForApiResponse("/v1/meals/daily/${date}")
        response.getInt("totalMealCount") == 4
        response.getInt("mealTypeCounts.breakfast") == 1
        response.getInt("mealTypeCounts.snack") == 2
        response.getInt("mealTypeCounts.dessert") == 1
        response.getInt("mealTypeCounts.lunch") == 0
        response.getInt("mealTypeCounts.dinner") == 0
        response.getInt("mealTypeCounts.brunch") == 0
        response.getInt("mealTypeCounts.drink") == 0
    }

    def "Scenario 5: Health rating counts are calculated correctly"() {
        given: "meals with different health ratings"
        def date = "2025-11-24"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|meal|very-healthy",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-24T08:00:00Z",
                    "payload": {
                        "title": "Green smoothie",
                        "mealType": "BREAKFAST",
                        "caloriesKcal": 200,
                        "proteinGrams": 10,
                        "fatGrams": 5,
                        "carbohydratesGrams": 30,
                        "healthRating": "VERY_HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|healthy",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-24T12:00:00Z",
                    "payload": {
                        "title": "Grilled chicken",
                        "mealType": "LUNCH",
                        "caloriesKcal": 400,
                        "proteinGrams": 35,
                        "fatGrams": 12,
                        "carbohydratesGrams": 20,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|very-unhealthy",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-24T20:00:00Z",
                    "payload": {
                        "title": "Deep fried pizza",
                        "mealType": "DINNER",
                        "caloriesKcal": 1200,
                        "proteinGrams": 25,
                        "fatGrams": 70,
                        "carbohydratesGrams": 100,
                        "healthRating": "VERY_UNHEALTHY"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit all meals"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "health rating counts are correct (verified via API)"
        def response = waitForApiResponse("/v1/meals/daily/${date}")
        response.getInt("healthRatingCounts.veryHealthy") == 1
        response.getInt("healthRatingCounts.healthy") == 1
        response.getInt("healthRatingCounts.neutral") == 0
        response.getInt("healthRatingCounts.unhealthy") == 0
        response.getInt("healthRatingCounts.veryUnhealthy") == 1
    }

    def "Scenario 6: Query API returns daily detail with all meals"() {
        given: "meal data with multiple meals"
        def date = "2025-11-25"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|meal|breakfast-2025-11-25",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-25T07:00:00Z",
                    "payload": {
                        "title": "Pancakes",
                        "mealType": "BREAKFAST",
                        "caloriesKcal": 400,
                        "proteinGrams": 10,
                        "fatGrams": 15,
                        "carbohydratesGrams": 55,
                        "healthRating": "NEUTRAL"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|lunch-2025-11-25",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-25T12:30:00Z",
                    "payload": {
                        "title": "Caesar salad",
                        "mealType": "LUNCH",
                        "caloriesKcal": 350,
                        "proteinGrams": 20,
                        "fatGrams": 20,
                        "carbohydratesGrams": 15,
                        "healthRating": "HEALTHY"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query daily detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/daily/${date}")
                .get("/v1/meals/daily/${date}")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response contains both meals"
        response.getList("meals").size() == 2

        and: "meal details are correct"
        def meals = response.getList("meals")
        meals[0].mealNumber == 1
        meals[0].title == "Pancakes"
        meals[0].mealType == "BREAKFAST"
        meals[1].mealNumber == 2
        meals[1].title == "Caesar salad"
        meals[1].mealType == "LUNCH"

        and: "daily totals are correct"
        response.getInt("totalMealCount") == 2
        response.getInt("totalCaloriesKcal") == 750
        response.getInt("totalProteinGrams") == 30
        response.getInt("totalFatGrams") == 35
        response.getInt("totalCarbohydratesGrams") == 70
    }

    def "Scenario 7: Query API returns range summary with daily stats"() {
        given: "meal data across multiple days"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|meal|2025-11-18",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-18T12:00:00Z",
                    "payload": {
                        "title": "Lunch day 1",
                        "mealType": "LUNCH",
                        "caloriesKcal": 500,
                        "proteinGrams": 25,
                        "fatGrams": 15,
                        "carbohydratesGrams": 50,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|2025-11-19",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-19T12:00:00Z",
                    "payload": {
                        "title": "Lunch day 2",
                        "mealType": "LUNCH",
                        "caloriesKcal": 600,
                        "proteinGrams": 30,
                        "fatGrams": 20,
                        "carbohydratesGrams": 55,
                        "healthRating": "NEUTRAL"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|2025-11-20",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-20T12:00:00Z",
                    "payload": {
                        "title": "Lunch day 3",
                        "mealType": "LUNCH",
                        "caloriesKcal": 400,
                        "proteinGrams": 20,
                        "fatGrams": 10,
                        "carbohydratesGrams": 45,
                        "healthRating": "HEALTHY"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query range summary"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/range?startDate=2025-11-18&endDate=2025-11-20")
                .get("/v1/meals/range?startDate=2025-11-18&endDate=2025-11-20")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "response contains 3 days"
        response.getList("dailyStats").size() == 3

        and: "daily stats are correct"
        def dailyStats = response.getList("dailyStats")
        dailyStats[0].date == "2025-11-18"
        dailyStats[0].totalCaloriesKcal == 500
        dailyStats[1].date == "2025-11-19"
        dailyStats[1].totalCaloriesKcal == 600
        dailyStats[2].date == "2025-11-20"
        dailyStats[2].totalCaloriesKcal == 400

        and: "totals are correct"
        response.getInt("totalCaloriesKcal") == 1500  // 500 + 600 + 400
        response.getInt("totalMealCount") == 3
        response.getInt("daysWithData") == 3
    }

    def "Scenario 8: Idempotent events don't duplicate projections"() {
        given: "a meal event"
        def date = "2025-11-30"
        def request = """
        {
            "events": [{
                "idempotencyKey": "test-device|meal|duplicate-test",
                "type": "MealRecorded.v1",
                "occurredAt": "2025-11-30T12:00:00Z",
                "payload": {
                    "title": "Test meal",
                    "mealType": "LUNCH",
                    "caloriesKcal": 500,
                    "proteinGrams": 25,
                    "fatGrams": 15,
                    "carbohydratesGrams": 50,
                    "healthRating": "HEALTHY"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit the same event twice"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projection is not duplicated (verified via API)"
        def response = waitForApiResponse("/v1/meals/daily/${date}")
        def meals = response.getList("meals")
        meals.size() == 1
        meals[0].caloriesKcal == 500

        and: "daily projection reflects single meal"
        response.getInt("totalMealCount") == 1
        response.getInt("totalCaloriesKcal") == 500
    }

    def "Scenario 9: Duplicate event with same idempotency key keeps original projection"() {
        given: "a meal event"
        def date = "2025-12-01"
        def request1 = """
        {
            "events": [{
                "idempotencyKey": "test-device|meal|update-test",
                "type": "MealRecorded.v1",
                "occurredAt": "2025-12-01T12:00:00Z",
                "payload": {
                    "title": "Original meal",
                    "mealType": "LUNCH",
                    "caloriesKcal": 500,
                    "proteinGrams": 25,
                    "fatGrams": 15,
                    "carbohydratesGrams": 50,
                    "healthRating": "HEALTHY"
                }
            }],
            "deviceId": "test-device"
        }
        """

        def request2 = """
        {
            "events": [{
                "idempotencyKey": "test-device|meal|update-test",
                "type": "MealRecorded.v1",
                "occurredAt": "2025-12-01T12:00:00Z",
                "payload": {
                    "title": "Updated meal",
                    "mealType": "LUNCH",
                    "caloriesKcal": 600,
                    "proteinGrams": 30,
                    "fatGrams": 20,
                    "carbohydratesGrams": 55,
                    "healthRating": "NEUTRAL"
                }
            }],
            "deviceId": "test-device"
        }
        """

        when: "I submit the first event"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request1)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        and: "I submit a duplicate with same idempotency key"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request2)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "projection keeps original data (projections are created on first event only, verified via API)"
        def response = waitForApiResponse("/v1/meals/daily/${date}")
        def meals = response.getList("meals")
        meals.size() == 1
        meals[0].title == "Original meal"
        meals[0].caloriesKcal == 500
        meals[0].healthRating == "HEALTHY"

        and: "daily projection keeps original data"
        response.getInt("totalCaloriesKcal") == 500
        response.getInt("healthRatingCounts.healthy") == 1
        response.getInt("healthRatingCounts.neutral") == 0
    }

    def "Scenario 10: API returns 404 for date with no meal data"() {
        given: "no meal data exists"

        when: "I query daily detail"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/daily/2025-12-05")
                .get("/v1/meals/daily/2025-12-05")
                .then()
                .extract()

        then: "404 is returned"
        response.statusCode() == 404
    }

    def "Scenario 11: Range summary includes days with no data as zero"() {
        given: "meal only on first and last day of range"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|meal|2025-11-27",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-27T12:00:00Z",
                    "payload": {
                        "title": "Day 1 meal",
                        "mealType": "LUNCH",
                        "caloriesKcal": 500,
                        "proteinGrams": 25,
                        "fatGrams": 15,
                        "carbohydratesGrams": 50,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|2025-11-29",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-11-29T12:00:00Z",
                    "payload": {
                        "title": "Day 3 meal",
                        "mealType": "LUNCH",
                        "caloriesKcal": 600,
                        "proteinGrams": 30,
                        "fatGrams": 20,
                        "carbohydratesGrams": 55,
                        "healthRating": "HEALTHY"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        and: "events are submitted"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I query 3-day range"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/range?startDate=2025-11-27&endDate=2025-11-29")
                .get("/v1/meals/range?startDate=2025-11-27&endDate=2025-11-29")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()

        then: "all 3 days are included"
        response.getList("dailyStats").size() == 3

        and: "middle day shows 0 meals"
        def dailyStats = response.getList("dailyStats")
        dailyStats[0].totalCaloriesKcal == 500
        dailyStats[1].totalCaloriesKcal == 0  // 2025-11-28 has no data
        dailyStats[2].totalCaloriesKcal == 600

        and: "daysWithData counts only non-zero days"
        response.getInt("daysWithData") == 2
    }

    def "Scenario 12: Range query with invalid dates returns 400"() {
        when: "I query range with endDate before startDate"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/meals/range?startDate=2025-11-30&endDate=2025-11-28")
                .get("/v1/meals/range?startDate=2025-11-30&endDate=2025-11-28")
                .then()
                .extract()

        then: "400 Bad Request is returned"
        response.statusCode() == 400
    }

    def "Scenario 13: Time tracking (first/last meal time) works correctly"() {
        given: "meals at different times"
        def date = "2025-12-02"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|meal|morning",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-02T07:30:00Z",
                    "payload": {
                        "title": "Early breakfast",
                        "mealType": "BREAKFAST",
                        "caloriesKcal": 300,
                        "proteinGrams": 15,
                        "fatGrams": 10,
                        "carbohydratesGrams": 35,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|evening",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-02T20:00:00Z",
                    "payload": {
                        "title": "Late dinner",
                        "mealType": "DINNER",
                        "caloriesKcal": 600,
                        "proteinGrams": 30,
                        "fatGrams": 20,
                        "carbohydratesGrams": 50,
                        "healthRating": "NEUTRAL"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit the meals"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "daily projection has correct time tracking (verified via API)"
        def response = waitForApiResponse("/v1/meals/daily/${date}")
        response.getString("firstMealTime") == "2025-12-02T07:30:00Z"
        response.getString("lastMealTime") == "2025-12-02T20:00:00Z"
    }

    def "Scenario 14: All meal types are tracked correctly"() {
        given: "one meal of each type"
        def date = "2025-12-03"
        def request = """
        {
            "events": [
                {
                    "idempotencyKey": "test-device|meal|breakfast-all",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-03T07:00:00Z",
                    "payload": {
                        "title": "Breakfast",
                        "mealType": "BREAKFAST",
                        "caloriesKcal": 300,
                        "proteinGrams": 10,
                        "fatGrams": 10,
                        "carbohydratesGrams": 40,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|brunch-all",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-03T10:00:00Z",
                    "payload": {
                        "title": "Brunch",
                        "mealType": "BRUNCH",
                        "caloriesKcal": 400,
                        "proteinGrams": 15,
                        "fatGrams": 15,
                        "carbohydratesGrams": 50,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|lunch-all",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-03T13:00:00Z",
                    "payload": {
                        "title": "Lunch",
                        "mealType": "LUNCH",
                        "caloriesKcal": 500,
                        "proteinGrams": 25,
                        "fatGrams": 15,
                        "carbohydratesGrams": 50,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|snack-all",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-03T15:00:00Z",
                    "payload": {
                        "title": "Snack",
                        "mealType": "SNACK",
                        "caloriesKcal": 150,
                        "proteinGrams": 5,
                        "fatGrams": 5,
                        "carbohydratesGrams": 20,
                        "healthRating": "NEUTRAL"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|dinner-all",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-03T18:00:00Z",
                    "payload": {
                        "title": "Dinner",
                        "mealType": "DINNER",
                        "caloriesKcal": 600,
                        "proteinGrams": 30,
                        "fatGrams": 20,
                        "carbohydratesGrams": 60,
                        "healthRating": "HEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|dessert-all",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-03T20:00:00Z",
                    "payload": {
                        "title": "Dessert",
                        "mealType": "DESSERT",
                        "caloriesKcal": 250,
                        "proteinGrams": 5,
                        "fatGrams": 15,
                        "carbohydratesGrams": 30,
                        "healthRating": "UNHEALTHY"
                    }
                },
                {
                    "idempotencyKey": "test-device|meal|drink-all",
                    "type": "MealRecorded.v1",
                    "occurredAt": "2025-12-03T21:00:00Z",
                    "payload": {
                        "title": "Smoothie",
                        "mealType": "DRINK",
                        "caloriesKcal": 200,
                        "proteinGrams": 10,
                        "fatGrams": 5,
                        "carbohydratesGrams": 30,
                        "healthRating": "VERY_HEALTHY"
                    }
                }
            ],
            "deviceId": "test-device"
        }
        """

        when: "I submit all meals"
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "all meal types are counted (verified via API)"
        def response = waitForApiResponse("/v1/meals/daily/${date}")
        response.getInt("totalMealCount") == 7
        response.getInt("mealTypeCounts.breakfast") == 1
        response.getInt("mealTypeCounts.brunch") == 1
        response.getInt("mealTypeCounts.lunch") == 1
        response.getInt("mealTypeCounts.snack") == 1
        response.getInt("mealTypeCounts.dinner") == 1
        response.getInt("mealTypeCounts.dessert") == 1
        response.getInt("mealTypeCounts.drink") == 1
    }
}
