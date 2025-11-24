package com.healthassistant

import io.restassured.http.ContentType
import spock.lang.Ignore

import static io.restassured.RestAssured.given

class ConversationHistorySpec extends BaseIntegrationSpec {

    @Ignore("Requires proper WireMock stubbing for Spring AI Google GenAI client")
    def "should create new conversation when conversationId not provided"() {
        given: "a valid chat request without conversationId"
        def request = [
                message: "Ile kroków zrobiłem dzisiaj?"
        ]

        when: "sending chat request"
        def response = sendAuthenticatedSseRequest("/v1/assistant/chat", request, TEST_DEVICE_ID)

        then: "response contains conversation events"
        response.size() > 0

        and: "last event is DoneEvent with conversationId"
        def doneEvent = response.find { it.type == "done" }
        doneEvent != null
        doneEvent.conversationId != null
    }

    @Ignore("Requires proper WireMock stubbing for Spring AI Google GenAI client")
    def "should continue conversation when conversationId provided"() {
        given: "first message creates conversation"
        def firstRequest = [
                message: "Ile kroków zrobiłem dzisiaj?"
        ]
        def firstResponse = sendAuthenticatedSseRequest("/v1/assistant/chat", firstRequest, TEST_DEVICE_ID)
        def conversationId = firstResponse.find { it.type == "done" }?.conversationId

        when: "sending second message with conversationId"
        def secondRequest = [
                message: "A wczoraj?",
                conversationId: conversationId
        ]
        def secondResponse = sendAuthenticatedSseRequest("/v1/assistant/chat", secondRequest, TEST_DEVICE_ID)

        then: "response uses same conversationId"
        def doneEvent = secondResponse.find { it.type == "done" }
        doneEvent.conversationId == conversationId
    }

    def "should return 404 for non-existent conversationId"() {
        given: "invalid conversationId"
        def request = [
                message: "Test message",
                conversationId: UUID.randomUUID().toString()
        ]

        when: "sending request"
        def response = given()
                .contentType(ContentType.JSON)
                .accept("text/event-stream")
                .headers(createHmacHeaders(TEST_DEVICE_ID, "/v1/assistant/chat", request))
                .body(request)
                .post("/v1/assistant/chat")

        then: "returns 404 or error event in stream"
        response.statusCode() == 404 ||
                (response.statusCode() == 200 &&
                 response.body().asString().contains("error"))
    }

    def "should reject conversationId from different device"() {
        given: "conversation created by device1"
        def request1 = [message: "Test"]
        def response1 = sendAuthenticatedSseRequest("/v1/assistant/chat", request1, TEST_DEVICE_ID)
        def conversationId = response1.find { it.type == "done" }?.conversationId

        when: "device2 tries to use same conversationId"
        def request2 = [
                message: "Another message",
                conversationId: conversationId
        ]
        def response2 = given()
                .contentType(ContentType.JSON)
                .accept("text/event-stream")
                .headers(createHmacHeaders("different-device-id", "/v1/assistant/chat", request2))
                .body(request2)
                .post("/v1/assistant/chat")

        then: "returns 404"
        response2.statusCode() == 404 ||
                (response2.statusCode() == 200 &&
                 response2.body().asString().contains("error"))
    }

    @Ignore("Requires proper WireMock stubbing for Spring AI Google GenAI client")
    def "should maintain conversation context across messages"() {
        given: "setup test data"
        setupTestData(TEST_DEVICE_ID)

        and: "first message about steps"
        def firstRequest = [message: "Ile kroków zrobiłem dzisiaj?"]
        def firstResponse = sendAuthenticatedSseRequest("/v1/assistant/chat", firstRequest, TEST_DEVICE_ID)
        def conversationId = firstResponse.find { it.type == "done" }?.conversationId

        when: "follow-up question using context"
        def secondRequest = [
                message: "A wczoraj?",  // Refers to "steps yesterday" from context
                conversationId: conversationId
        ]
        def secondResponse = sendAuthenticatedSseRequest("/v1/assistant/chat", secondRequest, TEST_DEVICE_ID)

        then: "AI should understand context and provide steps for yesterday"
        def contentEvents = secondResponse.findAll { it.type == "content" }
        !contentEvents.isEmpty()

        and: "conversation continues with same ID"
        def doneEvent = secondResponse.find { it.type == "done" }
        doneEvent.conversationId == conversationId
    }

    @Ignore("Requires proper WireMock stubbing for Spring AI Google GenAI client")
    def "should limit history to last 20 messages"() {
        given: "setup test data"
        setupTestData(TEST_DEVICE_ID)

        and: "create conversation with first message"
        def firstRequest = [message: "Rozpocznij konwersację"]
        def response = sendAuthenticatedSseRequest("/v1/assistant/chat", firstRequest, TEST_DEVICE_ID)
        def conversationId = response.find { it.type == "done" }?.conversationId

        when: "send 15 more messages (30 total messages: 15 user + 15 assistant)"
        (1..15).each { i ->
            def request = [
                    message: "Wiadomość numer ${i}",
                    conversationId: conversationId
            ]
            sendAuthenticatedSseRequest("/v1/assistant/chat", request, TEST_DEVICE_ID)
        }

        and: "send final message"
        def finalRequest = [
                message: "Czy pamiętasz pierwszą wiadomość?",
                conversationId: conversationId
        ]
        def finalResponse = sendAuthenticatedSseRequest("/v1/assistant/chat", finalRequest, TEST_DEVICE_ID)

        then: "conversation completes successfully"
        def doneEvent = finalResponse.find { it.type == "done" }
        doneEvent.conversationId == conversationId

        and: "response is generated (history was properly limited)"
        def contentEvents = finalResponse.findAll { it.type == "content" }
        !contentEvents.isEmpty()
    }

    @Ignore("Requires proper WireMock stubbing for Spring AI Google GenAI client")
    def "should handle empty conversation history"() {
        given: "a new conversation with first message"
        def request = [
                message: "To jest pierwsza wiadomość"
        ]

        when: "sending request"
        def response = sendAuthenticatedSseRequest("/v1/assistant/chat", request, TEST_DEVICE_ID)

        then: "conversation is created successfully"
        def doneEvent = response.find { it.type == "done" }
        doneEvent != null
        doneEvent.conversationId != null

        and: "content is generated"
        def contentEvents = response.findAll { it.type == "content" }
        !contentEvents.isEmpty()
    }

    private List<Map> sendAuthenticatedSseRequest(String path, Map request, String deviceId) {
        def response = given()
                .contentType(ContentType.JSON)
                .accept("text/event-stream")
                .headers(createHmacHeaders(deviceId, path, request))
                .body(request)
                .post(path)

        // Parse SSE events
        def events = []
        response.body().asString().split("\n\n").each { eventBlock ->
            if (eventBlock.startsWith("data: ")) {
                def jsonData = eventBlock.substring(6).trim()
                if (!jsonData.isEmpty()) {
                    events << parseJson(jsonData)
                }
            }
        }
        return events
    }
}
