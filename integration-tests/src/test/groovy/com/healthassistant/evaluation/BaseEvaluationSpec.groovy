package com.healthassistant.evaluation

import com.healthassistant.HealthAssistantApplication
import com.healthassistant.TestAsyncConfiguration
import com.healthassistant.calories.api.CaloriesFacade
import com.healthassistant.dailysummary.api.DailySummaryFacade
import com.healthassistant.healthevents.api.HealthEventsFacade
import com.healthassistant.sleep.api.SleepFacade
import com.healthassistant.workout.api.WorkoutFacade
import com.healthassistant.meals.api.MealsFacade
import com.healthassistant.steps.api.StepsFacade
import com.healthassistant.weight.api.WeightFacade
import com.healthassistant.heartrate.api.HeartRateFacade
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

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Base class for AI evaluation tests using real Gemini API.
 *
 * IMPORTANT: This class does NOT import TestChatModelConfiguration,
 * which means it uses the REAL Gemini API instead of mocks.
 *
 * Tests extending this class require GEMINI_API_KEY environment variable.
 *
 * NOTE: Each test class gets a unique device ID for parallel test isolation.
 * Use getTestDeviceId() to get the device ID for the current test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = [HealthAssistantApplication])
@Import([EvaluationTestConfiguration, TestAsyncConfiguration])
@ActiveProfiles(["test", "evaluation"])
abstract class BaseEvaluationSpec extends Specification {

    // Each test class gets a unique device ID based on class name for parallel test isolation
    static final String TEST_SECRET_BASE64 = "dGVzdC1zZWNyZXQtMTIz"
    static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw")

    // Legacy constant for backward compatibility - use getTestDeviceId() instead
    static final String TEST_DEVICE_ID = "test-device"

    // Generate unique device ID per test class for parallel isolation
    String getTestDeviceId() {
        return "eval-${this.class.simpleName.toLowerCase().hashCode().abs() % 10000}"
    }

    @LocalServerPort
    int port

    @Autowired
    HealthEventsFacade healthEventsFacade

    @Autowired
    HealthDataEvaluator healthDataEvaluator

    @Autowired
    SleepFacade sleepFacade

    @Autowired
    WorkoutFacade workoutFacade

    @Autowired
    MealsFacade mealsFacade

    @Autowired
    StepsFacade stepsFacade

    @Autowired
    CaloriesFacade caloriesFacade

    @Autowired
    DailySummaryFacade dailySummaryFacade

    @Autowired
    WeightFacade weightFacade

    @Autowired
    HeartRateFacade heartRateFacade

    @Autowired(required = false)
    com.healthassistant.assistant.api.AssistantFacade assistantFacade

