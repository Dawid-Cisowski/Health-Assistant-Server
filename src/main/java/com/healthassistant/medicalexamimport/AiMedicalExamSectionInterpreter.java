package com.healthassistant.medicalexamimport;

import com.healthassistant.config.AiMetricsRecorder;
import com.healthassistant.medicalexamimport.api.dto.ExtractedResultData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
class AiMedicalExamSectionInterpreter implements MedicalExamSectionInterpreter {

    private static final String SYSTEM_PROMPT = """
            Jesteś klinicznym diagnostą laboratoryjnym. Napisz krótką, czytelną interpretację wyników badań laboratoryjnych po polsku.

            ZASADY:
            - Pisz po polsku, profesjonalnie ale przystępnie
            - MAKSYMALNIE 3-4 zdania, bez nagłówków, bez list — płynny tekst
            - Używaj **pogrubień** dla klinicznie istotnych nieprawidłowych wartości
            - Dla wyników prawidłowych: krótko potwierdź, że są w normie
            - Dla nieprawidłowych: opisz co może wskazywać używając "może wskazywać na" (nigdy nie diagnozuj)
            - Jeśli znacznie poza normą: zalecaj konsultację z lekarzem
            - Dla sekcji opisowych bez wyników numerycznych: zinterpretuj opis badania
            """;

    private final ChatClient chatClient;
    private final AiMetricsRecorder aiMetrics;

    @Override
    public List<String> interpretSections(List<ExtractedExamData.ExtractedSectionData> sections) {
        return sections.stream()
                .map(this::interpretSection)
                .toList();
    }

    private String interpretSection(ExtractedExamData.ExtractedSectionData section) {
        try {
            var response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildContext(section))
                    .call()
                    .chatResponse();

            var text = Optional.ofNullable(response)
                    .map(r -> r.getResult())
                    .map(result -> result.getOutput().getText())
                    .orElse(null);

            Optional.ofNullable(response)
                    .map(r -> r.getMetadata())
                    .map(m -> m.getUsage())
                    .ifPresent(usage -> {
                        log.debug("Section {} AI interpretation: prompt={} tokens, completion={} tokens",
                                section.examTypeCode(), usage.getPromptTokens(), usage.getCompletionTokens());
                        aiMetrics.recordImportTokens("section_interpret",
                                usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null,
                                usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null);
                    });

            return text;
        } catch (Exception e) {
            log.warn("AI section interpretation failed for {}: {}", section.examTypeCode(), e.getMessage());
            return null;
        }
    }

    private String buildContext(ExtractedExamData.ExtractedSectionData section) {
        var title = section.title() != null ? section.title() : section.examTypeCode();
        var header = "Sekcja: " + title + " (" + section.examTypeCode() + ")\n\n";

        var results = Optional.ofNullable(section.results()).orElse(List.of());
        if (!results.isEmpty()) {
            var resultsText = results.stream()
                    .map(this::formatResult)
                    .collect(Collectors.joining("\n"));
            return header + "Wyniki:\n" + resultsText;
        }

        if (section.reportText() != null) {
            return header + "Opis badania:\n" + section.reportText();
        }

        if (section.conclusions() != null) {
            return header + "Wnioski:\n" + section.conclusions();
        }

        return header + "Brak danych do interpretacji.";
    }

    private String formatResult(ExtractedResultData result) {
        var markerLabel = result.markerName() != null
                ? result.markerCode() + " (" + result.markerName() + ")"
                : result.markerCode();
        var value = result.valueNumeric() != null
                ? result.valueNumeric().toPlainString()
                : Optional.ofNullable(result.valueText()).orElse("N/A");
        var unit = result.unit() != null ? " " + result.unit() : "";

        var refRange = "";
        if (result.refRangeLow() != null && result.refRangeHigh() != null) {
            refRange = " [ref: " + result.refRangeLow() + "-" + result.refRangeHigh() + "]";
        } else if (result.refRangeText() != null) {
            refRange = " [ref: " + result.refRangeText() + "]";
        }

        return "- " + markerLabel + ": " + value + unit + refRange;
    }
}
