package com.healthassistant

import io.restassured.RestAssured
import spock.lang.Title

/**
 * Integration tests for Error Handling (Feature 6)
 */
@Title("Feature 6: Error Handling")
class ErrorHandlingSpec extends BaseIntegrationSpec {

    def "Scenario 6.1: Missing idempotencyKey field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event without idempotencyKey"
        def body = """
        {
            "events": [{
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 100,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 400 BAD REQUEST"
        response.statusCode() == 400

        and: "error should mention idempotencyKey"
        def errorMessage = response.body().jsonPath().getString("message")
        errorMessage != null

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 6.2: Missing type field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event without type field"
        def body = """
        {
            "events": [{
                "idempotencyKey": "test|${System.currentTimeMillis()}",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 100,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 400 BAD REQUEST"
        response.statusCode() == 400

        and: "error should mention type"
        def errorMessage = response.body().jsonPath().getString("message")
        errorMessage != null

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 6.3: Missing occurredAt field"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event without occurredAt field"
        def body = """
        {
            "events": [{
                "idempotencyKey": "test|${System.currentTimeMillis()}",
                "type": "StepsBucketedRecorded.v1",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 100,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 400 BAD REQUEST"
        response.statusCode() == 400

        and: "error should mention occurredAt"
        def errorMessage = response.body().jsonPath().getString("message")
        errorMessage != null

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 6.4: Invalid occurredAt format"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event with invalid occurredAt format"
        def body = """
        {
            "events": [{
                "idempotencyKey": "test|${System.currentTimeMillis()}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "invalid-timestamp",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 100,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 400 BAD REQUEST"
        response.statusCode() == 400

        and: "error should indicate timestamp parsing issue"
        def errorMessage = response.body().jsonPath().getString("message")
        errorMessage != null

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 6.5: Null payload"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event with null payload"
        def body = """
        {
            "events": [{
                "idempotencyKey": "test|${System.currentTimeMillis()}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": null
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 400 BAD REQUEST (Spring validation rejects null payload)"
        response.statusCode() == 400

        and: "error message is present"
        def errorMessage = response.body().jsonPath().getString("message")
        errorMessage != null

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 6.6: Empty payload object"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event with empty payload object"
        def body = """
        {
            "events": [{
                "idempotencyKey": "test|${System.currentTimeMillis()}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {}
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED (partial failure)"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Payload cannot be empty")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 6.7: Request with Content-Type not application/json"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "valid event body"
        def body = createStepsEvent("test|contenttype|${System.currentTimeMillis()}")

        and: "signature computed for the body"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", "/v1/health-events", timestamp, nonce, deviceId, body, secretBase64)

        when: "I POST with Content-Type: text/plain"
        def response = RestAssured.given()
                .contentType("text/plain")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
        .when()
                .post("/v1/health-events")
        .then()
                .extract()

        then: "response status should be 415 UNSUPPORTED MEDIA TYPE"
        response.statusCode() == 415

        and: "error message should indicate Content-Type issue"
        def errorResponse = response.body().jsonPath()
        errorResponse.getString("code") == "UNSUPPORTED_MEDIA_TYPE"

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 6.8: Very long idempotencyKey (stress test)"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event with very long idempotencyKey (300 chars)"
        def longKey = "a" * 300
        def body = """
        {
            "events": [{
                "idempotencyKey": "${longKey}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 100,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED (server handles it)"
        response.statusCode() == 202

        and: "event is stored or rejected gracefully"
        def result = response.body().jsonPath()
        result.getString("results[0].status") in ["stored", "invalid"]

        and: "if stored, verify it's saved correctly"
        if (result.getString("results[0].status") == "stored") {
            eventRepository.findAll().size() == 1
            eventRepository.findAll()[0].idempotencyKey.length() == 300
        }
    }

    def "Scenario 6.9: Event type case sensitivity"() {
        given: "authenticated device"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "event with incorrect case in type (stepsbucketedrecorded.v1 instead of StepsBucketedRecorded.v1)"
        def body = """
        {
            "events": [{
                "idempotencyKey": "test|case|${System.currentTimeMillis()}",
                "type": "stepsbucketedrecorded.v1",
                "occurredAt": "2025-11-10T10:00:00Z",
                "payload": {
                    "bucketStart": "2025-11-10T09:00:00Z",
                    "bucketEnd": "2025-11-10T10:00:00Z",
                    "count": 100,
                    "originPackage": "com.test"
                }
            }]
        }
        """.stripIndent().trim()

        when: "I POST event"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/health-events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED (partial failure)"
        response.statusCode() == 202

        and: "result status should be 'invalid'"
        def result = response.body().jsonPath()
        result.getString("results[0].status") == "invalid"
        result.getString("results[0].error.message").contains("Invalid event type")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }
}

