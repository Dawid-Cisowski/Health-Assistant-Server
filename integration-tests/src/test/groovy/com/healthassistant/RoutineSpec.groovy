package com.healthassistant

import groovy.json.JsonOutput
import spock.lang.Title

/**
 * Integration tests for Routine (Workout Plan) CRUD API
 */
@Title("Feature: Routine (Workout Plan) CRUD API")
class RoutineSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-routines"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "Scenario 1: Create routine with exercises"() {
        given: "a valid routine request"
        def request = JsonOutput.toJson([
                name: "FBW A",
                description: "Full Body Workout - variant A",
                colorTheme: "bg-blue-500",
                exercises: [
                        [exerciseId: "legs_1", orderIndex: 1, defaultSets: 3, notes: "Tempo 3010"],
                        [exerciseId: "chest_1", orderIndex: 2, defaultSets: 4],
                        [exerciseId: "back_2", orderIndex: 3, defaultSets: 3]
                ]
        ])

        when: "I create the routine"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/routines", request)
                .post("/v1/routines")
                .then()
                .extract()

        then: "response status is 201"
        response.statusCode() == 201

        and: "response contains routine data"
        def body = response.body().jsonPath()
        body.getString("id") != null
        body.getString("name") == "FBW A"
        body.getString("description") == "Full Body Workout - variant A"
        body.getString("colorTheme") == "bg-blue-500"

        and: "routine has 3 exercises"
        def exercises = body.getList("exercises")
        exercises.size() == 3
        exercises[0].exerciseId == "legs_1"
        exercises[0].orderIndex == 1
        exercises[0].defaultSets == 3
        exercises[0].notes == "Tempo 3010"
        exercises[0].exerciseName == "Przysiady ze sztangą (high bar)"
    }

    def "Scenario 2: Get list of routines"() {
        given: "two routines exist"
        createRoutine("Push Day", [
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 4]
        ])
        createRoutine("Pull Day", [
                [exerciseId: "back_2", orderIndex: 1, defaultSets: 4]
        ])

        when: "I request the list of routines"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/routines")
                .get("/v1/routines")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains at least 2 routines"
        def routines = response.body().jsonPath().getList("")
        routines.size() >= 2

        and: "routines have required fields"
        routines.every { routine ->
            routine.id != null &&
            routine.name != null &&
            routine.exerciseCount >= 0
        }
    }

    def "Scenario 3: Get routine details"() {
        given: "a routine exists"
        def routineId = createRoutine("Legs Day", [
                [exerciseId: "legs_1", orderIndex: 1, defaultSets: 5, notes: "Heavy"],
                [exerciseId: "legs_3", orderIndex: 2, defaultSets: 4]
        ])

        when: "I request the routine details"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/routines/${routineId}")
                .get("/v1/routines/${routineId}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains routine details"
        def body = response.body().jsonPath()
        body.getString("id") == routineId
        body.getString("name") == "Legs Day"

        and: "exercises are included with names"
        def exercises = body.getList("exercises")
        exercises.size() == 2
        exercises[0].exerciseName == "Przysiady ze sztangą (high bar)"
        exercises[1].exerciseName == "Prasa do nóg (leg press)"
    }

    def "Scenario 4: Update routine"() {
        given: "a routine exists"
        def routineId = createRoutine("Old Name", [
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 3]
        ])

        and: "an update request"
        def updateRequest = JsonOutput.toJson([
                name: "New Name",
                description: "Updated description",
                colorTheme: "bg-green-500",
                exercises: [
                        [exerciseId: "back_1", orderIndex: 1, defaultSets: 5],
                        [exerciseId: "back_2", orderIndex: 2, defaultSets: 4]
                ]
        ])

        when: "I update the routine"
        def response = authenticatedPutRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/routines/${routineId}", updateRequest)
                .put("/v1/routines/${routineId}")
                .then()
                .extract()

        then: "response status is 200"
        response.statusCode() == 200

        and: "response contains updated data"
        def body = response.body().jsonPath()
        body.getString("name") == "New Name"
        body.getString("description") == "Updated description"
        body.getString("colorTheme") == "bg-green-500"

        and: "exercises are updated"
        def exercises = body.getList("exercises")
        exercises.size() == 2
        exercises[0].exerciseId == "back_1"
        exercises[1].exerciseId == "back_2"
    }

    def "Scenario 5: Delete routine"() {
        given: "a routine exists"
        def routineId = createRoutine("To Delete", [
                [exerciseId: "chest_1", orderIndex: 1, defaultSets: 3]
        ])

        when: "I delete the routine"
        def response = authenticatedDeleteRequest(DEVICE_ID, SECRET_BASE64, "/v1/routines/${routineId}")
                .delete("/v1/routines/${routineId}")
                .then()
                .extract()

        then: "response status is 204"
        response.statusCode() == 204

        when: "I try to get the deleted routine"
        def getResponse = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/routines/${routineId}")
                .get("/v1/routines/${routineId}")
                .then()
                .extract()

        then: "response status is 404"
        getResponse.statusCode() == 404
    }

    def "Scenario 6: Get non-existent routine returns 404"() {
        when: "I request a non-existent routine"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET_BASE64, "/v1/routines/00000000-0000-0000-0000-000000000000")
                .get("/v1/routines/00000000-0000-0000-0000-000000000000")
                .then()
                .extract()

        then: "response status is 404"
        response.statusCode() == 404
    }

    def "Scenario 7: Create routine with invalid exercise ID returns 400"() {
        given: "a routine request with invalid exercise ID"
        def request = JsonOutput.toJson([
                name: "Invalid Routine",
                exercises: [
                        [exerciseId: "invalid_exercise_id", orderIndex: 1, defaultSets: 3]
                ]
        ])

        when: "I try to create the routine"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/routines", request)
                .post("/v1/routines")
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }

    def "Scenario 8: Create routine without name returns 400"() {
        given: "a routine request without name"
        def request = JsonOutput.toJson([
                exercises: [
                        [exerciseId: "chest_1", orderIndex: 1, defaultSets: 3]
                ]
        ])

        when: "I try to create the routine"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/routines", request)
                .post("/v1/routines")
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }

    def "Scenario 9: Create routine without exercises returns 400"() {
        given: "a routine request without exercises"
        def request = JsonOutput.toJson([
                name: "Empty Routine",
                exercises: []
        ])

        when: "I try to create the routine"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/routines", request)
                .post("/v1/routines")
                .then()
                .extract()

        then: "response status is 400"
        response.statusCode() == 400
    }

    def "Scenario 10: Unauthenticated request returns 401"() {
        when: "I request routines without authentication"
        def response = io.restassured.RestAssured.given()
                .contentType(io.restassured.http.ContentType.JSON)
                .get("/v1/routines")
                .then()
                .extract()

        then: "response status is 401"
        response.statusCode() == 401
    }

    // Helper method to create a routine and return its ID
    private String createRoutine(String name, List<Map> exercises) {
        def request = JsonOutput.toJson([
                name: name,
                exercises: exercises
        ])

        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/routines", request)
                .post("/v1/routines")
                .then()
                .statusCode(201)
                .extract()

        return response.body().jsonPath().getString("id")
    }

    // Helper for PUT requests
    private authenticatedPutRequestWithBody(String deviceId, String secretBase64, String path, String body) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("PUT", path, timestamp, nonce, deviceId, body, secretBase64)

        return io.restassured.RestAssured.given()
                .contentType(io.restassured.http.ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
    }
}
