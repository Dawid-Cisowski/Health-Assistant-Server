package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType

class AssistantSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-assistant"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    def "POST /v1/assistant/chat should require HMAC authentication"() {
        when: "chat request is sent without authentication"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body('{"message": "Ile spałem wczoraj?"}')
                .when()
                .post("/v1/assistant/chat")

        then: "should return 401 Unauthorized"
        response.statusCode() == 401

        and: "response should contain error details"
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
    }

    def "POST /v1/assistant/chat should accept valid authenticated request and return SSE stream"() {
        given: "user has some health data"
        def sleepEvent = createSleepSessionEvent("sleep-key-1", "2025-11-22T08:00:00Z")
        authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/health-events", sleepEvent)
                .when()
                .post("/v1/health-events")
                .then()
                .statusCode(200)

        when: "authenticated chat request is sent"
        def chatRequest = '{"message": "Ile spałem wczoraj?"}'
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/assistant/chat", chatRequest)
                .when()
                .post("/v1/assistant/chat")

        then: "should return 200 OK"
        response.statusCode() == 200

        and: "response should have SSE content type"
        response.contentType().contains("text/event-stream")

        and: "response body should contain SSE data"
        response.body().asString().length() > 0
    }

    def "POST /v1/assistant/chat should reject empty message"() {
        when: "authenticated request with empty message is sent"
        def chatRequest = '{"message": ""}'
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/assistant/chat", chatRequest)
                .when()
                .post("/v1/assistant/chat")

        then: "should return 400 Bad Request"
        response.statusCode() == 400
    }

    def "POST /v1/assistant/chat should reject message that is too long"() {
        when: "authenticated request with very long message is sent"
        String longMessage = "a" * 1001  // Max is 1000
        def chatRequest = "{\"message\": \"${longMessage}\"}"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, "/v1/assistant/chat", chatRequest)
                .when()
                .post("/v1/assistant/chat")

        then: "should return 400 Bad Request"
        response.statusCode() == 400
    }
}
