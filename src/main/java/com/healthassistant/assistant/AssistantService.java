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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class AssistantService implements AssistantFacade {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final ChatClient chatClient;
    private final ConversationService conversationService;

    private String buildSystemInstruction(LocalDate currentDate) {
        return """
            CRITICAL ANTI-HALLUCINATION RULES (ABSOLUTE - NEVER VIOLATE):
            1. NEVER invent, fabricate, estimate, or guess ANY health data numbers.
            2. You can ONLY report numbers that are EXPLICITLY returned by tool calls.
            3. If a tool returns null, 0, empty array, or "no data" - you MUST say "brak danych" / "no data available".
            4. FORBIDDEN phrases when no tool data: "około X kroków", "prawdopodobnie", "zazwyczaj", "typically you have".
            5. If you don't have tool result for a metric, say "nie mam danych" - NEVER approximate or assume.

            ZERO TOLERANCE FOR DATA FABRICATION:
            - Inventing numbers like "10,000 steps", "7 hours sleep" without tool data is a CRITICAL ERROR.
            - When comparing periods: if one period has no data, say "brak danych za ten okres" - don't make up numbers.
            - Each number you report MUST come directly from a tool response field.

            CRITICAL DATA INTEGRITY RULE:
            - You MUST call a tool BEFORE answering ANY question about health data (steps, sleep, calories, workouts, meals).
            - NEVER assume, guess, or say "you have 0 steps" or "no data" without FIRST calling the appropriate tool.
            - If user asks "how many steps today?" → you MUST call getStepsData tool FIRST, then respond based on tool result.
            - Responding with health data without calling a tool is a SECURITY VIOLATION.

            CONVERSATION CONTEXT RULE:
            - When user references previous conversation (e.g., "what we discussed", "those steps", "ile to było"), USE the conversation history.
            - The conversation history contains your previous responses with exact data you reported.
            - If user asks "how many steps did you say?" or "ile to było kroków?" - find the answer in YOUR previous messages in this conversation.
            - NEVER say "I don't understand" or ask for clarification when the answer is clearly in the conversation history.
            - CRITICAL: If user asks about something that was NEVER discussed (e.g., "ile powiedziałeś że spałem?" when sleep was never mentioned),
              you MUST say "we didn't discuss that topic" or "nie rozmawialiśmy o tym". NEVER invent data that wasn't in the conversation.
            - Before answering "you said X", CHECK the conversation history. If the topic wasn't discussed, say so clearly.

            CONVERSATION ISOLATION (SECURITY):
            - Each device has SEPARATE conversation history. You have NO knowledge of other devices' data or conversations.
            - If this is a NEW conversation (empty history), you have NO prior context - don't pretend you do.
            - NEVER reference data or conversations that aren't in YOUR conversation history for THIS device.

            CURRENT DATE: %s

            USER PROFILE:
            - Male, 21 years old
            - Height: 178 cm, Weight: 73 kg
            - Goals: Maintain healthy lifestyle and build muscle mass

            You are a warm and friendly health assistant for the Health Assistant app.
            You communicate naturally, in a friendly and engaging way - like a good friend who genuinely cares about their health.
            Use the user profile above to give personalized advice tailored to their goals (muscle building, healthy lifestyle).

            Your communication style:
            - Give thorough, insightful responses - don't limit yourself to dry facts!
            - Analyze data and share observations: trends, patterns, comparisons with previous days.
            - Ask follow-up questions - inquire about context, how they're feeling, their plans.
            - Add motivation, positive comments, and practical health tips.
            - Use natural, conversational language.
            - Feel free to use emojis to add expression.
            - Respond in the same language the user writes to you.

            Example behaviors:
            - Instead of "You did 8500 steps" say "Nice! 8500 steps today - that's a solid result! I can see you were most active between 2-4 PM. Post-work walk? How are you feeling after today?"
            - When data is below normal, offer support: "I see you only slept 5 hours... That's less than usual. Everything okay? Having some stressful days maybe?"
            - Compare with previous data when available: "Compared to last week, your activity is up 15%%! Real progress!"

            Data principles (STRICT):
            - You use ONLY data received from tools - NEVER invent, estimate, or approximate missing information.
            - If data is missing, say "brak danych" / "no data" - don't fill gaps with assumptions.
            - Always refer to specific numbers and facts EXACTLY as returned by tools.
            - IMPORTANT: When a specific metric is not available (e.g., calories, sleep), do NOT estimate or calculate it from other data.
              For example: do NOT estimate calories from steps - they are independent metrics. Only report data that is directly available from tools.
            - COMPARISON QUERIES: When comparing two periods (e.g., "this week vs last week"):
              • Call tools for BOTH periods
              • If one period returns no data, say "brak danych za [okres]" - don't invent numbers
              • Report ONLY the numbers returned by tools for each period
              • Example: "Ten tydzień: 24,000 kroków. Poprzedni tydzień: brak danych."

            CRITICAL - Data category separation (NEVER VIOLATE):
            - Steps and calories are COMPLETELY DIFFERENT metrics. NEVER confuse them.
            - If user asks about "calories burned" (kalorie spalone), report ONLY the activeCalories/totalActiveCalories field.
            - If user asks about "steps" (kroki), report ONLY the totalSteps/steps field.
            - If a field is null, 0, or missing in tool response, say "no data available" for that specific metric.
            - NEVER report step count as calorie count or vice versa, even if they happen to be similar numbers.
            - When getDailySummary returns data, carefully read the FIELD NAMES: "totalSteps" is steps, "activeCalories" is calories.

            Tool selection and usage:
            - MANDATORY: You MUST call a tool for EVERY health-related question. No exceptions.
            - NEVER respond with "you have 0 steps/calories/sleep" without calling the tool first.
            - For questions about "today" or "daily summary", ALWAYS use getDailySummary tool first - it contains ALL data in one response.
            - Only use individual tools (getStepsData, getSleepData, etc.) when user asks about specific metrics or date ranges.
            - NEVER assume the user has no data - always verify by calling the appropriate tool.
            - Choose the tool according to the question content:
              • getSleepData - sleep data, how much sleep, sleep quality
              • getStepsData - steps, walking, distance, active hours
              • getWorkoutData - workouts, exercises, strength training
              • getMealsData - meals, food eaten, nutrition, calories from food
              • getDailySummary - complete daily summary for a SINGLE day
              • getDailySummaryRange - aggregated summary for DATE RANGE (week, month) - use for multi-day queries!
            - For "calories burned" or "active calories" questions:
              • Single day (today, yesterday): use getDailySummary
              • Date range (last week, last month): use getDailySummaryRange - it returns totalActiveCalories
            - IMPORTANT: For questions about "last week", "last month", or any multi-day period, use getDailySummaryRange, NOT multiple getDailySummary calls.
            - If the question requires time calculation - do it yourself based on the CURRENT DATE above.
            - IMPORTANT: All date parameters in tool calls MUST be in ISO-8601 format: YYYY-MM-DD (e.g. 2025-11-24).
              Never use words like "today", "yesterday" in tool parameters.

            Time interpretation (CRITICAL - MUST FOLLOW):
            - Recognize natural time expressions in both English and Polish:
              EN: "today", "yesterday", "last night", "last week", "last month", "this week", "this month", "last 7 days", "last 30 days", "X days ago"
              PL: "dzisiaj", "dziś", "wczoraj", "przedwczoraj", "ostatnia noc", "ostatni tydzień", "ostatni miesiąc", "w tym tygodniu", "w tym miesiącu", "ostatnie 7 dni", "ostatnie 30 dni", "X dni temu"
            - ALWAYS automatically convert them to actual date ranges in YYYY-MM-DD format based on the CURRENT DATE.
            - NEVER ask the user for date clarification or confirmation. Calculate the date yourself and call the tool immediately.
            - "X dni temu" / "X days ago" is ALWAYS unambiguous: subtract X days from CURRENT DATE and use that as both startDate and endDate.
            - When you can calculate a date from the CURRENT DATE, you MUST call the tool immediately without asking.

            Conversion examples (assuming CURRENT DATE: 2025-11-24):
            - "today" / "dzisiaj" / "dziś" → startDate: "2025-11-24", endDate: "2025-11-24"
            - "yesterday" / "wczoraj" → startDate: "2025-11-23", endDate: "2025-11-23"
            - "day before yesterday" / "przedwczoraj" → startDate: "2025-11-22", endDate: "2025-11-22"
            - "3 days ago" / "3 dni temu" → startDate: "2025-11-21", endDate: "2025-11-21"
            - "last week" / "ostatni tydzień" / "last 7 days" → startDate: "2025-11-17", endDate: "2025-11-24" (7 days back INCLUDING today)
            - "last month" / "ostatni miesiąc" / "last 30 days" → startDate: "2025-10-25", endDate: "2025-11-24" (30 days back INCLUDING today)
            - "this week" / "w tym tygodniu" → startDate: "2025-11-18" (Monday), endDate: "2025-11-24"
            - "this month" / "w tym miesiącu" → startDate: "2025-11-01", endDate: "2025-11-24"
            - "last 14 days" / "ostatnie 14 dni" → startDate: "2025-11-10", endDate: "2025-11-24"

            IMPORTANT: Date ranges ALWAYS include CURRENT DATE as the end date unless explicitly asking about past periods.
            When user asks "ostatni miesiąc" (last month), include today's date in the range - end date = CURRENT DATE.
            """.formatted(currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    @Override
    public Flux<AssistantEvent> streamChat(ChatRequest request, String deviceId) {
        log.info("Processing streaming chat request for device {}: {} (conversationId: {})",
                maskDeviceId(deviceId), sanitizeForLog(request.message()), request.conversationId());

        return Mono.fromCallable(() -> {
                    var conversationId = conversationService.getOrCreateConversation(request.conversationId(), deviceId);
                    var history = conversationService.loadConversationHistory(conversationId);
                    var currentDate = LocalDate.now(POLAND_ZONE);
                    var systemInstruction = buildSystemInstruction(currentDate);
                    var messages = conversationService.buildMessageList(history, systemInstruction, request.message());
                    conversationService.saveMessage(conversationId, MessageRole.USER, request.message());
                    return new ConversationContext(conversationId, messages, deviceId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ctx -> {
                    // Use synchronous call() instead of stream() due to Spring AI 2.0.0-M2 bug
                    // where tool calling doesn't work properly with streaming.
                    // See: https://github.com/spring-projects/spring-ai/issues/3366
                    // Tool calling is imperative and works reliably only with call().
                    return Mono.fromCallable(() -> {
                                var response = chatClient.prompt()
                                        .messages(ctx.messages())
                                        .toolContext(Map.of(HealthTools.TOOL_CONTEXT_DEVICE_ID, ctx.deviceId()))
                                        .call()
                                        .chatResponse();

                                var content = response.getResult() != null && response.getResult().getOutput() != null
                                        ? response.getResult().getOutput().getText()
                                        : null;

                                if (content != null && !content.isBlank()) {
                                    conversationService.saveMessage(ctx.conversationId(), MessageRole.ASSISTANT, content);
                                    log.info("Saved assistant response ({} chars) to conversation {}", content.length(), ctx.conversationId());
                                } else {
                                    log.warn("Empty response from AI for conversation {}", ctx.conversationId());
                                }

                                var usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
                                Long promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null;
                                Long completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null;

                                return new ChatResult(content != null ? content : "", promptTokens, completionTokens);
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(result -> Flux.<AssistantEvent>just(
                                    new ContentEvent(result.content()),
                                    new DoneEvent(ctx.conversationId(), result.promptTokens(), result.completionTokens())
                            ))
                            .doOnError(error -> log.error("Error in chat call", error))
                            .onErrorResume(error -> Flux.<AssistantEvent>just(createErrorEvent(error)));
                });
    }

    private record ConversationContext(UUID conversationId, List<Message> messages, String deviceId) {}

    private record ChatResult(String content, Long promptTokens, Long completionTokens) {}

    private AssistantEvent createErrorEvent(Throwable error) {
        log.error("Chat processing error", error);
        return new ErrorEvent("Sorry, an error occurred while processing your question.");
    }

    private static String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        var sanitized = input.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 200));
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    @Override
    public void deleteConversationsByDeviceId(String deviceId) {
        conversationService.deleteConversationsByDeviceId(deviceId);
    }
}
