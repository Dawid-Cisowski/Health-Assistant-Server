package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for Meal event ingestion via generic health events endpoint
 */
@Title("Feature: Meal Event Ingestion via Health Events API")
class MealEventSpec extends BaseIntegrationSpec {

    def "Scenario 1: Submit valid meal event returns success"() {
        given: "a valid meal event request"
        def request = createHealthEventsRequest(createMealEvent("Grilled chicken with rice", "LUNCH"))

        when: "I submit the meal event"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains success status"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("totalEvents") == 1

        and: "event result indicates stored"
        def events = body.getList("events")
        events.size() == 1
        events[0].status == "stored"
        events[0].eventId != null
    }

    def "Scenario 2: Meal event is stored in database"() {
        given: "a valid meal event request"
        def request = createHealthEventsRequest(createMealEvent("Oatmeal with berries", "BREAKFAST"))

        when: "I submit the meal event"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event is stored in database"
        def events = eventRepository.findAll()
        events.size() == 1

        and: "event has correct type"
        def mealEvent = events.first()
        mealEvent.eventType == "MealRecorded.v1"

        and: "event has correct device ID"
        mealEvent.deviceId == "mobile-app"

        and: "event has correct payload"
        def payload = mealEvent.payload
        payload.get("title") == "Oatmeal with berries"
        payload.get("mealType") == "BREAKFAST"
        payload.get("caloriesKcal") == 350
        payload.get("proteinGrams") == 12
        payload.get("fatGrams") == 8
        payload.get("carbohydratesGrams") == 55
        payload.get("healthRating") == "HEALTHY"
    }

    def "Scenario 3: Duplicate meal event returns duplicate status"() {
        given: "a valid meal event request"
        def request = createHealthEventsRequest(createMealEvent("Salmon salad", "DINNER"))

        and: "I submit the meal event first time"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "I submit the same meal event again"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains duplicate status for the event"
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        def events = body.getList("events")
        events[0].status == "duplicate"

        and: "only one event is stored in database"
        def dbEvents = eventRepository.findAll()
        dbEvents.size() == 1
    }

