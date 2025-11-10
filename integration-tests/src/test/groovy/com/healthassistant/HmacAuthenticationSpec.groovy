package com.healthassistant

import io.restassured.RestAssured
import spock.lang.Title

import java.time.Instant

/**
 * Integration tests for HMAC Authentication (Feature 1)
 */
@Title("Feature 1: HMAC Authentication")
class HmacAuthenticationSpec extends BaseIntegrationSpec {

    def "Scenario 1.1: Successful authentication with valid HMAC signature"() {
        given: "device 'test-device' exists in configuration with valid secret"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz" // base64("test-secret-123")

        and: "valid event payload"
        def idempotencyKey = "user1|steps|${System.currentTimeMillis()}|com.test"
        def body = createStepsEvent(idempotencyKey)

        when: "I POST to /v1/ingest/events with valid HMAC signature"
        def response = authenticatedRequest(deviceId, secretBase64, body)
                .post("/v1/ingest/events")
                .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "response should contain results array"
        def responseBody = response.body().jsonPath()
        responseBody.getList("results").size() == 1

        and: "result status should be 'stored'"
        responseBody.getString("results[0].status") == "stored"

        and: "result should have index 0"
        responseBody.getInt("results[0].index") == 0

        and: "result should contain eventId"
        def eventId = responseBody.getString("results[0].eventId")
        eventId != null
        eventId.startsWith("evt_")

        and: "event should be saved in database"
        def savedEvents = eventRepository.findAll()
        savedEvents.size() == 1

        and: "saved event should have correct data"
        def savedEvent = savedEvents[0]
        savedEvent.eventId == eventId
        savedEvent.idempotencyKey == idempotencyKey
        savedEvent.eventType == "StepsBucketedRecorded.v1"
        savedEvent.deviceId == deviceId

        and: "saved event payload should contain expected data"
        savedEvent.payload.bucketStart == "2025-11-10T06:00:00Z"
        savedEvent.payload.bucketEnd == "2025-11-10T07:00:00Z"
        savedEvent.payload.count == 742
        savedEvent.payload.originPackage == "com.google.android.apps.fitness"

        and: "saved event should have timestamps"
        savedEvent.occurredAt != null
        savedEvent.createdAt != null
    }

