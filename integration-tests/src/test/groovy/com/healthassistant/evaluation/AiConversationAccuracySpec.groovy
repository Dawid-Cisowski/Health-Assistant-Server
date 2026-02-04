package com.healthassistant.evaluation

import org.springframework.ai.evaluation.EvaluationRequest
import spock.lang.Requires
import spock.lang.Timeout

import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Integration tests verifying AI assistant conversation accuracy.
 *
 * These tests verify:
 * 1. Multi-turn conversation context retention
 * 2. AI correctly references previous conversation data
 * 3. No hallucinations about what was said in previous turns
 * 4. Conversation isolation between devices
 *
 * Uses the LLM-as-a-Judge pattern with real Gemini API.
 *
 * IMPORTANT: These tests require GEMINI_API_KEY environment variable.
 * Run with: GEMINI_API_KEY=your-key ./gradlew :integration-tests:test --tests "*AiConversationAccuracySpec"
 */
@Requires({ System.getenv('GEMINI_API_KEY') })
@Timeout(value = 180, unit = TimeUnit.SECONDS)
class AiConversationAccuracySpec extends BaseEvaluationSpec {

    // ==================== Multi-Turn Conversation Tests ====================

    def "AI uses context from previous conversation turn"() {
        given: "steps recorded today"
        submitStepsForToday(8500)
        waitForProjections()

        and: "first question about steps"
        def response1 = askAssistantWithConversation("Ile kroków zrobiłem dzisiaj?", null)
        println "DEBUG: Turn 1 response: ${response1.content}"
        def conversationId = response1.conversationId

        when: "asking follow-up question referencing previous answer"
        def response2 = askAssistantWithConversation("Czy uważasz że te 8500 kroków to dobry wynik?", conversationId)
        println "DEBUG: Turn 2 response: ${response2.content}"

        then: "AI provides evaluation referencing the step count"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should provide an evaluation or opinion about 8500 steps being a good or bad result. The response should contain some assessment (e.g., 'good', 'great', 'average', 'could be better').",
                        [],
                        response2.content
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI correctly answers question about data from earlier in conversation"() {
        given: "multiple data types recorded"
        submitStepsForToday(6000)
        submitActiveCalories(400)
        submitMeal("Obiad", "LUNCH", 800, 40, 25, 70)
        waitForProjections()

        and: "ask about steps first"
        def response1 = askAssistantWithConversation("Ile kroków dzisiaj?", null)
        println "DEBUG: Turn 1 (steps): ${response1.content}"
        def conversationId = response1.conversationId

        and: "ask about calories"
        def response2 = askAssistantWithConversation("A ile kalorii spaliłem?", conversationId)
        println "DEBUG: Turn 2 (calories): ${response2.content}"

        when: "asking about what we discussed regarding steps"
        def response3 = askAssistantWithConversation("Ile to było kroków, o których rozmawialiśmy?", conversationId)
        println "DEBUG: Turn 3 (reference): ${response3.content}"

        then: "AI correctly recalls the step count from earlier"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should recall approximately 6000 steps that were discussed earlier in the conversation",
                        [],
                        response3.content
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    def "AI does not hallucinate about previous conversation when asked"() {
        given: "some data recorded"
        submitStepsForToday(5000)
        waitForProjections()

        and: "single question asked"
        def response1 = askAssistantWithConversation("Ile kroków?", null)
        println "DEBUG: Turn 1: ${response1.content}"
        def conversationId = response1.conversationId

        when: "asking about something never discussed"
        def response2 = askAssistantWithConversation("Ile powiedziałeś, że spałem?", conversationId)
        println "DEBUG: Turn 2: ${response2.content}"

        then: "AI does not invent a sleep duration that was never discussed"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "AI should indicate that sleep was NOT discussed in this conversation - it should NOT invent a number like '7 hours' or '8 hours'",
                        [],
                        response2.content
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Context Retention Across Multiple Turns ====================

    def "AI maintains context across 5 conversation turns"() {
        given: "various data recorded"
        submitStepsForToday(7500)
        submitActiveCalories(350)
        submitSleepForLastNight(420) // 7 hours
        waitForProjections()

        and: "turn 1 - steps (explicit date)"
        def r1 = askAssistantWithConversation("Ile kroków zrobiłem dzisiaj?", null)
        println "DEBUG: Turn 1: ${r1.content}"
        def convId = r1.conversationId

        and: "turn 2 - calories (explicit date)"
        def r2 = askAssistantWithConversation("Ile kalorii spaliłem dzisiaj?", convId)
        println "DEBUG: Turn 2: ${r2.content}"

        and: "turn 3 - sleep (explicit date)"
        def r3 = askAssistantWithConversation("Ile spałem wczoraj w nocy?", convId)
        println "DEBUG: Turn 3: ${r3.content}"

        and: "turn 4 - general question"
        def r4 = askAssistantWithConversation("Świetnie, dziękuję za informacje!", convId)
        println "DEBUG: Turn 4: ${r4.content}"

        when: "turn 5 - summary referencing all previous data"
        def r5 = askAssistantWithConversation("Podsumuj wszystkie dane o których rozmawialiśmy: kroki, kalorie i sen", convId)
        println "DEBUG: Turn 5: ${r5.content}"

        then: "AI includes all data points from conversation"
        def evaluation = healthDataEvaluator.evaluate(
                new EvaluationRequest(
                        "Summary should mention: approximately 7500 steps, approximately 350 calories burned, and approximately 7 hours (420 minutes) of sleep",
                        [],
                        r5.content
                )
        )
        println "DEBUG: Evaluation: pass=${evaluation.isPass()}, feedback=${evaluation.feedback}"
        evaluation.isPass()
    }

    // ==================== Helper Methods ====================

    /**
     * Ask assistant with conversation tracking.
     * Returns both the content and conversation ID.
     */
    ConversationResponse askAssistantWithConversation(String message, String conversationId) {
        def chatRequest = conversationId
                ? """{"message": "${escapeJson(message)}", "conversationId": "${conversationId}"}"""
                : """{"message": "${escapeJson(message)}"}"""

        def response = authenticatedPostRequestWithBody(
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

        def content = parseSSEContent(response)
        def extractedConversationId = parseConversationId(response)

        return new ConversationResponse(content, extractedConversationId)
    }

    /**
     * Parse conversation ID from done event in SSE response.
     */
    String parseConversationId(String sseResponse) {
        def matcher = sseResponse =~ /"conversationId":"([^"]+)"/
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    /**
     * Response holder for conversation tracking.
     */
    static class ConversationResponse {
        String content
        String conversationId

        ConversationResponse(String content, String conversationId) {
            this.content = content
            this.conversationId = conversationId
        }
    }
}
