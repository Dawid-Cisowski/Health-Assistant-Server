package com.healthassistant.reports;

import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.reports.api.dto.GoalEvaluation;
import com.healthassistant.reports.api.dto.GoalResult;
import com.healthassistant.reports.api.dto.ComparisonMetric;
import com.healthassistant.reports.api.dto.PeriodComparison;
import com.healthassistant.reports.api.dto.ReportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
class ReportAiService {

    private static final int MAX_AI_INPUT_LENGTH = 50_000;

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
        Jestes asystentem zdrowia generujacym szczegolowe podsumowania raportow zdrowotnych w formacie Markdown.

        ZASADY:
        - Pisz po polsku
        - Uzywaj formatowania Markdown: ## naglowki, **bold** dla kluczowych metryk, listy punktowane
        - Styl: motywujacy, konkretny, z konkretnymi liczbami
        - NIE wymyslaj danych - uzywaj TYLKO tego co dostales
        - Uzywaj emoji tematycznie (np. przy celach, sekcjach)

        STRUKTURA RAPORTU:

        ## Podsumowanie
        2-3 zdania ogolnego przegladu dnia/okresu z kluczowymi osiagnieciami.

        ## Cele
        Przejdz przez KAZDY cel - czy osiagniety, jak blisko, co pomoglo/zabraklo.
        Uzyj ✅ dla osiagnietych i ❌ dla nieosiagnietych.

        ## Porownanie z poprzednim okresem
        Skomentuj najwazniejsze zmiany (wzrosty/spadki) z konkretnymi liczbami i procentami.
        Wyrozni pozytywne trendy i obszary do poprawy.

        ## Aktywnosc i trening
        Kroki, aktywne minuty, spalone kalorie, treningi - co poszlo dobrze, co mozna poprawic.

        ## Sen i regeneracja
        Czas snu, jakosc, porownanie z celem 7h.

        ## Odzywianie
        Kalorie, bialko, zdrowe posilki - czy w normie, co skorygowac.

        ## Wnioski i rekomendacje
        3-5 konkretnych, praktycznych wskazowek na kolejny dzien/tydzien/miesiac.
        """;

    Optional<String> generateDailyAiSummary(DailySummary data, GoalEvaluation goals, PeriodComparison comparison) {
        String userMessage = buildDailyPrompt(data, goals, comparison);
        return callAi(userMessage);
    }

    Optional<String> generateRangeAiSummary(DailySummaryRangeSummaryResponse data, GoalEvaluation goals,
                                            PeriodComparison comparison, ReportType type) {
        String userMessage = buildRangePrompt(data, goals, comparison, type);
        return callAi(userMessage);
    }

    private Optional<String> callAi(String userMessage) {
        String effectiveMessage = userMessage.length() > MAX_AI_INPUT_LENGTH
                ? userMessage.substring(0, MAX_AI_INPUT_LENGTH) + "\n\n[Dane skrocone]"
                : userMessage;

        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(effectiveMessage)
                    .call()
                    .chatResponse();

            String content = chatResponse.getResult() != null
                    ? chatResponse.getResult().getOutput().getText()
                    : null;

            if (content == null || content.isBlank()) {
                log.warn("AI returned empty report summary");
                return Optional.empty();
            }

            log.info("AI report summary generated: {} chars", content.length());
            return Optional.of(content);
        } catch (Exception e) {
            log.error("Failed to generate AI report summary", e);
            return Optional.empty();
        }
    }

    private String buildDailyPrompt(DailySummary data, GoalEvaluation goals, PeriodComparison comparison) {
        StringBuilder sb = new StringBuilder();
        sb.append("Wygeneruj podsumowanie dziennego raportu zdrowotnego za ").append(data.date()).append(".\n\n");

        sb.append("DANE ZDROWOTNE:\n");
        sb.append(data.toAiDataContext(sanitizer()));
        sb.append("\n");

        appendGoals(sb, goals);
        appendComparison(sb, comparison);

        return sb.toString();
    }

    private String buildRangePrompt(DailySummaryRangeSummaryResponse data, GoalEvaluation goals,
                                    PeriodComparison comparison, ReportType type) {
        StringBuilder sb = new StringBuilder();
        String periodLabel = type == ReportType.WEEKLY ? "tygodniowego" : "miesiecznego";
        sb.append("Wygeneruj podsumowanie ").append(periodLabel).append(" raportu zdrowotnego za okres ")
                .append(data.startDate()).append(" do ").append(data.endDate()).append(".\n\n");

        sb.append("DANE ZDROWOTNE:\n");
        sb.append(data.toAiDataContext(sanitizer()));
        sb.append("\n");

        appendGoals(sb, goals);
        appendComparison(sb, comparison);

        return sb.toString();
    }

    private void appendGoals(StringBuilder sb, GoalEvaluation goals) {
        sb.append("CELE (").append(goals.achieved()).append("/").append(goals.total()).append(" osiagnietych):\n");
        goals.details().stream()
                .map(g -> "- " + g.displayName() + ": " + (g.achieved() ? "OSIAGNIETY" : "NIEosiagniety")
                        + " (wynik: " + g.actualValue() + ", cel: " + g.targetValue() + ")")
                .forEach(line -> sb.append(line).append("\n"));
        sb.append("\n");
    }

    private void appendComparison(StringBuilder sb, PeriodComparison comparison) {
        if (comparison == null || comparison.metrics() == null || comparison.metrics().isEmpty()) {
            return;
        }
        sb.append("POROWNANIE Z POPRZEDNIM OKRESEM (")
                .append(comparison.previousPeriodStart()).append(" do ").append(comparison.previousPeriodEnd())
                .append("):\n");
        comparison.metrics().stream()
                .map(m -> "- " + m.displayName() + ": " + m.currentValue() + " vs " + m.previousValue()
                        + (m.changePercent() != null ? " (" + formatChange(m.changePercent()) + ")" : " (brak danych porownawczych)"))
                .forEach(line -> sb.append(line).append("\n"));
    }

    private static String formatChange(double percent) {
        return (percent >= 0 ? "+" : "") + String.format("%.1f%%", percent);
    }

    private static UnaryOperator<String> sanitizer() {
        return input -> {
            if (input == null) return "";
            return input.length() > 500 ? input.substring(0, 500) : input;
        };
    }
}
