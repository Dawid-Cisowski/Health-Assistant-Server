package com.healthassistant

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Title

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Title("Feature: HMAC Authentication Security")
class HmacAuthenticationSpec extends BaseIntegrationSpec {

    static final String VALID_EVENTS_BODY = '''
    {
        "events": [
            {
                "idempotencyKey": "hmac-test-key-1",
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
    '''

    // ===========================================
    // Missing Headers Tests
    // ===========================================

    def "Scenario 1: Missing X-Device-Id header returns 401"() {
        given: "a request without X-Device-Id header"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", "someSignature")
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized with HMAC_AUTH_FAILED code"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        response.body().jsonPath().getString("message").contains("X-Device-Id")
    }

    def "Scenario 2: Missing X-Timestamp header returns 401"() {
        given: "a request without X-Timestamp header"
        def path = "/v1/health-events"
        def nonce = generateNonce()

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Nonce", nonce)
                .header("X-Signature", "someSignature")
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized with HMAC_AUTH_FAILED code"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        response.body().jsonPath().getString("message").contains("X-Timestamp")
    }

    def "Scenario 3: Missing X-Nonce header returns 401"() {
        given: "a request without X-Nonce header"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Signature", "someSignature")
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized with HMAC_AUTH_FAILED code"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        response.body().jsonPath().getString("message").contains("X-Nonce")
    }

    def "Scenario 4: Missing X-Signature header returns 401"() {
        given: "a request without X-Signature header"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized with HMAC_AUTH_FAILED code"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        response.body().jsonPath().getString("message").contains("X-Signature")
    }

    def "Scenario 5: All authentication headers missing returns 401"() {
        given: "a request without any authentication headers"
        def path = "/v1/health-events"

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
    }

    // ===========================================
    // Invalid Signature Tests
    // ===========================================

    def "Scenario 6: Wrong signature (random base64) returns 401"() {
        given: "a request with invalid random signature"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def invalidSignature = Base64.encoder.encodeToString("random-invalid-signature".bytes)

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", invalidSignature)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized with Invalid signature message"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        response.body().jsonPath().getString("message").toLowerCase().contains("invalid signature")
    }

    def "Scenario 7: Signature calculated with wrong secret returns 401"() {
        given: "a request with signature calculated using wrong secret"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def wrongSecretBase64 = Base64.encoder.encodeToString("wrong-secret".bytes)
        def signature = generateHmacSignature("POST", path, timestamp, nonce, TEST_DEVICE_ID, VALID_EVENTS_BODY, wrongSecretBase64)

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
    }

    def "Scenario 8: Signature calculated with different body returns 401"() {
        given: "a request with signature calculated for different body"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def differentBody = '{"events": []}'
        def signature = generateHmacSignature("POST", path, timestamp, nonce, TEST_DEVICE_ID, differentBody, TEST_SECRET_BASE64)

        when: "I submit the request with different body than signed"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
    }

    def "Scenario 9: Empty signature header returns 401"() {
        given: "a request with empty signature"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", "")
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
    }

    // ===========================================
    // Timestamp Validation Tests
    // ===========================================

