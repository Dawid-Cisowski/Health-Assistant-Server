package com.healthassistant

import com.healthassistant.activity.api.ActivityFacade
import com.healthassistant.bodymeasurements.api.BodyMeasurementsFacade
import com.healthassistant.calories.api.CaloriesFacade
import com.healthassistant.dailysummary.api.DailySummaryFacade
import com.healthassistant.healthevents.api.HealthEventsFacade
import com.healthassistant.heartrate.api.HeartRateFacade
import com.healthassistant.meals.api.MealsFacade
import com.healthassistant.sleep.api.SleepFacade
import com.healthassistant.steps.api.StepsFacade
import com.healthassistant.weight.api.WeightFacade
import com.healthassistant.workout.api.WorkoutFacade
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.Shared
import spock.lang.Specification

import org.awaitility.Awaitility

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = [com.healthassistant.HealthAssistantApplication])
@Import([TestChatModelConfiguration, TestAsyncConfiguration])
@ActiveProfiles("test")
abstract class BaseIntegrationSpec extends Specification {

    // Each test class gets a unique device ID based on class name for parallel test isolation
    static final String TEST_SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    static final String DIFFERENT_DEVICE_SECRET_BASE64 = "ZGlmZmVyZW50LXNlY3JldC0xMjM="

    // Legacy constant for backward compatibility - use getTestDeviceId() instead
    static final String TEST_DEVICE_ID = "test-device"

    // Generate unique device ID per test class for parallel isolation
    String getTestDeviceId() {
        return "test-${this.class.simpleName.toLowerCase().hashCode().abs() % 10000}"
    }

    @LocalServerPort
    int port

    @Autowired
    HealthEventsFacade healthEventsFacade

    @Autowired
    StepsFacade stepsFacade

    @Autowired
    SleepFacade sleepFacade

    @Autowired
    WorkoutFacade workoutFacade

    @Autowired
    MealsFacade mealsFacade

    @Autowired
    CaloriesFacade caloriesFacade

    @Autowired
    ActivityFacade activityFacade

    @Autowired
    DailySummaryFacade dailySummaryFacade

    @Autowired
    WeightFacade weightFacade

    @Autowired
    HeartRateFacade heartRateFacade

    @Autowired
    BodyMeasurementsFacade bodyMeasurementsFacade

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

        // Generate HMAC devices config with all test devices for parallel execution
        // Named devices used by test specs (all using same secret for simplicity)
        def namedDevices = [
            "test-device",
            // Projection specs
            "test-steps", "test-meals", "test-workout-proj", "test-workout-import",
            "test-sleep-proj", "test-calories", "test-activity", "test-daily",
            "test-workout", "test-sleep-import", "test-meal-import", "test-assistant",
            "test-batch", "test-ai-summary", "test-ai-cache", "test-convo", "test-admin",
            // Event validation specs
            "test-steps-valid", "test-sleep-valid", "test-meal-event",
            "test-heartrate", "test-active-cal", "test-active-min",
            "test-distance", "test-walking", "test-exception",
            // Import and other specs
            "test-meal-draft", "test-gfit",
            // Concurrency specs
            "test-optlock",
            // Exercise catalog specs
            "test-exercises",
            // Routine specs
            "test-routines",
            // Compensation events specs
            "test-deletion", "test-correction",
            // Meal CRUD specs
            "test-meal-crud",
            // Workout CRUD specs
            "test-workout-crud",
            // Cross-module and edge case specs
            "test-cross-module", "test-timezone", "test-ratelimit", "test-mealedge",
            // Personal records specs
            "test-pr",
            // Weight specs
            "test-weight", "test-weight-valid",
            // Body measurements specs
            "test-body-meas", "test-body-meas-valid",
            // Energy requirements specs
            "test-energy",
            // Guardrail specs
            "test-guardrail",
            // AI Metrics specs
            "test-ai-metrics",
            // Reprojection specs
            "test-reproject",
            // Heart rate projection specs
            "test-hr-proj",
            // Conversation cleanup specs
            "test-cleanup",
            // Conversation API specs
            "test-convo-api",
            // Reports specs
            "test-reports"
        ]
        def devicesMap = new StringBuilder('{')
        def first = true
        namedDevices.each { device ->
            if (!first) devicesMap.append(',')
            first = false
            devicesMap.append('"').append(device).append('":"dGVzdC1zZWNyZXQtMTIz"')
        }
        // Add different-device-id with different secret (for authentication tests)
        devicesMap.append(',"different-device-id":"ZGlmZmVyZW50LXNlY3JldC0xMjM="')
        devicesMap.append('}')

