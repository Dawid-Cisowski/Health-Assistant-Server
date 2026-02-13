package com.healthassistant

class ConversationApiSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-convo-api"
    private static final String SECRET = TEST_SECRET_BASE64

    def setup() {
        setupGeminiApiMock("Oto odpowiedź asystenta zdrowotnego.")
    }

    def cleanup() {
        authenticatedDeleteRequest(DEVICE_ID, SECRET, "/v1/assistant/conversations")
    }

    // --- GET /v1/assistant/conversations ---

    def "should return empty page when device has no conversations"() {
        when: "listing conversations"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "/v1/assistant/conversations")
                .get("/v1/assistant/conversations")

        then: "returns 200 with empty page"
        response.statusCode() == 200
        def body = response.body().as(Map)
        body.content == []
        body.totalElements == 0
    }

    def "should list conversations after chat"() {
        given: "a conversation exists"
        def chatResponse = sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Cześć"], DEVICE_ID)
        def conversationId = chatResponse.find { it.type == "done" }?.conversationId

        when: "listing conversations"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "/v1/assistant/conversations")
                .get("/v1/assistant/conversations")

        then: "returns page with one conversation"
        response.statusCode() == 200
        def body = response.body().as(Map)
        body.totalElements == 1
        body.content.size() == 1

        and: "conversation has correct data"
        def conv = body.content[0]
        conv.conversationId == conversationId
        conv.createdAt != null
        conv.updatedAt != null
        conv.messageCount >= 2  // at least user + assistant
    }

    def "should include preview from first user message"() {
        given: "a conversation with a known first message"
        def firstMessage = "Ile kroków zrobiłem dzisiaj?"
        sendAuthenticatedSseRequest("/v1/assistant/chat", [message: firstMessage], DEVICE_ID)

        when: "listing conversations"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "/v1/assistant/conversations")
                .get("/v1/assistant/conversations")

        then: "preview contains first user message"
        response.statusCode() == 200
        def body = response.body().as(Map)
        body.content[0].preview == firstMessage
    }

    def "should truncate long preview to 100 characters"() {
        given: "a conversation with a very long first message"
        def longMessage = "A" * 150
        sendAuthenticatedSseRequest("/v1/assistant/chat", [message: longMessage], DEVICE_ID)

        when: "listing conversations"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "/v1/assistant/conversations")
                .get("/v1/assistant/conversations")

        then: "preview is truncated"
        response.statusCode() == 200
        def preview = response.body().as(Map).content[0].preview
        preview.length() == 103  // 100 chars + "..."
        preview.endsWith("...")
    }

    def "should paginate conversations"() {
        given: "three conversations exist"
        3.times {
            sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Wiadomość ${it}"], DEVICE_ID)
        }

        when: "requesting page 0 with size 2"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "/v1/assistant/conversations?page=0&size=2")
                .get("/v1/assistant/conversations?page=0&size=2")

        then: "returns 2 items with correct pagination metadata"
        response.statusCode() == 200
        def body = response.body().as(Map)
        body.content.size() == 2
        body.totalElements == 3
        body.totalPages == 2

        when: "requesting page 1"
        def response2 = authenticatedGetRequest(DEVICE_ID, SECRET, "/v1/assistant/conversations?page=1&size=2")
                .get("/v1/assistant/conversations?page=1&size=2")

        then: "returns remaining 1 item"
        response2.statusCode() == 200
        def body2 = response2.body().as(Map)
        body2.content.size() == 1
    }

    def "should sort conversations by most recently updated"() {
        given: "two conversations - second one updated later"
        def resp1 = sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Pierwsza"], DEVICE_ID)
        def id1 = resp1.find { it.type == "done" }?.conversationId
        Thread.sleep(100)  // ensure different updatedAt
        def resp2 = sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Druga"], DEVICE_ID)
        def id2 = resp2.find { it.type == "done" }?.conversationId

        when: "listing conversations"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "/v1/assistant/conversations")
                .get("/v1/assistant/conversations")

        then: "most recently updated conversation is first"
        response.statusCode() == 200
        def content = response.body().as(Map).content
        content[0].conversationId == id2
        content[1].conversationId == id1
    }

    def "should not list conversations from other devices"() {
        given: "a conversation on this device"
        sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Test"], DEVICE_ID)

        when: "different device lists conversations"
        def response = authenticatedGetRequest("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, "/v1/assistant/conversations")
                .get("/v1/assistant/conversations")

        then: "returns empty"
        response.statusCode() == 200
        response.body().as(Map).totalElements == 0
    }

    def "should clamp negative page and size to safe values"() {
        given: "a conversation exists"
        sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Test"], DEVICE_ID)

        when: "requesting with negative params"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, "/v1/assistant/conversations?page=-1&size=-5")
                .get("/v1/assistant/conversations?page=-1&size=-5")

        then: "returns 200 with clamped values (not 400 or 500)"
        response.statusCode() == 200
    }

    // --- GET /v1/assistant/conversations/{id} ---

    def "should return conversation detail with messages"() {
        given: "a conversation with messages"
        def chatResponse = sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Jak się masz?"], DEVICE_ID)
        def conversationId = chatResponse.find { it.type == "done" }?.conversationId

        when: "getting conversation detail"
        def path = "/v1/assistant/conversations/${conversationId}"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)

        then: "returns 200 with conversation data"
        response.statusCode() == 200
        def body = response.body().as(Map)
        body.conversationId == conversationId
        body.createdAt != null
        body.updatedAt != null

        and: "messages are present in chronological order"
        body.messages.size() >= 2
        body.messages[0].role == "USER"
        body.messages[0].content == "Jak się masz?"
        body.messages[1].role == "ASSISTANT"
        body.messages[1].content != null
        body.messages[1].createdAt != null
    }

    def "should return messages in chronological order"() {
        given: "a conversation with multiple exchanges"
        def resp1 = sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Pierwsza wiadomość"], DEVICE_ID)
        def conversationId = resp1.find { it.type == "done" }?.conversationId
        sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Druga wiadomość", conversationId: conversationId], DEVICE_ID)

        when: "getting conversation detail"
        def path = "/v1/assistant/conversations/${conversationId}"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)

        then: "messages are ordered chronologically"
        response.statusCode() == 200
        def messages = response.body().as(Map).messages
        messages.size() >= 4  // 2 user + 2 assistant

        and: "timestamps are in ascending order"
        (0..<messages.size() - 1).every { i ->
            messages[i].createdAt <= messages[i + 1].createdAt
        }
    }

    def "should return 404 for non-existent conversation"() {
        when: "requesting non-existent conversation"
        def path = "/v1/assistant/conversations/${UUID.randomUUID()}"
        def response = authenticatedGetRequest(DEVICE_ID, SECRET, path)
                .get(path)

        then: "returns 404"
        response.statusCode() == 404
    }

    def "should return 404 when accessing other device's conversation"() {
        given: "a conversation on this device"
        def chatResponse = sendAuthenticatedSseRequest("/v1/assistant/chat", [message: "Secret"], DEVICE_ID)
        def conversationId = chatResponse.find { it.type == "done" }?.conversationId

        when: "different device tries to access it"
        def path = "/v1/assistant/conversations/${conversationId}"
        def response = authenticatedGetRequest("different-device-id", DIFFERENT_DEVICE_SECRET_BASE64, path)
                .get(path)

        then: "returns 404 (not 403 - don't leak existence)"
        response.statusCode() == 404
    }

    // --- Helpers ---

    private List<Map> sendAuthenticatedSseRequest(String path, Map request, String deviceId) {
        def requestJson = groovy.json.JsonOutput.toJson(request)

        def url = "http://localhost:${port}${path}"
        def connection = new URL(url).openConnection()
        connection.setRequestMethod("POST")
        connection.setDoOutput(true)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.setConnectTimeout(30000)
        connection.setReadTimeout(60000)

        createHmacHeadersFromString(deviceId, path, requestJson).each { key, value ->
            connection.setRequestProperty(key, value)
        }

        connection.outputStream.write(requestJson.getBytes("UTF-8"))
        connection.outputStream.flush()

        def responseBody = connection.inputStream.text

        def events = []
        responseBody.split("\n\n").each { eventBlock ->
            def lines = eventBlock.split("\n")
            lines.each { line ->
                if (line.startsWith("data:")) {
                    def jsonData = line.substring(5).trim()
                    if (!jsonData.isEmpty()) {
                        events << parseJson(jsonData)
                    }
                }
            }
        }
        return events
    }
}