    def "Scenario 10: Timestamp too old (>600s) returns 401"() {
        given: "a request with timestamp older than tolerance"
        def path = "/v1/health-events"
        def oldTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().minus(700, ChronoUnit.SECONDS))
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", path, oldTimestamp, nonce, TEST_DEVICE_ID, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", oldTimestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized with timestamp out of range message"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        response.body().jsonPath().getString("message").toLowerCase().contains("timestamp")
    }

    def "Scenario 11: Timestamp too far in future (>600s) returns 401"() {
        given: "a request with timestamp too far in future"
        def path = "/v1/health-events"
        def futureTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plus(700, ChronoUnit.SECONDS))
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", path, futureTimestamp, nonce, TEST_DEVICE_ID, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", futureTimestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        response.body().jsonPath().getString("message").toLowerCase().contains("timestamp")
    }

    def "Scenario 12: Timestamp at boundary (599s old) should be accepted"() {
        given: "a request with timestamp just within tolerance"
        def path = "/v1/health-events"
        def boundaryTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now().minus(599, ChronoUnit.SECONDS))
        def nonce = generateNonce()
        def body = createUniqueEventsBody("boundary-test-" + System.currentTimeMillis())
        def signature = generateHmacSignature("POST", path, boundaryTimestamp, nonce, TEST_DEVICE_ID, body, TEST_SECRET_BASE64)

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", boundaryTimestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
                .post(path)
                .then()
                .extract()

        then: "I get 200 OK - request is accepted"
        response.statusCode() == 200
    }

    def "Scenario 13: Invalid timestamp format returns 401"() {
        given: "a request with invalid timestamp format"
        def path = "/v1/health-events"
        def invalidTimestamp = "2025-01-15 15:30:00"  // Not ISO-8601
        def nonce = generateNonce()
        def signature = generateHmacSignature("POST", path, invalidTimestamp, nonce, TEST_DEVICE_ID, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", invalidTimestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized with invalid timestamp format message"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        response.body().jsonPath().getString("message").toLowerCase().contains("timestamp")
    }

    // ===========================================
    // Nonce Replay Tests
    // ===========================================

    def "Scenario 14: Same nonce replayed returns 401 on second request"() {
        given: "a valid request with specific nonce"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def fixedNonce = "fixed-nonce-" + UUID.randomUUID().toString()
        def body1 = createUniqueEventsBody("replay-test-1")
        def signature1 = generateHmacSignature("POST", path, timestamp, fixedNonce, TEST_DEVICE_ID, body1, TEST_SECRET_BASE64)

        when: "I submit the first request"
        def response1 = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", fixedNonce)
                .header("X-Signature", signature1)
                .body(body1)
                .post(path)
                .then()
                .extract()

        then: "first request succeeds"
        response1.statusCode() == 200

        when: "I submit a second request with same nonce"
        def newTimestamp = generateTimestamp()
        def body2 = createUniqueEventsBody("replay-test-2")
        def signature2 = generateHmacSignature("POST", path, newTimestamp, fixedNonce, TEST_DEVICE_ID, body2, TEST_SECRET_BASE64)

        def response2 = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", newTimestamp)
                .header("X-Nonce", fixedNonce)
                .header("X-Signature", signature2)
                .body(body2)
                .post(path)
                .then()
                .extract()

        then: "second request fails with nonce already used"
        response2.statusCode() == 401
        response2.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        response2.body().jsonPath().getString("message").toLowerCase().contains("nonce")
    }

    def "Scenario 15: Same nonce from different device is accepted"() {
        given: "a request from first device"
        def path = "/v1/health-events"
        def timestamp1 = generateTimestamp()
        def sharedNonce = "shared-nonce-" + UUID.randomUUID().toString()
        def body1 = createUniqueEventsBody("device1-test")
        def signature1 = generateHmacSignature("POST", path, timestamp1, sharedNonce, TEST_DEVICE_ID, body1, TEST_SECRET_BASE64)

        when: "first device submits request"
        def response1 = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", TEST_DEVICE_ID)
                .header("X-Timestamp", timestamp1)
                .header("X-Nonce", sharedNonce)
                .header("X-Signature", signature1)
                .body(body1)
                .post(path)
                .then()
                .extract()

        then: "first request succeeds"
        response1.statusCode() == 200

        when: "second device submits request with same nonce"
        def timestamp2 = generateTimestamp()
        def body2 = createUniqueEventsBody("device2-test")
        def signature2 = generateHmacSignature("POST", path, timestamp2, sharedNonce, "different-device-id", body2, DIFFERENT_DEVICE_SECRET_BASE64)

        def response2 = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", "different-device-id")
                .header("X-Timestamp", timestamp2)
                .header("X-Nonce", sharedNonce)
                .header("X-Signature", signature2)
                .body(body2)
                .post(path)
                .then()
                .extract()

        then: "second device request succeeds (nonce is per-device)"
        response2.statusCode() == 200
    }

    // ===========================================
    // Device Validation Tests
    // ===========================================

    def "Scenario 16: Unknown device ID returns 401"() {
        given: "a request with unknown device ID"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()
        def unknownDeviceId = "unknown-device-xyz"
        def signature = generateHmacSignature("POST", path, timestamp, nonce, unknownDeviceId, VALID_EVENTS_BODY, TEST_SECRET_BASE64)

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", unknownDeviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized (generic message to prevent device enumeration)"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
        // Security: Don't reveal whether device ID exists - use generic message
        response.body().jsonPath().getString("message") != null
    }

    def "Scenario 17: Empty device ID returns 401"() {
        given: "a request with empty device ID"
        def path = "/v1/health-events"
        def timestamp = generateTimestamp()
        def nonce = generateNonce()

        when: "I submit the request"
        def response = RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", "")
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", "someSignature")
                .body(VALID_EVENTS_BODY)
                .post(path)
                .then()
                .extract()

        then: "I get 401 Unauthorized"
        response.statusCode() == 401
        response.body().jsonPath().getString("code") == "HMAC_AUTH_FAILED"
    }

    // ===========================================
    // Request Body Tests
    // ===========================================

    def "Scenario 18: POST with valid body and correct signature succeeds"() {
        given: "a properly authenticated POST request"
        def path = "/v1/health-events"
        def body = createUniqueEventsBody("valid-post-test")

        when: "I submit the request"
        def response = authenticatedPostRequestWithBody(TEST_DEVICE_ID, TEST_SECRET_BASE64, path, body)
                .post(path)
                .then()
                .extract()

        then: "I get 200 OK"
        response.statusCode() == 200
        response.body().jsonPath().getString("status") == "success"
    }

    def "Scenario 19: GET request with valid authentication succeeds"() {
        given: "a properly authenticated GET request"
        def date = "2025-11-10"
        def path = "/v1/daily-summaries/${date}"

        when: "I submit the GET request"
        def response = authenticatedGetRequest(TEST_DEVICE_ID, TEST_SECRET_BASE64, path)
                .get(path)
                .then()
                .extract()

        then: "I get either 200 OK or 404 Not Found (but not 401)"
        response.statusCode() in [200, 404]
    }

    // ===========================================
    // Helper Methods
    // ===========================================

    private String createUniqueEventsBody(String keyPrefix) {
        return """
        {
            "events": [
                {
                    "idempotencyKey": "${keyPrefix}-${UUID.randomUUID()}",
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
    }
}