    def "Scenario 4: Meal event with missing title returns validation error"() {
        given: "a meal event without title"
        def event = """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "2025-11-21T12:00:00Z",
            "payload": {
                "mealType": "LUNCH",
                "caloriesKcal": 500,
                "proteinGrams": 30,
                "fatGrams": 15,
                "carbohydratesGrams": 50,
                "healthRating": "HEALTHY"
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit the meal event"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status for the event"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
        events[0].error != null
    }

    def "Scenario 5: Meal event with missing mealType returns validation error"() {
        given: "a meal event without mealType"
        def event = """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "2025-11-21T12:00:00Z",
            "payload": {
                "title": "Chicken pasta",
                "caloriesKcal": 500,
                "proteinGrams": 30,
                "fatGrams": 15,
                "carbohydratesGrams": 50,
                "healthRating": "HEALTHY"
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit the meal event"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
    }

    def "Scenario 6: Meal event with invalid mealType returns validation error"() {
        given: "a meal event with invalid mealType"
        def event = """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "2025-11-21T12:00:00Z",
            "payload": {
                "title": "Chicken pasta",
                "mealType": "SUPPER",
                "caloriesKcal": 500,
                "proteinGrams": 30,
                "fatGrams": 15,
                "carbohydratesGrams": 50,
                "healthRating": "HEALTHY"
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit the meal event"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
        events[0].error.message.toString().contains("mealType")
    }

    def "Scenario 7: Meal event with invalid healthRating returns validation error"() {
        given: "a meal event with invalid healthRating"
        def event = """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "2025-11-21T12:00:00Z",
            "payload": {
                "title": "Pizza",
                "mealType": "DINNER",
                "caloriesKcal": 800,
                "proteinGrams": 30,
                "fatGrams": 35,
                "carbohydratesGrams": 90,
                "healthRating": "SUPER_UNHEALTHY"
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit the meal event"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
        events[0].error.message.toString().contains("healthRating")
    }

    def "Scenario 8: Meal event with negative calories returns validation error"() {
        given: "a meal event with negative calories"
        def event = """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "2025-11-21T12:00:00Z",
            "payload": {
                "title": "Test meal",
                "mealType": "SNACK",
                "caloriesKcal": -100,
                "proteinGrams": 10,
                "fatGrams": 5,
                "carbohydratesGrams": 20,
                "healthRating": "NEUTRAL"
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit the meal event"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
        events[0].error.message.toString().contains("non-negative")
    }

    def "Scenario 9: Meal event with negative protein returns validation error"() {
        given: "a meal event with negative protein"
        def event = """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "2025-11-21T12:00:00Z",
            "payload": {
                "title": "Test meal",
                "mealType": "SNACK",
                "caloriesKcal": 100,
                "proteinGrams": -10,
                "fatGrams": 5,
                "carbohydratesGrams": 20,
                "healthRating": "NEUTRAL"
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit the meal event"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains invalid status"
        def body = response.body().jsonPath()
        def events = body.getList("events")
        events[0].status == "invalid"
        events[0].error.message.toString().contains("non-negative")
    }

    def "Scenario 10: Multiple meal events are stored correctly"() {
        given: "multiple valid meal events"
        def event1 = createMealEvent("Oatmeal", "BREAKFAST")
        def event2 = createMealEvent("Chicken salad", "LUNCH")
        def event3 = createMealEvent("Protein shake", "SNACK")
        def event4 = createMealEvent("Steak with vegetables", "DINNER")

        def request = """
        {
            "events": [${event1}, ${event2}, ${event3}, ${event4}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit all meal events"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates success"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("totalEvents") == 4

        and: "all events are stored in database"
        def dbEvents = eventRepository.findAll()
        dbEvents.size() == 4
        dbEvents.every { it.eventType == "MealRecorded.v1" }
        dbEvents.every { it.deviceId == "mobile-app" }
    }

    def "Scenario 11: Meal event with all meal types are accepted"() {
        given: "meal events for each meal type"
        def mealTypes = ["BREAKFAST", "BRUNCH", "LUNCH", "DINNER", "SNACK", "DESSERT", "DRINK"]
        def events = mealTypes.collect { type ->
            createMealEventWithType("Test " + type, type)
        }

        def request = """
        {
            "events": [${events.join(",")}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit all meal types"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates success"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("totalEvents") == 7

        and: "all events are stored"
        def dbEvents = eventRepository.findAll()
        dbEvents.size() == 7
        dbEvents.collect { it.payload.get("mealType") }.containsAll(mealTypes)
    }

    def "Scenario 12: Meal event with all health ratings are accepted"() {
        given: "meal events for each health rating"
        def healthRatings = ["VERY_HEALTHY", "HEALTHY", "NEUTRAL", "UNHEALTHY", "VERY_UNHEALTHY"]
        def events = healthRatings.collect { rating ->
            createMealEventWithHealthRating("Test meal", rating)
        }

        def request = """
        {
            "events": [${events.join(",")}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit all health ratings"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response indicates success"
        response.statusCode() == 200
        def body = response.body().jsonPath()
        body.getString("status") == "success"
        body.getInt("totalEvents") == 5

        and: "all events are stored"
        def dbEvents = eventRepository.findAll()
        dbEvents.size() == 5
        dbEvents.collect { it.payload.get("healthRating") }.containsAll(healthRatings)
    }

    def "Scenario 13: Meal event with zero macros is valid"() {
        given: "a meal event with zero macros (e.g., water)"
        def event = """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "2025-11-21T10:00:00Z",
            "payload": {
                "title": "Water",
                "mealType": "DRINK",
                "caloriesKcal": 0,
                "proteinGrams": 0,
                "fatGrams": 0,
                "carbohydratesGrams": 0,
                "healthRating": "VERY_HEALTHY"
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit the meal event"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "event is stored"
        def dbEvents = eventRepository.findAll()
        dbEvents.size() == 1
        def mealEvent = dbEvents.first()
        mealEvent.payload.get("caloriesKcal") == 0
        mealEvent.payload.get("proteinGrams") == 0
    }

    def "Scenario 14: Meal event occurredAt timestamp is preserved"() {
        given: "a meal event with specific timestamp"
        def occurredAt = "2025-11-21T12:30:45Z"
        def event = """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "title": "Lunch meal",
                "mealType": "LUNCH",
                "caloriesKcal": 600,
                "proteinGrams": 40,
                "fatGrams": 20,
                "carbohydratesGrams": 50,
                "healthRating": "HEALTHY"
            }
        }
        """
        def request = """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """

        when: "I submit the meal event"
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        then: "event occurredAt matches the submitted timestamp"
        def dbEvents = eventRepository.findAll()
        dbEvents.size() == 1
        def mealEvent = dbEvents.first()
        def storedOccurredAt = Instant.parse(mealEvent.occurredAt.toString())
        storedOccurredAt == Instant.parse(occurredAt)
    }

    // Helper methods

    String createHealthEventsRequest(String event) {
        return """
        {
            "events": [${event}],
            "deviceId": "mobile-app"
        }
        """.stripIndent().trim()
    }

    String createMealEvent(String title, String mealType, String occurredAt = "2025-11-21T12:00:00Z") {
        return """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "${occurredAt}",
            "payload": {
                "title": "${title}",
                "mealType": "${mealType}",
                "caloriesKcal": 350,
                "proteinGrams": 12,
                "fatGrams": 8,
                "carbohydratesGrams": 55,
                "healthRating": "HEALTHY"
            }
        }
        """.stripIndent().trim()
    }

    String createMealEventWithType(String title, String mealType) {
        return """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "2025-11-21T12:00:00Z",
            "payload": {
                "title": "${title}",
                "mealType": "${mealType}",
                "caloriesKcal": 300,
                "proteinGrams": 10,
                "fatGrams": 10,
                "carbohydratesGrams": 40,
                "healthRating": "NEUTRAL"
            }
        }
        """.stripIndent().trim()
    }

    String createMealEventWithHealthRating(String title, String healthRating) {
        return """
        {
            "type": "MealRecorded.v1",
            "occurredAt": "2025-11-21T12:00:00Z",
            "payload": {
                "title": "${title}",
                "mealType": "LUNCH",
                "caloriesKcal": 400,
                "proteinGrams": 20,
                "fatGrams": 15,
                "carbohydratesGrams": 45,
                "healthRating": "${healthRating}"
            }
        }
        """.stripIndent().trim()
    }
}
