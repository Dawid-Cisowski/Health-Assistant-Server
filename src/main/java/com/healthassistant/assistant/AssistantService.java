package com.healthassistant.assistant;

import com.healthassistant.assistant.api.AssistantFacade;
import com.healthassistant.assistant.api.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class AssistantService implements AssistantFacade {

    private final ChatClient chatClient;
    private final ConversationService conversationService;

    private String buildSystemInstruction(LocalDate currentDate) {
        return """
            CURRENT DATE: %s

            You are a health assistant for the Health Assistant app.
            You respond in a calm, supportive and concrete tone.

            Your main operating principles:
            - You respond briefly and concisely (1-3 sentences for simple questions).
            - You use only data received from tools - you do not invent missing information.
            - If data is missing, you say so directly.
            - You always refer to specific numbers and facts when available.
            - If needed, you may add simple health advice (unforced, non-intrusive).

            Tool selection and usage:
            - Use tools only when needed to answer.
            - Choose the tool according to the question content:
              • getSleepData - sleep data,
              • getStepsData - steps and activity,
              • getWorkoutData - workouts,
              • getDailySummary - complete daily summary.
            - If the question requires time calculation - do it yourself based on the CURRENT DATE above.
            - IMPORTANT: All date parameters in tool calls MUST be in ISO-8601 format: YYYY-MM-DD (e.g. 2025-11-24).
              Never use words like "today", "yesterday" in tool parameters.

            Time interpretation:
            - Recognize natural time expressions such as:
              "last night", "yesterday", "today", "last week",
              "last month", "this week", "this month", "last 7 days", "last 30 days",
              "previous night", "last sleep", "this week", "this month".
            - Automatically convert them to actual date ranges in YYYY-MM-DD format based on the CURRENT DATE.
            - Never ask the user for a date if it can be unambiguously determined from the CURRENT DATE.
            - If the expression is ambiguous - choose the most natural interpretation,
              and only ask for clarification if inference is impossible.

            Conversion examples (assuming CURRENT DATE: 2025-11-24):
            - "today" → startDate: "2025-11-24", endDate: "2025-11-24"
            - "yesterday" → startDate: "2025-11-23", endDate: "2025-11-23"
            - "last week" / "last 7 days" → startDate: "2025-11-17", endDate: "2025-11-24" (7 days back)
            - "last month" / "last 30 days" → startDate: "2025-10-25", endDate: "2025-11-24" (30 days back)
            - "this week" → startDate: "2025-11-18" (Monday), endDate: "2025-11-24"
            - "this month" → startDate: "2025-11-01", endDate: "2025-11-24"
            - "last 14 days" / "last two weeks" → startDate: "2025-11-10", endDate: "2025-11-24"

            Response format:
            - Responses are maximally clear, data-based.
            - You may use emoji, but optionally and sparingly.
            - If the tool returns more data, present only those relevant to the question context.

            Most important:
            - Your response is always correct, precise and based solely on facts.
            - Avoid unnecessary introductions and explanations of how you work.
            """.formatted(currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    @Override
    public Flux<AssistantEvent> streamChat(ChatRequest request, String deviceId) {
        log.info("Processing streaming chat request for device {}: {} (conversationId: {})",
                deviceId, request.message(), request.conversationId());

        AssistantContext.setDeviceId(deviceId);

        return Mono.fromCallable(() -> {
                    var conversationId = conversationService.getOrCreateConversation(request.conversationId(), deviceId);
                    var history = conversationService.loadConversationHistory(conversationId);
                    var currentDate = LocalDate.now();
                    var systemInstruction = buildSystemInstruction(currentDate);
                    var messages = conversationService.buildMessageList(history, systemInstruction, request.message());
                    conversationService.saveMessage(conversationId, MessageRole.USER, request.message());
                    return new ConversationContext(conversationId, messages);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ctx -> {
                    var assistantResponse = new StringBuilder();
                    return chatClient.prompt()
                            .messages(ctx.messages())
                            .stream()
                            .chatResponse()
                            .flatMap(this::mapToAssistantEvents)
                            .doOnNext(event -> {
                                if (event instanceof ContentEvent(String content)) {
                                    assistantResponse.append(content);
                                }
                            })
                            .concatWith(Flux.just(new DoneEvent(ctx.conversationId())))
                            .doOnError(error -> log.error("Error in stream chat", error))
                            .onErrorResume(error -> Flux.just(createErrorEvent(error)))
                            .doFinally(signal -> Schedulers.boundedElastic().schedule(() -> {
                                if (!assistantResponse.isEmpty()) {
                                    conversationService.saveMessage(ctx.conversationId(), MessageRole.ASSISTANT, assistantResponse.toString());
                                    log.info("Saved assistant response ({} chars) to conversation {}", assistantResponse.length(), ctx.conversationId());
                                }
                                AssistantContext.clear();
                            }));
                });
    }

    private record ConversationContext(UUID conversationId, List<Message> messages) {}

    private Flux<AssistantEvent> mapToAssistantEvents(org.springframework.ai.chat.model.ChatResponse response) {
        var content = response.getResult().getOutput().getText();

        if (content == null || content.isBlank()) {
            return Flux.empty();
        }

        return Flux.just(new ContentEvent(content));
    }

    private AssistantEvent createErrorEvent(Throwable error) {
        log.error("Chat processing error", error);
        return new ErrorEvent("Sorry, an error occurred while processing your question.");
    }

    @Override
    public void deleteAllConversations() {
        conversationService.deleteAllConversations();
    }
}
