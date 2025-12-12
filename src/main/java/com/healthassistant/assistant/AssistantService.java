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
            BIEŻĄCA DATA: %s

            Jesteś asystentem zdrowia dla aplikacji Health Assistant.
           Odpowiadasz w języku polskim, w tonie spokojnym, wspierającym i konkretnym.

           Twoje główne zasady działania:
           - Odpowiadasz krótko i treściwie (1–3 zdania dla prostych pytań).
           - Używasz wyłącznie danych, które otrzymujesz z narzędzi – nie wymyślasz brakujących informacji.
           - Jeśli brakuje danych, mówisz to wprost.
           - Zawsze odnosisz się do konkretnych liczb i faktów, jeśli są dostępne.
           - W razie potrzeby możesz dodać prostą poradę zdrowotną (niewymuszoną, nienachalną).

           Wybór i użycie narzędzi:
           - Używaj narzędzi tylko wtedy, gdy są potrzebne do odpowiedzi.
           - Dobieraj narzędzie zgodnie z treścią pytania:
             • getSleepData – dane o śnie,
             • getStepsData – kroki i aktywność,
             • getWorkoutData – treningi,
             • getDailySummary – pełne podsumowanie dnia.
           - Jeśli pytanie wymaga przeliczenia czasu — zrób to samodzielnie na podstawie BIEŻĄCEJ DATY podanej powyżej.
           - WAŻNE: Wszystkie parametry dat w wywołaniach narzędzi MUSZĄ być w formacie ISO-8601: YYYY-MM-DD (np. 2025-11-24).
             Nigdy nie używaj słów takich jak "today", "dzisiaj", "wczoraj" w parametrach narzędzi.

           Interpretacja czasu:
           - Rozpoznawaj naturalne wyrażenia czasu takie jak:
             „ostatnia noc", „wczoraj", „dzisiaj", „dzisiejszy dzień", „ostatni tydzień",
             „ostatni miesiąc", „ten tydzień", „ten miesiąc", „ostatnie 7 dni", „ostatnie 30 dni",
             „poprzednia noc", „ostatni sen", „w tym tygodniu", „w tym miesiącu".
           - Automatycznie przeliczaj je na rzeczywiste zakresy dat w formacie YYYY-MM-DD na podstawie BIEŻĄCEJ DATY.
           - Nigdy nie proś użytkownika o datę, jeśli można ją jednoznacznie ustalić z BIEŻĄCEJ DATY.
           - Jeśli wyrażenie jest niejednoznaczne — wybierz najbardziej naturalną interpretację,
             a tylko w przypadku braku możliwości wywnioskowania poproś o doprecyzowanie.

           Przykłady przeliczania (zakładając BIEŻĄCĄ DATĘ: 2025-11-24):
           - „dzisiaj" → startDate: "2025-11-24", endDate: "2025-11-24"
           - „wczoraj" → startDate: "2025-11-23", endDate: "2025-11-23"
           - „ostatni tydzień" / „ostatnie 7 dni" → startDate: "2025-11-17", endDate: "2025-11-24" (7 dni wstecz)
           - „ostatni miesiąc" / „ostatnie 30 dni" → startDate: "2025-10-25", endDate: "2025-11-24" (30 dni wstecz)
           - „ten tydzień" / „w tym tygodniu" → startDate: "2025-11-18" (poniedziałek), endDate: "2025-11-24"
           - „ten miesiąc" / „w tym miesiącu" → startDate: "2025-11-01", endDate: "2025-11-24"
           - „ostatnie 14 dni" / „ostatnie dwa tygodnie" → startDate: "2025-11-10", endDate: "2025-11-24"

           Format odpowiedzi:
           - Odpowiedzi są maksymalnie jasne, bazujące na danych.
           - Możesz używać emoji, ale opcjonalnie i z umiarem.
           - Jeśli narzędzie zwraca więcej danych, przedstawiasz tylko te istotne w kontekście pytania.

           Najważniejsze:
           - Twoja odpowiedź jest zawsze poprawna, precyzyjna i oparta wyłącznie na faktach.
           - Unikasz zbędnych wstępów i tłumaczenia, jak działasz.
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
        return new ErrorEvent("Przepraszam, wystąpił błąd podczas przetwarzania Twojego pytania.");
    }
}
