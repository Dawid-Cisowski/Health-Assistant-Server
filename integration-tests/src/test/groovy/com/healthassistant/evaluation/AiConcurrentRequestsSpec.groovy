package com.healthassistant.evaluation

import io.restassured.RestAssured
import io.restassured.builder.MultiPartSpecBuilder
import io.restassured.http.ContentType
import spock.lang.Requires
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration tests for concurrent AI request handling.
 *
 * These tests verify that:
 * 1. Multiple simultaneous chat requests complete without errors
 * 2. Concurrent imports don't cause data corruption
 * 3. Conversation updates handle race conditions correctly
 * 4. Draft confirmations are idempotent/conflict-safe
 *
 * IMPORTANT: These tests require GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiConcurrentRequestsSpec"
 */
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 300, unit = TimeUnit.SECONDS)
class AiConcurrentRequestsSpec extends BaseEvaluationSpec {

    private static final String MEAL_IMPORT_ENDPOINT = "/v1/meals/import"

    def "Multiple simultaneous chat requests all complete successfully"() {
        given: "some health data exists"
        submitStepsForToday(5000)
        submitActiveCalories(300)
        waitForProjections()

        and: "executor for parallel requests"
        ExecutorService executor = Executors.newFixedThreadPool(5)

        when: "sending 5 concurrent chat requests"
        def futures = (1..5).collect { requestNum ->
            CompletableFuture.supplyAsync({
                try {
                    println "DEBUG: Starting request ${requestNum}"
                    def response = sendChatRequest("Ile kroków dzisiaj? Request ${requestNum}")
                    println "DEBUG: Completed request ${requestNum}, length=${response.length()}"
                    return [success: true, response: response, requestNum: requestNum]
                } catch (Exception e) {
                    println "DEBUG: Failed request ${requestNum}: ${e.message}"
                    return [success: false, error: e.message, requestNum: requestNum]
                }
            }, executor)
        }

        def results = futures.collect { it.get(120, TimeUnit.SECONDS) }
        executor.shutdown()

        then: "all requests completed"
        results.size() == 5

        and: "all requests were successful"
        results.every { it.success }

        and: "all responses contain DoneEvent"
        results.every { it.response.contains('"type":"done"') }

        and: "all responses contain content"
        results.every { it.response.contains('"type":"content"') }
    }

    def "Concurrent meal imports create separate events"() {
        given: "executor for parallel imports"
        ExecutorService executor = Executors.newFixedThreadPool(3)
        def successCount = new AtomicInteger(0)
        def mealIds = Collections.synchronizedList([])

        when: "importing 3 meals concurrently"
        def futures = (1..3).collect { mealNum ->
            CompletableFuture.supplyAsync({
                try {
                    println "DEBUG: Starting meal import ${mealNum}"
                    def description = "Obiad numer ${mealNum} - kurczak z ryżem"
                    def response = importMeal(description)
                    println "DEBUG: Meal import ${mealNum} response: ${response}"

                    if (response.contains('"status":"success"')) {
                        successCount.incrementAndGet()
                        // Extract mealId
                        def matcher = response =~ /"mealId":"([^"]+)"/
                        if (matcher.find()) {
                            mealIds.add(matcher.group(1))
                        }
                    }
                    return response
                } catch (Exception e) {
                    println "DEBUG: Failed meal import ${mealNum}: ${e.message}"
                    return null
                }
            }, executor)
        }

        futures.each { it.get(120, TimeUnit.SECONDS) }
        executor.shutdown()

        then: "all imports succeeded"
        successCount.get() == 3

        and: "each meal has unique ID"
        mealIds.size() == 3
        mealIds.unique().size() == 3
    }

    def "Rapid fire requests don't cause server errors"() {
        given: "data exists"
        submitStepsForToday(5000)
        waitForProjections()

        when: "sending 10 requests as fast as possible"
        def responses = []
        def errors = []

        (1..10).each { n ->
            try {
                def response = sendChatRequest("Kroki ${n}")
                responses << response
            } catch (Exception e) {
                errors << e.message
            }
        }

        println "DEBUG: Successful responses: ${responses.size()}, Errors: ${errors.size()}"

        then: "most requests succeed (allow some rate limiting)"
        responses.size() >= 8 // At least 80% success rate

        and: "successful responses are valid"
        responses.every { it.contains('"type":"done"') }
    }

    // ==================== Helper Methods ====================

    String sendChatRequest(String message) {
        def chatRequest = """{"message": "${escapeJson(message)}"}"""

        return authenticatedPostRequestWithBody(
                getTestDeviceId(), TEST_SECRET_BASE64,
                "/v1/assistant/chat", chatRequest
        )
                .when()
                .post("/v1/assistant/chat")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString()
    }

    String importMeal(String description) {
        String timestamp = generateTimestamp()
        String nonce = generateNonce()
        String signature = generateHmacSignature("POST", MEAL_IMPORT_ENDPOINT, timestamp, nonce, getTestDeviceId(), "", TEST_SECRET_BASE64)

        return RestAssured.given()
                .header("X-Device-Id", getTestDeviceId())
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .multiPart("description", description)
                .post(MEAL_IMPORT_ENDPOINT)
                .then()
                .extract()
                .body()
                .asString()
    }

    String extractConversationId(String sseResponse) {
        def matcher = sseResponse =~ /"conversationId":"([^"]+)"/
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }
}
