package com.healthassistant

import com.healthassistant.application.ingestion.HealthEventJpaRepository
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = [com.healthassistant.HealthAssistantApplication])
@ActiveProfiles("test")
abstract class BaseIntegrationSpec extends Specification {

    @LocalServerPort
    int port

    @Autowired
    HealthEventJpaRepository eventRepository

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

    String generateTimestamp() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    }

    String generateNonce() {
        return UUID.randomUUID().toString().toLowerCase()
    }

    def authenticatedRequest(String deviceId, String secretBase64, String body) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", "/v1/health-events", timestamp, nonce, deviceId, body, secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
    }

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

    String createExerciseSessionEvent(String idempotencyKey, String occurredAt = "2025-11-10T10:00:00Z") {
        return """
        {
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "ExerciseSessionRecorded.v1",
                    "occurredAt": "${occurredAt}",
                    "payload": {
                        "sessionId": "e4210819-5708-3835-bcbb-2776e037e258",
                        "type": "other_0",
                        "start": "2025-11-10T09:03:15Z",
                        "end": "2025-11-10T10:03:03Z",
                        "durationMinutes": 59,
                        "distanceMeters": "5838.7",
                        "steps": 13812,
                        "avgSpeedMetersPerSecond": "1.65",
                        "avgHr": 83,
                        "maxHr": 123,
                        "originPackage": "com.heytap.health.international"
                    }
                }
            ]
        }
        """.stripIndent().trim()
    }

    String createActiveMinutesEvent(String idempotencyKey, String occurredAt = "2025-11-10T14:00:00Z") {
        return """
        {
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "ActiveMinutesRecorded.v1",
                    "occurredAt": "${occurredAt}",
                    "payload": {
                        "bucketStart": "2025-11-10T13:00:00Z",
                        "bucketEnd": "2025-11-10T14:00:00Z",
                        "activeMinutes": 25,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
    }

    String createActiveCaloriesEvent(String idempotencyKey, String occurredAt = "2025-11-10T16:00:00Z") {
        return """
        {
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "ActiveCaloriesBurnedRecorded.v1",
                    "occurredAt": "${occurredAt}",
                    "payload": {
                        "bucketStart": "2025-11-10T15:00:00Z",
                        "bucketEnd": "2025-11-10T16:00:00Z",
                        "energyKcal": 125.5,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
    }

    String createSleepSessionEvent(String idempotencyKey, String occurredAt = "2025-11-10T08:00:00Z") {
        return """
        {
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "${occurredAt}",
                    "payload": {
                        "sleepStart": "2025-11-10T00:30:00Z",
                        "sleepEnd": "2025-11-10T08:00:00Z",
                        "totalMinutes": 450,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """.stripIndent().trim()
    }
}

