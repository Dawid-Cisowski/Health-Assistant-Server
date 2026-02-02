package com.healthassistant

import spock.lang.Title

/**
 * Integration tests for Exercise Catalog API
 */
@Title("Feature: Exercise Catalog API")
class ExerciseCatalogSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-exercises"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "Scenario 1: Get all exercises returns complete catalog"() {
        when: "I request all exercises"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/exercises")
                .get("/v1/workouts/exercises")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains all 62 exercises"
        def exercises = response.body().jsonPath().getList("")
        exercises.size() == 62

        and: "each exercise has required fields"
        exercises.every { exercise ->
            exercise.id != null &&
            exercise.name != null &&
            exercise.description != null &&
            exercise.primaryMuscle != null &&
            exercise.muscles != null &&
            exercise.muscles.size() > 0
        }
    }

    def "Scenario 2: Get all muscle groups returns 16 groups"() {
        when: "I request all muscle groups"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/exercises/muscles")
                .get("/v1/workouts/exercises/muscles")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains 16 muscle groups"
        def muscles = response.body().jsonPath().getList("")
        muscles.size() == 16

        and: "contains expected muscle groups"
        muscles.containsAll(["CHEST", "UPPER_BACK", "LOWER_BACK", "TRAPS",
                             "SHOULDERS_FRONT", "SHOULDERS_SIDE", "SHOULDERS_REAR",
                             "BICEPS", "TRICEPS", "FOREARMS",
                             "ABS", "OBLIQUES",
                             "GLUTES", "QUADS", "HAMSTRINGS", "CALVES"])
    }

    def "Scenario 3: Get exercises by muscle returns filtered results"() {
        when: "I request exercises for CHEST muscle"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/exercises/muscle/CHEST")
                .get("/v1/workouts/exercises/muscle/CHEST")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains chest exercises"
        def exercises = response.body().jsonPath().getList("")
        exercises.size() > 0

        and: "all exercises engage CHEST muscle"
        exercises.every { exercise ->
            exercise.muscles.contains("CHEST")
        }

        and: "includes bench press"
        exercises.any { exercise ->
            exercise.id == "chest_1" && exercise.name.contains("Wyciskanie sztangi")
        }
    }

    def "Scenario 4: Get exercises by muscle is case insensitive"() {
        when: "I request exercises for lowercase 'biceps' muscle"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/exercises/muscle/biceps")
                .get("/v1/workouts/exercises/muscle/biceps")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains exercises that engage BICEPS (6 biceps + 7 back exercises)"
        def exercises = response.body().jsonPath().getList("")
        exercises.size() == 13

        and: "all exercises engage BICEPS muscle"
        exercises.every { exercise ->
            exercise.muscles.contains("BICEPS")
        }
    }

    def "Scenario 5: Get exercises for non-existent muscle returns empty list"() {
        when: "I request exercises for non-existent muscle"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/exercises/muscle/NONEXISTENT")
                .get("/v1/workouts/exercises/muscle/NONEXISTENT")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains empty list"
        def exercises = response.body().jsonPath().getList("")
        exercises.size() == 0
    }

    def "Scenario 6: Exercise has correct muscle mapping"() {
        when: "I request all exercises"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/exercises")
                .get("/v1/workouts/exercises")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "deadlift has correct muscles"
        def exercises = response.body().jsonPath().getList("")
        def deadlift = exercises.find { it.id == "back_1" }
        deadlift != null
        deadlift.name == "Martwy ciąg klasyczny"
        deadlift.primaryMuscle == "LOWER_BACK"
        deadlift.muscles.containsAll(["LOWER_BACK", "UPPER_BACK", "GLUTES", "HAMSTRINGS", "TRAPS", "FOREARMS"])
    }

    def "Scenario 7: Exercises are categorized by body part"() {
        when: "I request all exercises"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/workouts/exercises")
                .get("/v1/workouts/exercises")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "exercises are categorized correctly"
        def exercises = response.body().jsonPath().getList("")

        def chestExercises = exercises.findAll { it.id.startsWith("chest_") }
        def backExercises = exercises.findAll { it.id.startsWith("back_") }
        def legsExercises = exercises.findAll { it.id.startsWith("legs_") }
        def shouldersExercises = exercises.findAll { it.id.startsWith("shoulders_") }
        def bicepsExercises = exercises.findAll { it.id.startsWith("biceps_") }
        def tricepsExercises = exercises.findAll { it.id.startsWith("triceps_") }
        def absExercises = exercises.findAll { it.id.startsWith("abs_") }

        chestExercises.size() == 11
        backExercises.size() == 12
        legsExercises.size() == 12
        shouldersExercises.size() == 9
        bicepsExercises.size() == 6
        tricepsExercises.size() == 6
        absExercises.size() == 6
    }

    def "Scenario 8: Unauthenticated request returns 401"() {
        when: "I request exercises without authentication"
        def response = io.restassured.RestAssured.given()
                .contentType(io.restassured.http.ContentType.JSON)
                .get("/v1/workouts/exercises")
                .then()
                .extract()

        then: "response status is 401"
        response.statusCode() == 401
    }
}
