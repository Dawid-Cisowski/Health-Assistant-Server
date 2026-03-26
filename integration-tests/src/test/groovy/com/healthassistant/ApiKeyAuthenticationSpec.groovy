package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

@Title("Feature: API Key (Bearer Token) Authentication for Claude")
class ApiKeyAuthenticationSpec extends BaseIntegrationSpec {

    static final String VALID_EVENTS_BODY = """
    {
        "events": [
            {
                "idempotencyKey": "api-key-test-${UUID.randomUUID()}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T07:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T06:00:00Z",
                    "bucketEnd": "2025-11-10T07:00:00Z",
                    "count": 100,
                    "originPackage": "com.test"
                }
            }
        ]
    }
    """

    // ===========================================
    // Valid Bearer Token Tests
    // ===========================================

    def "Scenario 1: Valid Bearer token on GET endpoint is authenticated (not 401)"() {
        given: "a request with valid Bearer token"
        def path = "/v1/daily-summaries/2025-11-10"

        when: "I submit the GET request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer ${TEST_API_KEY}")
                .get(path)
                .then()
                .extract()

        then: "I do NOT get 401 - auth succeeded even if endpoint returns other status"
        response.statusCode() != 401
    }

    def "Scenario 2: Valid Bearer token on POST health-events returns 200"() {
        given: "a request with valid Bearer token"
        def path = "/v1/health-events"
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "api-key-post-test-${UUID.randomUUID()}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T07:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T06:00:00Z",
                        "bucketEnd": "2025-11-10T07:00:00Z",
                        "count": 100,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        when: "I submit the POST request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer ${TEST_API_KEY}")
                .body(body)
                .post(path)
                .then()
                .extract()

        then: "I get 200 OK"
        response.statusCode() == 200
    }

    // ===========================================
    // Invalid/Missing Token Tests
    // ===========================================

    def "Scenario 3: No auth headers at all returns 401"() {
        given: "a request with no authentication headers"
        def path = "/v1/health-events"

        when: "I submit the request without any auth"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 (falls through to HMAC filter)"
        response.statusCode() == 401
    }

    def "Scenario 4: Wrong Bearer token returns 401 with API_KEY_AUTH_FAILED"() {
        given: "a request with an incorrect Bearer token"
        def path = "/v1/health-events"

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer this-is-a-wrong-token-totally-wrong!!")
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 with API key error code"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "API_KEY_AUTH_FAILED"
    }

    def "Scenario 5: Authorization header without 'Bearer ' prefix falls through to HMAC → 401"() {
        given: "a request with raw token (no Bearer prefix)"
        def path = "/v1/health-events"

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", TEST_API_KEY)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 from HMAC (API key filter skipped - no Bearer prefix)"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
    }

    def "Scenario 6: Empty Bearer token ('Bearer ' with no value) returns 401"() {
        given: "a request with empty Bearer token (HTTP may trim trailing space to 'Bearer', falling through to HMAC)"
        def path = "/v1/health-events"

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer ")
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 (either API_KEY_AUTH_FAILED or HMAC_AUTH_FAILED - both indicate auth failure)"
        response.statusCode() == 401
    }

    // ===========================================
    // Backward Compatibility Tests
    // ===========================================

    def "Scenario 7: HMAC authentication still works alongside Bearer token support"() {
        given: "a properly HMAC-authenticated POST request"
        def path = "/v1/health-events"
        def body = """
        {
            "events": [
                {
                    "idempotencyKey": "hmac-alongside-api-key-${UUID.randomUUID()}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T07:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T06:00:00Z",
                        "bucketEnd": "2025-11-10T07:00:00Z",
                        "count": 100,
                        "originPackage": "com.test"
                    }
                }
            ]
        }
        """

        when: "I submit with HMAC headers (no Bearer)"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "HMAC auth still works - 200 OK"
        response.statusCode() == 200
    }

    // ===========================================
    // Unauthenticated Path Tests
    // ===========================================

    def "Scenario 8: Bearer token on unauthenticated actuator path passes through"() {
        given: "a request to actuator with a Bearer token"

        when: "I query the health endpoint"
        def response = RestAssured.given()
                .header("Authorization", "Bearer ${TEST_API_KEY}")
                .get("/actuator/health")
                .then()
                .extract()

        then: "I get 200 (Bearer token doesn't interfere with public paths)"
        response.statusCode() == 200
    }

    def "Scenario 9: Bearer token on unauthenticated path without token also works"() {
        given: "a request to actuator without any auth"

        when: "I query the health endpoint"
        def response = RestAssured.given()
                .get("/actuator/health")
                .then()
                .extract()

        then: "I get 200 (public path)"
        response.statusCode() == 200
    }
}
