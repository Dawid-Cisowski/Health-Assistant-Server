package com.healthassistant

import com.healthassistant.repository.HealthEventRepository
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.Shared
import spock.lang.Specification

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Base specification for integration tests.
 * - Starts PostgreSQL in Testcontainers
 * - Starts Spring Boot application
 * - Provides helper methods for HMAC signing and API calls
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = [com.healthassistant.HealthAssistantApplication])
@ActiveProfiles("test")
abstract class BaseIntegrationSpec extends Specification {

    @LocalServerPort
    int port

    @Autowired
    HealthEventRepository eventRepository

    @Shared
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)

    static {
        // Start testcontainers
        postgres.start()

        // Configure properties using System.setProperty (works better with Spock)
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl())
        System.setProperty("spring.datasource.username", postgres.getUsername())
        System.setProperty("spring.datasource.password", postgres.getPassword())
        System.setProperty("app.hmac.devices-json", '{"test-device":"dGVzdC1zZWNyZXQtMTIz"}')
        System.setProperty("app.hmac.tolerance-seconds", "600")
        System.setProperty("app.nonce.cache-ttl-seconds", "600")
    }

    def setupSpec() {
        // Runs once before all tests in the specification
    }

    def setup() {
        // Runs before each test
        RestAssured.port = port
        RestAssured.baseURI = "http://localhost"
        if (eventRepository != null) {
            eventRepository.deleteAll() // Clean DB before each test
        }
    }

    def cleanup() {
        // Runs after each test
        if (eventRepository != null) {
            eventRepository.deleteAll() // Clean DB after each test
        }
    }

    /**
     * Generate HMAC signature for request
     */
    String generateHmacSignature(String method, String path, String timestamp, String nonce, String deviceId, String body, String secretBase64) {
        // Decode base64 secret
        byte[] secret = Base64.decoder.decode(secretBase64)

        // Build canonical string
        String canonicalString = [method, path, timestamp, nonce, deviceId, body].join('\n')

        // Calculate HMAC-SHA256
        Mac mac = Mac.getInstance("HmacSHA256")
        SecretKeySpec keySpec = new SecretKeySpec(secret, "HmacSHA256")
        mac.init(keySpec)
        byte[] hmacBytes = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8))

        // Return base64 encoded signature
        return Base64.encoder.encodeToString(hmacBytes)
    }

    /**
     * Generate current timestamp in ISO-8601 UTC format
     */
    String generateTimestamp() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    }

    /**
     * Generate random nonce (UUID)
     */
    String generateNonce() {
        return UUID.randomUUID().toString().toLowerCase()
    }

    /**
     * Build authenticated request with HMAC headers
     */
    def authenticatedRequest(String deviceId, String secretBase64, String body) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", "/v1/ingest/events", timestamp, nonce, deviceId, body, secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
    }

    /**
     * Create simple test event payload
     */
    String createStepsEvent(String idempotencyKey, String occurredAt = "2025-11-10T07:00:00Z") {
        return """
        {
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "${occurredAt}",
                    "payload": {
                        "bucketStart": "2025-11-10T06:00:00Z",
                        "bucketEnd": "2025-11-10T07:00:00Z",
                        "count": 742,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
    }

    /**
     * Create heart rate event payload
     */
    String createHeartRateEvent(String idempotencyKey, String occurredAt = "2025-11-10T07:15:00Z") {
        return """
        {
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "HeartRateSummaryRecorded.v1",
                    "occurredAt": "${occurredAt}",
                    "payload": {
                        "bucketStart": "2025-11-10T07:00:00Z",
                        "bucketEnd": "2025-11-10T07:15:00Z",
                        "avg": 78.3,
                        "min": 61,
                        "max": 115,
                        "samples": 46,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
    }
}