    def "Scenario 1.2: Authentication fails with invalid HMAC signature"() {
        given: "device 'test-device' exists in configuration"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "valid event payload"
        def idempotencyKey = "user1|steps|${System.currentTimeMillis()}|com.test"
        def body = createStepsEvent(idempotencyKey)

        and: "INVALID signature is calculated with wrong secret"
        def wrongSecret = "d3Jvbmctc2VjcmV0" // base64("wrong-secret")
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def invalidSignature = generateHmacSignature("POST", "/v1/ingest/events", timestamp, nonce, deviceId, body, wrongSecret)

        when: "I POST to /v1/ingest/events with invalid signature"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", invalidSignature)
                .body(body)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "response status should be 401 UNAUTHORIZED"
        response.statusCode() == 401

        and: "response should contain error information"
        def responseBody = response.body().jsonPath()
        responseBody.getString("code") == "HMAC_AUTH_FAILED"
        responseBody.getString("message") == "Invalid signature"
        responseBody.getList("details") != null

        and: "no events should be saved in database"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 1.3: Authentication fails with unknown device ID"() {
        given: "unknown device ID that is not configured"
        def unknownDeviceId = "unknown-device-123"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "valid event payload"
        def idempotencyKey = "user1|steps|${System.currentTimeMillis()}|com.test"
        def body = createStepsEvent(idempotencyKey)

        and: "signature calculated with test secret"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", "/v1/ingest/events", timestamp, nonce, unknownDeviceId, body, secretBase64)

        when: "I POST to /v1/ingest/events with unknown device ID"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", unknownDeviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "response status should be 401 UNAUTHORIZED"
        response.statusCode() == 401

        and: "response should indicate unknown device"
        def responseBody = response.body().jsonPath()
        responseBody.getString("code") == "HMAC_AUTH_FAILED"
        responseBody.getString("message").contains("Unknown device")

        and: "no events should be saved in database"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 1.4: Authentication fails with expired timestamp"() {
        given: "device 'test-device' exists in configuration"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "valid event payload"
        def idempotencyKey = "user1|steps|${System.currentTimeMillis()}|com.test"
        def body = createStepsEvent(idempotencyKey)

        and: "timestamp is older than tolerance window (15 minutes ago)"
        def expiredTimestamp = Instant.now().minusSeconds(900).toString() // 15 minutes ago
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", "/v1/ingest/events", expiredTimestamp, nonce, deviceId, body, secretBase64)

        when: "I POST to /v1/ingest/events with expired timestamp"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", expiredTimestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "response status should be 401 UNAUTHORIZED"
        response.statusCode() == 401

        and: "response should indicate timestamp is out of range"
        def responseBody = response.body().jsonPath()
        responseBody.getString("code") == "HMAC_AUTH_FAILED"
        responseBody.getString("message").contains("Timestamp out of acceptable range")

        and: "no events should be saved in database"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 1.5: Authentication fails with replay attack (duplicate nonce)"() {
        given: "device 'test-device' exists in configuration"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "shared timestamp and nonce for both requests"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()

        and: "first request with specific payload and nonce"
        def idempotencyKey1 = "user1|steps|${System.currentTimeMillis()}|com.test|1"
        def body1 = createStepsEvent(idempotencyKey1)
        def signature1 = generateHmacSignature("POST", "/v1/ingest/events", timestamp, nonce, deviceId, body1, secretBase64)

        when: "I send first request"
        def response1 = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature1)
                .body(body1)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "first request should succeed"
        response1.statusCode() == 202

        when: "I try to replay the EXACT same nonce (replay attack)"
        // Use different body but SAME nonce - this simulates replay attack
        def idempotencyKey2 = "user1|steps|${System.currentTimeMillis()}|com.test|2"
        def body2 = createStepsEvent(idempotencyKey2)
        def signature2 = generateHmacSignature("POST", "/v1/ingest/events", timestamp, nonce, deviceId, body2, secretBase64)
        
