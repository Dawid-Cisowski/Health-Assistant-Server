package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

@Title("Feature: Global Exception Handler - Error Response Formatting")
class GlobalExceptionHandlerSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-exception"
    private static final String SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"

    // ===========================================
    // Validation Errors (MethodArgumentNotValidException)
    // ===========================================

    def "Scenario 1: Empty events array returns batch size error"() {
        given: "a request with empty events array"
        def path = "/v1/health-events"
        def body = '{"events": []}'

        when: "I submit the request"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get 413 Payload Too Large with batch size error"
        response.statusCode() == 413
        response.body().jsonPath().getString("code") == "BATCH_TOO_LARGE"
    }

    def "Scenario 2: Missing required field in event payload returns validation error with field name"() {
        given: "a request with missing required field (count)"
        def path = "/v1/health-events"
        def body = '''
        {
            "events": [
                {
                    "idempotencyKey": "validation-test-1",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "2025-11-10T07:00:00Z",
                    "payload": {
                        "bucketStart": "2025-11-10T06:00:00Z",
                        "bucketEnd": "2025-11-10T07:00:00Z"
                    }
                }
            ]
        }
        '''

        when: "I submit the request"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get 200 with partial success or all_invalid status (validation happens per-event)"
        response.statusCode() == 200
        def status = response.body().jsonPath().getString("status")
        status in ["partial_success", "all_invalid"]

        and: "event result contains validation error"
        def eventResults = response.body().jsonPath().getList("events")
        eventResults.size() == 1
        eventResults[0].status == "invalid"
        eventResults[0].error != null
    }

    // ===========================================
    // Malformed Request (HttpMessageNotReadableException)
    // ===========================================

    def "Scenario 3: Invalid JSON syntax returns MALFORMED_REQUEST error"() {
        given: "a request with invalid JSON"
        def path = "/v1/health-events"
        def invalidJson = '{"events": [invalid json here}'

        when: "I submit the request"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, path, invalidJson)
                .post(path)
                .then()
                .extract()

        then: "I get 400 Bad Request with MALFORMED_REQUEST code"
        response.statusCode() == 400
        response.body().jsonPath().getString("code") == "MALFORMED_REQUEST"
    }

    def "Scenario 4: Invalid timestamp format in payload returns error"() {
        given: "a request with invalid timestamp format in occurredAt"
        def path = "/v1/health-events"
        def body = '''
        {
            "events": [
                {
                    "idempotencyKey": "timestamp-test-1",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "not-a-valid-timestamp",
                    "payload": {
                        "bucketStart": "2025-11-10T06:00:00Z",
                        "bucketEnd": "2025-11-10T07:00:00Z",
                        "count": 100
                    }
                }
            ]
        }
        '''

        when: "I submit the request"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get 400 Bad Request"
        response.statusCode() == 400
        def code = response.body().jsonPath().getString("code")
        code in ["MALFORMED_REQUEST", "VALIDATION_ERROR"]
    }

    def "Scenario 5: Unknown event type returns invalid status per event"() {
        given: "a request with unknown event type"
        def path = "/v1/health-events"
        def body = '''
        {
            "events": [
                {
                    "idempotencyKey": "unknown-type-test-1",
                    "type": "UnknownEventType.v1",
                    "occurredAt": "2025-11-10T07:00:00Z",
                    "payload": {
                        "someField": "someValue"
                    }
                }
            ]
        }
        '''

        when: "I submit the request"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get 200 with all_invalid status"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "all_invalid"

        and: "event result shows invalid with error message"
        def eventResults = response.body().jsonPath().getList("events")
        eventResults.size() == 1
        eventResults[0].status == "invalid"
        eventResults[0].error != null
        eventResults[0].error.message != null
    }

    // ===========================================
    // Media Type Errors
    // ===========================================

    def "Scenario 6: Wrong Content-Type returns UNSUPPORTED_MEDIA_TYPE error"() {
        given: "a request with wrong Content-Type"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def body = '{"events": []}'
        def signature = generateHmacSignature("POST", path, timestamp, nonce, DEVICE_ID, body, SECRET_BASE64)

        when: "I submit the request with text/plain Content-Type"
        def response = RestAssured.given()
                .contentType("text/plain")
                .header("X-Device-Id", DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
                .post(path)
                .then()
                .extract()

        then: "I get 415 Unsupported Media Type"
        response.statusCode() == 415
        response.body().jsonPath().getString("code") == "UNSUPPORTED_MEDIA_TYPE"
        response.body().jsonPath().getString("message").toLowerCase().contains("application/json")
    }

    // ===========================================
    // Batch Size Validation
    // ===========================================

    def "Scenario 7: Large batch with 50 events processes successfully"() {
        given: "a request with 50 events with unique keys and times"
        def path = "/v1/health-events"
        def batchId = UUID.randomUUID().toString()
        def events = (1..50).collect { i ->
            def day = 10 + (i / 24).intValue()  // Spread across multiple days
            def hour = i % 24
            def minute = (i % 60)
            """
            {
                "idempotencyKey": "large-batch-${batchId}-${i}",
                "type": "StepsBucketedRecorded.v1",
                "occurredAt": "2025-11-${String.format('%02d', day)}T${String.format('%02d', hour)}:${String.format('%02d', minute)}:00Z",
                "payload": {
                    "bucketStart": "2025-11-${String.format('%02d', day)}T${String.format('%02d', hour)}:00:00Z",
                    "bucketEnd": "2025-11-${String.format('%02d', day)}T${String.format('%02d', hour)}:59:00Z",
                    "count": ${i * 10},
                    "originPackage": "com.test"
                }
            }
            """
        }.join(",")
        def body = """{"events": [${events}]}"""

        when: "I submit the request"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get 200 with success status"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
        response.body().jsonPath().getInt("summary.stored") == 50
    }

    // ===========================================
    // ErrorResponse Structure Validation
    // ===========================================

    def "Scenario 8: ErrorResponse contains all required fields (code, message, details)"() {
        given: "a request that will trigger an error"
        def path = "/v1/health-events"
        def invalidJson = '{"events": [{'

        when: "I submit the request"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, path, invalidJson)
                .post(path)
                .then()
                .extract()

        then: "response contains code and message fields"
        response.statusCode() == 400
        def jsonPath = response.body().jsonPath()
        jsonPath.getString("code") != null
        jsonPath.getString("message") != null

        and: "details field exists (can be empty list)"
        def details = jsonPath.getList("details")
        details != null
    }

    // ===========================================
    // Null/Missing Body
    // ===========================================

    def "Scenario 9: Missing request body returns error"() {
        given: "a POST request without body"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", path, timestamp, nonce, DEVICE_ID, "", SECRET_BASE64)

        when: "I submit the request without body"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .post(path)
                .then()
                .extract()

        then: "I get 400 Bad Request"
        response.statusCode() == 400
        response.body().jsonPath().getString("code") in ["MALFORMED_REQUEST", "VALIDATION_ERROR"]
    }

    def "Scenario 10: Null events field returns validation error"() {
        given: "a request with null events"
        def path = "/v1/health-events"
        def body = '{"events": null}'

        when: "I submit the request"
        def response = authenticatedPostRequestWithBody(DEVICE_ID, SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get 400 Bad Request with validation error"
        response.statusCode() == 400
        response.body().jsonPath().getString("code") == "VALIDATION_ERROR"
    }
}