    @Shared
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)

    static {
        postgres.start()

        // Generate HMAC devices config with all evaluation test devices for parallel execution
        // Evaluation spec class names - each gets a unique device ID based on class name hash
        def evalSpecs = [
            "AiConversationAccuracySpec",
            "AiToolErrorHandlingSpec",
            "AiHallucinationSpec",
            "AiStreamErrorRecoverySpec",
            "AiContentFilteringSpec",
            "AiConcurrentRequestsSpec",
            "AiMultiToolQuerySpec",
            "AiPromptInjectionSpec",
            "AiDailySummaryEvaluationSpec",
            "MealImportAISpec",
            "WorkoutImportAISpec",
            "SleepImportAISpec",
            "WeightImportAISpec",
            "AiBenchmarkSpec"
        ]

        def devicesMap = new StringBuilder('{')
        devicesMap.append('"test-device":"dGVzdC1zZWNyZXQtMTIz"')  // Legacy device
        evalSpecs.each { specName ->
            def deviceId = "eval-${specName.toLowerCase().hashCode().abs() % 10000}"
            devicesMap.append(',"').append(deviceId).append('":"dGVzdC1zZWNyZXQtMTIz"')
        }
        devicesMap.append('}')

        System.setProperty("spring.datasource.url", postgres.getJdbcUrl())
        System.setProperty("spring.datasource.username", postgres.getUsername())
        System.setProperty("spring.datasource.password", postgres.getPassword())
        System.setProperty("app.hmac.devices-json", devicesMap.toString())
        System.setProperty("app.hmac.tolerance-seconds", "600")
        System.setProperty("app.nonce.cache-ttl-seconds", "600")

        // Use real Gemini API - key should be provided via environment variable
        String geminiApiKey = System.getenv("GEMINI_API_KEY") ?: "test-key"
        System.setProperty("spring.ai.google.genai.api-key", geminiApiKey)
    }

    def setup() {
        RestAssured.port = port
        RestAssured.baseURI = "http://localhost"

        println "DEBUG: Test class ${this.class.simpleName} using device ID: ${getTestDeviceId()}"
        cleanAllData()
    }

    def cleanup() {
        cleanAllData()
    }

    void cleanAllData() {
        def deviceId = getTestDeviceId()
        // Delete events first
        if (healthEventsFacade != null) {
            healthEventsFacade.deleteEventsByDeviceId(deviceId)
        }
        // Delete conversations for device
        if (assistantFacade != null) {
            assistantFacade.deleteConversationsByDeviceId(deviceId)
        }
        // Delete all projections for device within a reasonable date range
        def today = LocalDate.now(POLAND_ZONE)
        def startDate = today.minusMonths(6)
        def endDate = today.plusDays(7)

        startDate.datesUntil(endDate.plusDays(1)).toList().each { date ->
            cleanupProjectionsForDate(deviceId, date)
        }
    }

    void cleanupProjectionsForDate(String deviceId, LocalDate date) {
        if (stepsFacade != null) {
            stepsFacade.deleteProjectionsForDate(deviceId, date)
        }
        if (sleepFacade != null) {
            sleepFacade.deleteProjectionsForDate(deviceId, date)
        }
        if (workoutFacade != null) {
            workoutFacade.deleteProjectionsForDate(deviceId, date)
        }
        if (mealsFacade != null) {
            mealsFacade.deleteProjectionsForDate(deviceId, date)
        }
        if (caloriesFacade != null) {
            caloriesFacade.deleteProjectionsForDate(deviceId, date)
        }
        if (dailySummaryFacade != null) {
            dailySummaryFacade.deleteSummaryForDate(deviceId, date)
        }
        if (weightFacade != null) {
            weightFacade.deleteProjectionsForDate(deviceId, date)
        }
        if (heartRateFacade != null) {
            heartRateFacade.deleteProjectionsForDate(deviceId, date)
        }
    }

    // ==================== Assistant Helpers ====================

    // Store last conversation ID for multi-turn tests
    protected String lastConversationId = null

    /**
     * Send a chat message to the AI assistant and return the response content.
     * Does NOT maintain conversation context - each call starts a new conversation.
     */
    String askAssistant(String message) {
        def result = askAssistantWithContext(message, null)
        return result.content
    }

    /**
     * Send a chat message continuing a conversation.
     * Maintains conversation context using lastConversationId.
     * Use this for multi-turn conversation tests.
     */
    String askAssistantContinue(String message) {
        def result = askAssistantWithContext(message, lastConversationId)
        lastConversationId = result.conversationId
        return result.content
    }

    /**
     * Start a new conversation and track it for follow-up messages.
     * Use askAssistantContinue() for subsequent messages in the same conversation.
     */
    String askAssistantStart(String message) {
        lastConversationId = null
        def result = askAssistantWithContext(message, null)
        lastConversationId = result.conversationId
        return result.content
    }

    /**
     * Send a chat message with optional conversation context.
     * Returns both content and conversationId for multi-turn conversations.
     */
    Map askAssistantWithContext(String message, String conversationId) {
        def deviceId = getTestDeviceId()
        println "DEBUG: Asking assistant for device ${deviceId}: ${message} (conversationId: ${conversationId ?: 'new'})"

        def chatRequest = conversationId ?
            """{"message": "${escapeJson(message)}", "conversationId": "${conversationId}"}""" :
            """{"message": "${escapeJson(message)}"}"""

        def response = authenticatedPostRequestWithBody(
                deviceId, TEST_SECRET_BASE64,
                "/v1/assistant/chat", chatRequest
        )
                .when()
                .post("/v1/assistant/chat")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString()

        def content = parseSSEContent(response)
        def newConversationId = parseSSEConversationId(response)
        println "DEBUG: Assistant response for device ${deviceId}: ${content?.take(100)}... (conversationId: ${newConversationId})"

        return [content: content, conversationId: newConversationId]
    }

    /**
     * Extract conversationId from SSE done event.
     */
    String parseSSEConversationId(String sseResponse) {
        def jsonSlurper = new groovy.json.JsonSlurper()
        String conversationId = null
        sseResponse.split("\n\n").each { event ->
            event.split("\n").each { line ->
                if (line.startsWith("data:")) {
                    def json = line.substring(5).trim()
                    if (json && json.contains('"type":"done"')) {
                        try {
                            def parsed = jsonSlurper.parseText(json)
                            conversationId = parsed.conversationId
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return conversationId
    }

    /**
     * Parse SSE response and extract all content events concatenated.
     */
    String parseSSEContent(String sseResponse) {
        def content = new StringBuilder()
        def jsonSlurper = new groovy.json.JsonSlurper()
        sseResponse.split("\n\n").each { event ->
            event.split("\n").each { line ->
                if (line.startsWith("data:")) {
                    def json = line.substring(5).trim()
                    if (json && json.contains('"type":"content"')) {
                        try {
                            def parsed = jsonSlurper.parseText(json)
                            if (parsed.content) {
                                content.append(parsed.content)
                            }
                        } catch (Exception ignored) {
                            // JSON parsing failed, skip this chunk
                        }
                    }
                }
            }
        }
        return content.toString()
    }

    // ==================== Event Submission Helpers ====================

    void submitStepsForToday(int count) {
        submitStepsForDate(LocalDate.now(POLAND_ZONE), count)
    }

    void submitStepsForDate(LocalDate date, int count) {
        def bucketEnd = date.atTime(12, 0).atZone(POLAND_ZONE).toInstant()
        def bucketStart = bucketEnd.minusSeconds(3600)

        def request = [
            deviceId: getTestDeviceId(),
            events: [[
                idempotencyKey: UUID.randomUUID().toString(),
                type: "StepsBucketedRecorded.v1",
                occurredAt: bucketEnd.toString(),
                payload: [
                    bucketStart: bucketStart.toString(),
                    bucketEnd: bucketEnd.toString(),
                    count: count,
                    originPackage: "com.test"
                ]
            ]]
        ]
        submitEventMap(request)
    }

    void submitSleepForLastNight(int totalMinutes) {
        // Sleep ends today morning (attributed to today in daily summary)
        // Example: sleep from Dec 16 22:00 to Dec 17 05:00 → attributed to Dec 17
        def today = LocalDate.now(POLAND_ZONE)
        def sleepEnd = today.atTime(5, 0).atZone(POLAND_ZONE).toInstant()
        def sleepStart = sleepEnd.minusSeconds(totalMinutes * 60L)

        def request = [
            deviceId: getTestDeviceId(),
            events: [[
                idempotencyKey: UUID.randomUUID().toString(),
                type: "SleepSessionRecorded.v1",
                occurredAt: sleepEnd.toString(),
                payload: [
                    sleepId: UUID.randomUUID().toString(),
                    sleepStart: sleepStart.toString(),
                    sleepEnd: sleepEnd.toString(),
                    totalMinutes: totalMinutes,
                    originPackage: "com.test"
                ]
            ]]
        ]
        submitEventMap(request)
    }

    void submitWorkout(String exerciseName, int sets, int reps, double weightKg) {
        def performedAt = Instant.now()
        def setsData = (1..sets).collect { setNum ->
            [setNumber: setNum, reps: reps, weightKg: weightKg, isWarmup: false]
        }

        def request = [
            deviceId: getTestDeviceId(),
            events: [[
                idempotencyKey: UUID.randomUUID().toString(),
                type: "WorkoutRecorded.v1",
                occurredAt: performedAt.toString(),
                payload: [
                    workoutId: UUID.randomUUID().toString(),
                    performedAt: performedAt.toString(),
                    source: "TEST",
                    note: null,
                    exercises: [[
                        name: exerciseName,
                        muscleGroup: null,
                        orderInWorkout: 1,
                        sets: setsData
                    ]]
                ]
            ]]
        ]
        submitEventMap(request)
    }

    void submitActiveCalories(double kcal) {
        def bucketEnd = Instant.now()
        def bucketStart = bucketEnd.minusSeconds(3600)

        def request = [
            deviceId: getTestDeviceId(),
            events: [[
                idempotencyKey: UUID.randomUUID().toString(),
                type: "ActiveCaloriesBurnedRecorded.v1",
                occurredAt: bucketEnd.toString(),
                payload: [
                    bucketStart: bucketStart.toString(),
                    bucketEnd: bucketEnd.toString(),
                    energyKcal: kcal,
                    originPackage: "com.test"
                ]
            ]]
        ]
        submitEventMap(request)
    }

    void submitMeal(String title, String mealType, int calories, int protein, int fat, int carbs) {
        def occurredAt = Instant.now()

        def request = [
            deviceId: getTestDeviceId(),
            events: [[
                idempotencyKey: UUID.randomUUID().toString(),
                type: "MealRecorded.v1",
                occurredAt: occurredAt.toString(),
                payload: [
                    title: title,
                    mealType: mealType,
                    caloriesKcal: calories,
                    proteinGrams: protein,
                    fatGrams: fat,
                    carbohydratesGrams: carbs,
                    healthRating: "HEALTHY"
                ]
            ]]
        ]
        submitEventMap(request)
    }

    void submitEvent(String eventJson) {
        def deviceId = getTestDeviceId()
        println "DEBUG: Submitting event for device ${deviceId}"
        def response = authenticatedPostRequestWithBody(deviceId, TEST_SECRET_BASE64, "/v1/health-events", eventJson)
                .post("/v1/health-events")
        println "DEBUG: Submit response status: ${response.statusCode()}"
        response.then().statusCode(200)
    }

    void submitEventMap(Map request) {
        def jsonBuilder = new groovy.json.JsonBuilder(request)
        submitEvent(jsonBuilder.toString())
    }

    /**
     * Wait for async projections to complete.
     * Increased timeout to ensure all projections and daily summaries are fully updated.
     */
    void waitForProjections() {
        Thread.sleep(1000) // Increased from 500ms to 2000ms for AI evaluation tests
    }

    // ==================== HMAC Authentication Helpers ====================

    String generateHmacSignature(String method, String path, String timestamp, String nonce, String deviceId, String body, String secretBase64) {
        byte[] secret = Base64.decoder.decode(secretBase64)
        String canonicalString = [method, path, timestamp, nonce, deviceId, body].join('\n')

        Mac mac = Mac.getInstance("HmacSHA256")
        SecretKeySpec keySpec = new SecretKeySpec(secret, "HmacSHA256")
        mac.init(keySpec)
        byte[] hmacBytes = mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8))

        return Base64.encoder.encodeToString(hmacBytes)
    }

    String generateTimestamp() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    }

    String generateNonce() {
        return UUID.randomUUID().toString().toLowerCase()
    }

    def authenticatedPostRequestWithBody(String deviceId, String secretBase64, String path, String body) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", path, timestamp, nonce, deviceId, body, secretBase64)

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("X-Device-Id", deviceId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .body(body)
    }

    // ==================== Utility Helpers ====================

    String escapeJson(String text) {
        return text
                .replace('\\', '\\\\')
                .replace('"', '\\"')
                .replace('\n', '\\n')
                .replace('\r', '\\r')
                .replace('\t', '\\t')
    }
}