        // Configure properties using System.setProperty (works better with Spock)
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl())
        System.setProperty("spring.datasource.username", postgres.getUsername())
        System.setProperty("spring.datasource.password", postgres.getPassword())
        System.setProperty("app.hmac.devices-json", devicesMap.toString())
        System.setProperty("app.hmac.tolerance-seconds", "600")
        System.setProperty("app.nonce.cache-ttl-seconds", "600")

        // Configure WireMock URLs for Google Fit API and OAuth
        String wireMockUrl = "http://localhost:${wireMockServer.port()}"
        System.setProperty("app.google-fit.api-url", wireMockUrl)
        System.setProperty("app.google-fit.oauth-url", wireMockUrl)
        System.setProperty("app.google-fit.client-id", "test-client-id")
        System.setProperty("app.google-fit.client-secret", "test-client-secret")
        System.setProperty("app.google-fit.refresh-token", "test-refresh-token")

        // Configure Spring AI Google GenAI to use WireMock
        System.setProperty("spring.ai.google.genai.base-url", wireMockUrl)
        System.setProperty("spring.ai.google.genai.api-key", "test-gemini-key")

        // Setup OAuth mock once (stateless, same for all tests)
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/token"))
                .atPriority(100) // Low priority so test-specific stubs can override
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody('{"access_token":"test-access-token","expires_in":3600,"token_type":"Bearer"}')))
    }

    def setupSpec() {
        // Runs once before all tests in the specification
    }

    def setup() {
        // Runs before each test
        RestAssured.port = port
        RestAssured.baseURI = "http://localhost"

        // NOTE: WireMock stubs are NOT reset between tests for parallel execution safety.
        // OAuth mock is set up once in static block with low priority.
        // Test-specific mocks should use higher priority (lower number) to override.

        // NOTE: No global database cleanup needed!
        // Tests are isolated by unique deviceId per test class.
        // Each test class uses getTestDeviceId() which returns a unique ID.
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

    void setupGeminiApiMock(String responseText) {
        String sseResponse = """data: {"candidates":[{"content":{"parts":[{"text":"${responseText}"}],"role":"model"},"finishReason":"STOP"}]}

"""
        wireMockServer.stubFor(post(urlPathMatching(".*streamGenerateContent.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream; charset=utf-8")
                        .withBody(sseResponse)))
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
        // Include body in signature calculation for HMAC validation
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, body, secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
    }

    def authenticatedDeleteRequest(String deviceId, String secretBase64, String path) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("DELETE", path, timestamp, nonce, deviceId, "", secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
    }

    def authenticatedPutRequestWithBody(String deviceId, String secretBase64, String path, String body) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("PUT", path, timestamp, nonce, deviceId, body, secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
    }


    String createStepsEvent(String idempotencyKey, String occurredAt = "2025-11-10T07:00:00Z", String deviceId = null) {
        deviceId = deviceId ?: getTestDeviceId()
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
            ],
            "deviceId": "${deviceId}"
        }
        """.stripIndent().trim()
    }

    String createHeartRateEvent(String idempotencyKey, String occurredAt = "2025-11-10T07:15:00Z", String deviceId = null) {
        deviceId = deviceId ?: getTestDeviceId()
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
            ],
            "deviceId": "${deviceId}"
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

    String createSleepSessionEvent(String idempotencyKey, String occurredAt = "2025-11-10T08:00:00Z", String sleepId = "sleep-session-default", String deviceId = null) {
        deviceId = deviceId ?: getTestDeviceId()
        return """
        {
            "events": [
                {
                    "idempotencyKey": "${idempotencyKey}",
                    "type": "SleepSessionRecorded.v1",
                    "occurredAt": "${occurredAt}",
                    "payload": {
                        "sleepId": "${sleepId}",
                        "sleepStart": "2025-11-10T00:30:00Z",
                        "sleepEnd": "2025-11-10T08:00:00Z",
                        "totalMinutes": 450,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ],
            "deviceId": "${deviceId}"
        }
        """.stripIndent().trim()
    }

    Map<String, String> createHmacHeaders(String deviceId, String path, Map request) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String body = groovy.json.JsonOutput.toJson(request)

        // Select the correct secret based on device ID
        String secretBase64 = deviceId == "different-device-id" ? DIFFERENT_DEVICE_SECRET_BASE64 : TEST_SECRET_BASE64

        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, body, secretBase64)

        return [
            "X-Device-Id": deviceId,
            "X-Timestamp": timestamp,
            "X-Nonce": nonce,
            "X-Signature": signature
        ]
    }

    Map<String, String> createHmacHeadersFromString(String deviceId, String path, String body) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()

        // Select the correct secret based on device ID
        String secretBase64 = deviceId == "different-device-id" ? DIFFERENT_DEVICE_SECRET_BASE64 : TEST_SECRET_BASE64

        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, body, secretBase64)

        return [
            "X-Device-Id": deviceId,
            "X-Timestamp": timestamp,
            "X-Nonce": nonce,
            "X-Signature": signature
        ]
    }

    Map parseJson(String jsonData) {
        return new groovy.json.JsonSlurper().parseText(jsonData) as Map
    }

    /**
     * Wait for async event processing to complete.
     * Uses Awaitility to poll until the condition is satisfied or timeout occurs.
     *
     * @param timeoutSeconds max time to wait
     * @param condition closure that returns true when ready
     */
    void waitForEventProcessing(int timeoutSeconds = 5, Closure<Boolean> condition) {
        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(condition as Callable<Boolean>)
    }

    /**
     * Wait for a repository to contain at least one record matching the condition.
     * Useful for waiting on projections to be created after async event processing.
     */
    def <T> T waitForProjection(int timeoutSeconds = 5, Closure<Optional<T>> finder) {
        def result = null
        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until({
                    def found = finder.call()
                    if (found.isPresent()) {
                        result = found.get()
                        return true
                    }
                    return false
                } as Callable<Boolean>)
        return result
    }

    /**
     * Wait for a list query to return non-empty results.
     */
    def <T> List<T> waitForProjections(int timeoutSeconds = 5, Closure<List<T>> finder) {
        def result = []
        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until({
                    def found = finder.call()
                    if (found != null && !found.isEmpty()) {
                        result = found
                        return true
                    }
                    return false
                } as Callable<Boolean>)
        return result
    }

    def waitForApiResponse(String path, String deviceId = null, String secretBase64 = TEST_SECRET_BASE64, int timeoutSeconds = 5) {
        deviceId = deviceId ?: getTestDeviceId()
        def result = null
        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until({
                    def response = authenticatedGetRequest(deviceId, secretBase64, path)
                            .get(path)
                            .then()
                            .extract()
                    if (response.statusCode() == 200) {
                        result = response.body().jsonPath()
                        return true
                    }
                    return false
                } as Callable<Boolean>)
        return result
    }

    boolean apiReturns404(String path, String deviceId = null, String secretBase64 = TEST_SECRET_BASE64) {
        deviceId = deviceId ?: getTestDeviceId()
        def response = authenticatedGetRequest(deviceId, secretBase64, path)
                .get(path)
                .then()
                .extract()
        return response.statusCode() == 404
    }

    /**
     * Clean up all health events for a specific device.
     * Use this in spec setup() methods for test isolation.
     */
    void cleanupEventsForDevice(String deviceId) {
        healthEventsFacade.deleteEventsByDeviceId(deviceId)
    }

    /**
     * Find all events using facade (with a wide time range).
     * Returns list of EventData objects.
     * WARNING: In parallel tests, use findEventsForDevice() instead to avoid cross-test pollution.
     */
    List findAllEvents() {
        def start = Instant.parse("2020-01-01T00:00:00Z")
        def end = Instant.parse("2030-12-31T23:59:59Z")
        return healthEventsFacade.findEventsByOccurredAtBetween(start, end)
    }

    /**
     * Find events for a specific device.
     * Use this in tests for parallel test isolation.
     */
    List findEventsForDevice(String deviceId) {
        return healthEventsFacade.findEventsByDeviceId(deviceId)
    }

    /**
     * Wait for events to be stored and return them.
     */
    List waitForEvents(int expectedCount, int timeoutSeconds = 5) {
        def result = []
        Awaitility.await()
                .atMost(timeoutSeconds, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until({
                    result = findAllEvents()
                    return result.size() >= expectedCount
                } as Callable<Boolean>)
        return result
    }

    void setupTestData(String deviceId) {
        // Setup Gemini API mock for assistant responses
        setupGeminiApiMock("Na podstawie danych widzę następujące informacje o Twoich krokach.")

        // Create some test health events via API
        def todayStepsEvent = """
        {
            "events": [
                {
                    "idempotencyKey": "${UUID.randomUUID()}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "${Instant.now().toString()}",
                    "payload": {
                        "bucketStart": "${Instant.now().minus(java.time.Duration.ofHours(1)).toString()}",
                        "bucketEnd": "${Instant.now().toString()}",
                        "count": 5000,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """

        def yesterdayStepsEvent = """
        {
            "events": [
                {
                    "idempotencyKey": "${UUID.randomUUID()}",
                    "type": "StepsBucketedRecorded.v1",
                    "occurredAt": "${Instant.now().minus(java.time.Duration.ofDays(1)).toString()}",
                    "payload": {
                        "bucketStart": "${Instant.now().minus(java.time.Duration.ofDays(1)).minus(java.time.Duration.ofHours(1)).toString()}",
                        "bucketEnd": "${Instant.now().minus(java.time.Duration.ofDays(1)).toString()}",
                        "count": 3000,
                        "originPackage": "com.google.android.apps.fitness"
                    }
                }
            ]
        }
        """

        // Submit events
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(todayStepsEvent)
            .post("/v1/health-events")

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(yesterdayStepsEvent)
            .post("/v1/health-events")
    }

    /**
     * Clean up all projections for a specific device and dates.
     * Use this in spec setup() methods for test isolation.
     */
    void cleanupProjectionsForDevice(String deviceId, LocalDate... dates) {
        dates.each { date ->
            stepsFacade.deleteProjectionsForDate(deviceId, date)
            sleepFacade.deleteProjectionsForDate(deviceId, date)
            workoutFacade.deleteProjectionsForDate(deviceId, date)
            mealsFacade.deleteProjectionsForDate(deviceId, date)
            caloriesFacade.deleteProjectionsForDate(deviceId, date)
            activityFacade.deleteProjectionsForDate(deviceId, date)
            dailySummaryFacade.deleteSummaryForDate(deviceId, date)
            weightFacade.deleteProjectionsForDate(deviceId, date)
            heartRateFacade.deleteProjectionsForDate(deviceId, date)
            bodyMeasurementsFacade.deleteProjectionsForDate(deviceId, date)
        }
    }

    /**
     * Clean up all projections for a specific device within a date range.
     * Use this when you need to clean up multiple consecutive days.
     */
    void cleanupProjectionsForDateRange(String deviceId, LocalDate startDate, LocalDate endDate) {
        startDate.datesUntil(endDate.plusDays(1)).toList().each { date ->
            cleanupProjectionsForDevice(deviceId, date)
        }
    }
}