        def response2 = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce) // SAME nonce as first request!
                .header("X-Signature", signature2)
                .body(body2)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "second request should be rejected as replay attack"
        response2.statusCode() == 401

        and: "response should indicate nonce reuse"
        def responseBody = response2.body().jsonPath()
        responseBody.getString("code") == "HMAC_AUTH_FAILED"
        responseBody.getString("message").contains("Nonce already used")

        and: "only first event should be saved"
        eventRepository.findAll().size() == 1
    }

    def "Scenario 1.6: Authentication fails with missing headers"() {
        given: "valid event payload"
        def body = createStepsEvent("test-key-${System.currentTimeMillis()}")

        when: "I POST to /v1/ingest/events without authentication headers"
        def response = RestAssured.given()
                .contentType("application/json")
                .body(body)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "response status should be 401 UNAUTHORIZED"
        response.statusCode() == 401

        and: "response should indicate missing headers"
        def responseBody = response.body().jsonPath()
        responseBody.getString("code") == "HMAC_AUTH_FAILED"
        // Should fail on first missing header check
        responseBody.getString("message") != null

        and: "no events should be saved in database"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 1.7: Authentication with timestamp at tolerance boundary (exactly 10 minutes ago)"() {
        given: "device 'test-device' exists in configuration"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "timestamp is 599 seconds (just under 10 minutes) in the past - within tolerance"
        def timestamp = Instant.now().minusSeconds(599).toString()

        and: "valid event payload"
        def idempotencyKey = "boundary|test|${System.currentTimeMillis()}"
        def body = createStepsEvent(idempotencyKey)

        and: "valid HMAC signature for that timestamp"
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", "/v1/ingest/events", timestamp, nonce, deviceId, body, secretBase64)

        when: "I POST to /v1/ingest/events"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "response status should be 202 ACCEPTED (within tolerance)"
        response.statusCode() == 202

        and: "event should be stored"
        eventRepository.findAll().size() == 1
    }

    def "Scenario 1.8: Authentication with future timestamp within tolerance"() {
        given: "device 'test-device' exists in configuration"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "timestamp is 5 minutes in the future (within tolerance)"
        def timestamp = Instant.now().plusSeconds(300).toString()

        and: "valid event payload"
        def idempotencyKey = "future|test|${System.currentTimeMillis()}"
        def body = createStepsEvent(idempotencyKey)

        and: "valid HMAC signature"
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", "/v1/ingest/events", timestamp, nonce, deviceId, body, secretBase64)

        when: "I POST to /v1/ingest/events"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "response status should be 202 ACCEPTED"
        response.statusCode() == 202

        and: "event should be stored"
        eventRepository.findAll().size() == 1
    }

    def "Scenario 1.9: Invalid timestamp format"() {
        given: "device exists in configuration"
        def deviceId = "test-device"

        and: "X-Timestamp header value is invalid format"
        def invalidTimestamp = "2025-11-10 12:00:00" // Space instead of T

        and: "valid event payload"
        def body = createStepsEvent("invalid|timestamp|${System.currentTimeMillis()}")

        when: "I POST to /v1/ingest/events"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", invalidTimestamp)
                .header("X-Nonce", generateNonce())
                .header("X-Signature", "dummy-signature")
                .body(body)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "response status should be 401 UNAUTHORIZED"
        response.statusCode() == 401

        and: "error message should contain 'Invalid timestamp format'"
        def responseBody = response.body().jsonPath()
        responseBody.getString("code") == "HMAC_AUTH_FAILED"
        responseBody.getString("message").contains("Invalid timestamp format")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 1.10: Timestamp too far in future (outside tolerance)"() {
        given: "device exists in configuration"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "timestamp is 11 minutes in the future (outside tolerance)"
        def futureTimestamp = Instant.now().plusSeconds(660).toString()

        and: "valid event payload"
        def idempotencyKey = "future|invalid|${System.currentTimeMillis()}"
        def body = createStepsEvent(idempotencyKey)

        and: "valid HMAC signature for that future timestamp"
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", "/v1/ingest/events", futureTimestamp, nonce, deviceId, body, secretBase64)

        when: "I POST to /v1/ingest/events"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", futureTimestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "response status should be 401 UNAUTHORIZED"
        response.statusCode() == 401

        and: "error message should contain 'Timestamp out of acceptable range'"
        def responseBody = response.body().jsonPath()
        responseBody.getString("message").contains("Timestamp out of acceptable range")

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }

    def "Scenario 1.11: Canonical string mismatch - wrong body"() {
        given: "device exists in configuration"
        def deviceId = "test-device"
        def secretBase64 = "dGVzdC1zZWNyZXQtMTIz"

        and: "HMAC signature is calculated for body A"
        def bodyA = createStepsEvent("body|a|${System.currentTimeMillis()}")
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", "/v1/ingest/events", timestamp, nonce, deviceId, bodyA, secretBase64)

        and: "actual request body is B (different)"
        def bodyB = createStepsEvent("body|b|${System.currentTimeMillis()}")

        when: "I POST to /v1/ingest/events with body B but signature for body A"
        def response = RestAssured.given()
                .contentType("application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature) // Signature for bodyA!
                .body(bodyB) // But sending bodyB!
        .when()
                .post("/v1/ingest/events")
        .then()
                .extract()

        then: "response status should be 401 UNAUTHORIZED"
        response.statusCode() == 401

        and: "error message should contain 'Invalid signature'"
        response.body().jsonPath().getString("message") == "Invalid signature"

        and: "no events stored"
        eventRepository.findAll().size() == 0
    }
}

