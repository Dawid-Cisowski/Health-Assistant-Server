package com.healthassistant

import com.healthassistant.healthevents.HealthEventJpaRepository
import com.healthassistant.dailysummary.DailySummaryJpaRepository
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
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

import static com.github.tomakehurst.wiremock.client.WireMock.*

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

    @Shared
    static WireMockServer wireMockServer = new WireMockServer(0)

    static {
        // Start testcontainers
        postgres.start()
        wireMockServer.start()

        // Configure properties using System.setProperty (works better with Spock)
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl())
        System.setProperty("spring.datasource.username", postgres.getUsername())
        System.setProperty("spring.datasource.password", postgres.getPassword())
        System.setProperty("app.hmac.devices-json", '{"test-device":"dGVzdC1zZWNyZXQtMTIz"}')
        System.setProperty("app.hmac.tolerance-seconds", "600")
        System.setProperty("app.nonce.cache-ttl-seconds", "600")
        
        // Configure WireMock URLs for Google Fit API and OAuth
        String wireMockUrl = "http://localhost:${wireMockServer.port()}"
        System.setProperty("app.google-fit.api-url", wireMockUrl)
        System.setProperty("app.google-fit.oauth-url", wireMockUrl)
        System.setProperty("app.google-fit.client-id", "test-client-id")
        System.setProperty("app.google-fit.client-secret", "test-client-secret")
        System.setProperty("app.google-fit.refresh-token", "test-refresh-token")
    }

    def setupSpec() {
        // Runs once before all tests in the specification
    }

    def setup() {
        // Runs before each test
        RestAssured.port = port
        RestAssured.baseURI = "http://localhost"
        
        // Reset WireMock
        wireMockServer.resetAll()
        
        // Setup default OAuth mock
        setupOAuthMock()
        
        if (eventRepository != null) {
            eventRepository.deleteAll() // Clean DB before each test
        }
    }
    
    void setupOAuthMock() {
        wireMockServer.stubFor(post(urlEqualTo("/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('{"access_token":"test-access-token","expires_in":3600,"token_type":"Bearer"}')))
    }
    
    void setupGoogleFitApiMock(String responseBody) {
        wireMockServer.stubFor(post(urlPathMatching("/users/me/dataset:aggregate"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody))
                .atPriority(1))
    }

    void setupGoogleFitApiMockMultipleTimes(String responseBody, int times) {
        wireMockServer.stubFor(post(urlPathMatching("/users/me/dataset:aggregate"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody))
                .atPriority(10))
    }
    
    void setupGoogleFitSessionsApiMock(String responseBody) {
        wireMockServer.stubFor(get(urlPathMatching("/users/me/sessions"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody))
                .atPriority(1))
    }

    void setupGoogleFitSessionsApiMockMultipleTimes(String responseBody, int times) {
        wireMockServer.stubFor(get(urlPathMatching("/users/me/sessions"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody))
                .atPriority(10))
    }
    
    String createGoogleFitResponseWithSteps(long startTimeMillis, long endTimeMillis, long steps) {
        return """
        {
            "bucket": [{
                "startTimeMillis": ${startTimeMillis},
                "endTimeMillis": ${endTimeMillis},
                "dataset": [{
                    "dataSourceId": "derived:com.google.step_count.delta:com.google.android.gms:aggregated",
                    "point": [{
                        "startTimeNanos": "${startTimeMillis * 1_000_000}",
                        "endTimeNanos": "${endTimeMillis * 1_000_000}",
                        "value": [{
                            "intVal": ${steps}
                        }]
                    }]
                }]
            }]
        }
        """
    }
    
    String createGoogleFitResponseWithMultipleDataTypes(long startTimeMillis, long endTimeMillis, long steps, double distance, double calories, int heartRate) {
        return """
        {
            "bucket": [{
                "startTimeMillis": ${startTimeMillis},
                "endTimeMillis": ${endTimeMillis},
                "dataset": [
                    {
                        "dataSourceId": "derived:com.google.step_count.delta:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${startTimeMillis * 1_000_000}",
                            "endTimeNanos": "${endTimeMillis * 1_000_000}",
                            "value": [{"intVal": ${steps}}]
                        }]
                    },
                    {
                        "dataSourceId": "derived:com.google.distance.delta:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${startTimeMillis * 1_000_000}",
                            "endTimeNanos": "${endTimeMillis * 1_000_000}",
                            "value": [{"fpVal": ${distance}}]
                        }]
                    },
                    {
                        "dataSourceId": "derived:com.google.calories.expended:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${startTimeMillis * 1_000_000}",
                            "endTimeNanos": "${endTimeMillis * 1_000_000}",
                            "value": [{"fpVal": ${calories}}]
                        }]
                    },
                    {
                        "dataSourceId": "derived:com.google.heart_rate.bpm:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${startTimeMillis * 1_000_000}",
                            "endTimeNanos": "${endTimeMillis * 1_000_000}",
                            "value": [{"intVal": ${heartRate}}]
                        }]
                    }
                ]
            }]
        }
        """
    }
    
    String createEmptyGoogleFitResponse() {
        return '{"bucket": []}'
    }
    
    String createGoogleFitSessionsResponseWithSleep(long startTimeMillis, long endTimeMillis, String sessionId = "sleep-session-123") {
        return """
        {
            "session": [{
                "id": "${sessionId}",
                "activityType": 72,
                "startTimeMillis": ${startTimeMillis},
                "endTimeMillis": ${endTimeMillis},
                "packageName": "com.google.android.apps.fitness"
            }]
        }
        """
    }
    
    String createEmptyGoogleFitSessionsResponse() {
        return '{"session": []}'
    }

    String createGoogleFitSessionsResponseWithWalking(long startTimeMillis, long endTimeMillis, String sessionId = "walk-session-123") {
        return """
        {
            "session": [{
                "id": "${sessionId}",
                "name": "Chodzenie",
                "activityType": 108,
                "startTimeMillis": ${startTimeMillis},
                "endTimeMillis": ${endTimeMillis},
                "application": {
                    "packageName": "com.heytap.health.international"
                }
            }]
        }
        """
    }

    void setupGoogleFitApiMockForSession(long startTimeMillis, long endTimeMillis, long steps, double distance, double calories, List<Integer> heartRates) {
        // This stub should have higher priority to match first for session-specific requests
        def bucketCount = (int) ((endTimeMillis - startTimeMillis) / 60000)
        def buckets = []
        
        for (int i = 0; i < bucketCount; i++) {
            def bucketStart = startTimeMillis + (i * 60000)
            def bucketEnd = bucketStart + 60000
            def stepsPerBucket = (int) (steps / bucketCount)
            def distancePerBucket = distance / bucketCount
            def caloriesPerBucket = calories / bucketCount
            
            def datasets = []
            if (stepsPerBucket > 0) {
                datasets.add("""
                    {
                        "dataSourceId": "derived:com.google.step_count.delta:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${bucketStart * 1_000_000}",
                            "endTimeNanos": "${bucketEnd * 1_000_000}",
                            "value": [{"intVal": ${stepsPerBucket}}]
                        }]
                    }
                """)
            }
            if (distancePerBucket > 0) {
                datasets.add("""
                    {
                        "dataSourceId": "derived:com.google.distance.delta:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${bucketStart * 1_000_000}",
                            "endTimeNanos": "${bucketEnd * 1_000_000}",
                            "value": [{"fpVal": ${distancePerBucket}}]
                        }]
                    }
                """)
            }
            if (caloriesPerBucket > 0) {
                datasets.add("""
                    {
                        "dataSourceId": "derived:com.google.calories.expended:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${bucketStart * 1_000_000}",
                            "endTimeNanos": "${bucketEnd * 1_000_000}",
                            "value": [{"fpVal": ${caloriesPerBucket}}]
                        }]
                    }
                """)
            }
            if (heartRates != null && !heartRates.isEmpty() && i < heartRates.size()) {
                datasets.add("""
                    {
                        "dataSourceId": "derived:com.google.heart_rate.bpm:com.google.android.gms:aggregated",
                        "point": [{
                            "startTimeNanos": "${bucketStart * 1_000_000}",
                            "endTimeNanos": "${bucketEnd * 1_000_000}",
                            "value": [{"intVal": ${heartRates[i]}}]
                        }]
                    }
                """)
            }
            
            buckets.add("""
                {
                    "startTimeMillis": ${bucketStart},
                    "endTimeMillis": ${bucketEnd},
                    "dataset": [${datasets.join(',')}]
                }
            """)
        }
        
        def responseBody = """
        {
            "bucket": [${buckets.join(',')}]
        }
        """
        
        wireMockServer.stubFor(post(urlPathMatching("/users/me/dataset:aggregate"))
                .withHeader("Authorization", matching("Bearer .+"))
                .withRequestBody(matching(".*\"startTimeMillis\"\\s*:\\s*${startTimeMillis}.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody))
                .atPriority(5))
    }
    
    void setupGoogleFitApiMockError(int statusCode, String errorBody) {
        wireMockServer.stubFor(post(urlPathMatching("/users/me/dataset:aggregate"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorBody)))
    }

    void setupGoogleFitSessionsApiMockError(int statusCode, String errorBody) {
        wireMockServer.stubFor(get(urlPathMatching("/users/me/sessions"))
                .withHeader("Authorization", matching("Bearer .+"))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorBody)))
    }

    void setupOAuthMockError(int statusCode, String errorBody) {
        wireMockServer.stubFor(post(urlEqualTo("/token"))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorBody)))
    }

    def cleanup() {
        // Runs after each test
        if (eventRepository != null) {
            eventRepository.deleteAll()
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

    def authenticatedGetRequest(String deviceId, String secretBase64, String path) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("GET", path, timestamp, nonce, deviceId, "", secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
    }

    def authenticatedPostRequest(String deviceId, String secretBase64, String path) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
    }

    def authenticatedPostRequestWithBody(String deviceId, String secretBase64, String path, String body) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        // Note: body is not included in signature as per server implementation
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, "", secretBase64)

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

    String createWalkingSessionEvent(String idempotencyKey, String occurredAt = "2025-11-10T10:00:00Z") {
        return """
        {
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "WalkingSessionRecorded.v1",
                    "occurredAt": "${occurredAt}",
                    "payload": {
                        "sessionId": "health_platform:888c4cb9-1034-324e-b093-8a260f19e7a5",
                        "name": "Chodzenie",
                        "start": "2025-11-10T09:03:15Z",
                        "end": "2025-11-10T10:03:03Z",
                        "durationMinutes": 59,
                        "totalSteps": 13812,
                        "totalDistanceMeters": 5839,
                        "totalCalories": 125,
                        "avgHeartRate": 83,
                        "maxHeartRate": 123,
                        "heartRateSamples": [75, 80, 85, 90, 88],
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

