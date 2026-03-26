package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

@Title("Feature: Spring AI MCP Server — auth and tool registration")
class McpServerSpec extends BaseIntegrationSpec {

    // ===========================================
    // SSE endpoint (/sse) auth
    // ===========================================

    def "SSE endpoint without auth returns 401"() {
        when:
        def response = RestAssured.given()
                .get("/sse")
                .then().extract()

        then:
        response.statusCode() == 401
    }

    def "SSE endpoint with valid Bearer token connects (not 401)"() {
        when: "Bearer token is provided"
        def response = RestAssured.given()
                .header("Authorization", "Bearer ${TEST_API_KEY}")
                .config(RestAssured.config().connectionConfig(
                        io.restassured.config.ConnectionConfig.connectionConfig()
                                .closeIdleConnectionsAfterEachResponse()))
                .get("/sse")
                .then().extract()

        then: "Auth passes — connection opens (200) or server closes it immediately (any 2xx)"
        response.statusCode() != 401
        response.statusCode() != 403
    }

    // ===========================================
    // Message endpoint (/mcp/message) auth
    // ===========================================

    def "MCP message endpoint without auth returns 401"() {
        when:
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body('{"jsonrpc":"2.0","method":"tools/list","id":1}')
                .post("/mcp/message")
                .then().extract()

        then:
        response.statusCode() == 401
    }

    def "MCP message endpoint with wrong token returns 401"() {
        when:
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer totally-wrong-token-value-here!!")
                .body('{"jsonrpc":"2.0","method":"tools/list","id":1}')
                .post("/mcp/message")
                .then().extract()

        then:
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "API_KEY_AUTH_FAILED"
    }

    def "MCP message endpoint with valid Bearer token is authenticated (not 401)"() {
        when:
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer ${TEST_API_KEY}")
                .body('{"jsonrpc":"2.0","method":"tools/list","id":1}')
                .post("/mcp/message")
                .then().extract()

        then: "Request reaches MCP handler — any response except 401/403 means auth passed"
        response.statusCode() != 401
        response.statusCode() != 403
    }
}
