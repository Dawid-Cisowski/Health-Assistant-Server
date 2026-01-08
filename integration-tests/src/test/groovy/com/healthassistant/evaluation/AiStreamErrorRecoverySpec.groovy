package com.healthassistant.evaluation

import io.restassured.RestAssured
import io.restassured.http.ContentType
import spock.lang.Requires
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

/**
 * Integration tests for SSE stream error handling and recovery.
 *
 * These tests verify that:
 * 1. SSE stream completes even with edge case queries
 * 2. ErrorEvents are formatted correctly
 * 3. Stream doesn't hang or crash on unusual inputs
 * 4. DoneEvent is always sent at the end
 *
 * IMPORTANT: These tests require GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiStreamErrorRecoverySpec"
 */
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiStreamErrorRecoverySpec extends BaseEvaluationSpec {

    def "SSE stream completes with DoneEvent for normal query"() {
        given: "some health data"
        submitStepsForToday(5000)
        waitForProjections()

        when: "sending a normal query"
        def sseResponse = sendChatRequest("Ile kroków dzisiaj?")
        println "DEBUG: SSE Response length: ${sseResponse.length()}"

        then: "stream contains DoneEvent"
        sseResponse.contains('"type":"done"')

        and: "stream contains content"
        sseResponse.contains('"type":"content"')
    }

    def "SSE stream completes with DoneEvent for empty data query"() {
        given: "no data exists (cleaned in setup)"

        when: "asking about non-existent data"
        def sseResponse = sendChatRequest("Ile kroków zrobiłem w zeszłym roku?")
        println "DEBUG: SSE Response length: ${sseResponse.length()}"

        then: "stream still completes with DoneEvent"
        sseResponse.contains('"type":"done"')
    }

    def "SSE stream handles very long question gracefully"() {
        given: "a question near the limit (1000 chars)"
        def longQuestion = "Opowiedz mi o moich krokach " + ("i kaloriach " * 70) // ~950 chars

        when: "sending the long question"
        def sseResponse = sendChatRequest(longQuestion.take(999))
        println "DEBUG: Question length: ${longQuestion.take(999).length()}"
        println "DEBUG: SSE Response length: ${sseResponse.length()}"

        then: "stream completes"
        sseResponse.contains('"type":"done"')
    }

    def "SSE stream handles special characters in question"() {
        given: "a question with special characters"
        def specialQuestion = "Ile kroków? Zrobiłem < 5000 & > 3000 kroków?"

        when: "sending the question with special chars"
        def sseResponse = sendChatRequest(specialQuestion)
        println "DEBUG: SSE Response: ${sseResponse.take(500)}"

        then: "stream completes without crashing"
        sseResponse.contains('"type":"done"')
    }

    def "SSE stream handles Polish diacritics correctly"() {
        given: "a question with Polish characters"
        def polishQuestion = "Ile spałem? Ćwiczenia? Posiłki żółć źdźbło?"

        when: "sending the Polish question"
        def sseResponse = sendChatRequest(polishQuestion)
        println "DEBUG: SSE Response: ${sseResponse.take(500)}"

        then: "stream completes"
        sseResponse.contains('"type":"done"')
    }

    def "SSE stream handles rapid consecutive requests"() {
        given: "some data"
        submitStepsForToday(5000)
        waitForProjections()

        when: "sending multiple requests quickly"
        def responses = []
        (1..3).each { i ->
            responses << sendChatRequest("Ile kroków? Request ${i}")
        }

        then: "all streams complete"
        responses.every { it.contains('"type":"done"') }
    }

    def "SSE ContentEvent format is correct"() {
        given: "data exists"
        submitStepsForToday(8000)
        waitForProjections()

        when: "parsing SSE response"
        def sseResponse = sendChatRequest("Ile kroków?")
        def contentEvents = extractEventsOfType(sseResponse, "content")
        println "DEBUG: Found ${contentEvents.size()} content events"

        then: "content events have correct structure"
        contentEvents.size() > 0
        contentEvents.every { event ->
            event.contains('"type":"content"')
            event.contains('"content":')
        }
    }

    def "SSE DoneEvent contains conversationId"() {
        when: "sending a request"
        def sseResponse = sendChatRequest("Hello")
        def doneEvents = extractEventsOfType(sseResponse, "done")
        println "DEBUG: Done event: ${doneEvents}"

        then: "DoneEvent has conversationId"
        doneEvents.size() == 1
        doneEvents[0].contains('"conversationId"')
    }

    def "SSE stream handles numeric data in response correctly"() {
        given: "steps data"
        submitStepsForToday(12345)
        waitForProjections()

        when: "asking for exact count"
        def sseResponse = sendChatRequest("Podaj dokładną liczbę kroków dzisiaj")
        def content = parseSSEContent(sseResponse)
        println "DEBUG: Parsed content: $content"

        then: "response contains the number"
        content.contains("12345") || content.contains("12,345") || content.contains("12 345")
    }

    // ==================== Helper Methods ====================

    String sendChatRequest(String message) {
        def chatRequest = """{"message": "${escapeJson(message)}"}"""

        return authenticatedPostRequestWithBody(
                TEST_DEVICE_ID, TEST_SECRET_BASE64,
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

    List<String> extractEventsOfType(String sseResponse, String type) {
        def events = []
        sseResponse.split("\n\n").each { event ->
            event.split("\n").each { line ->
                if (line.startsWith("data:")) {
                    def json = line.substring(5).trim()
                    if (json && json.contains("\"type\":\"${type}\"")) {
                        events << json
                    }
                }
            }
        }
        return events
    }
}
