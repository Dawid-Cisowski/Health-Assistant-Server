package com.healthassistant.medicalexams;

import com.healthassistant.config.AiMetricsRecorder;
import com.healthassistant.medicalexams.api.dto.HealthPillarDetailResponse;
import com.healthassistant.medicalexams.api.dto.HealthPillarMarkerResult;
import com.healthassistant.medicalexams.api.dto.HealthPillarSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
class HealthPillarAiService {

    private static final int MAX_AI_INPUT_LENGTH = 50_000;

    private static final List<String> ALL_PILLAR_CODES = List.of(
            "OVERALL", "CIRCULATORY", "DIGESTIVE", "METABOLISM", "BLOOD_IMMUNITY", "VITAMINS_MINERALS");

    private static final Map<String, String> PILLAR_NAMES_PL = Map.of(
            "OVERALL", "Ogólny stan zdrowia",
            "CIRCULATORY", "Układ Krążeniowy",
            "DIGESTIVE", "Układ Pokarmowy i Nerkowy",
            "METABOLISM", "Metabolizm i Hormony",
            "BLOOD_IMMUNITY", "Krew i Odporność",
            "VITAMINS_MINERALS", "Witaminy i Minerały");

    private static final String OVERALL_SYSTEM_PROMPT = """
            Jesteś asystentem zdrowotnym generującym ogólne podsumowanie stanu zdrowia.
            ZASADY: pisz po polsku, używaj Markdown (**bold**, ## nagłówki, listy).
            Bądź konkretny, nie wymyślaj danych. Zaznacz nieaktualne filary.
            STRUKTURA:
            ## Ogólny stan zdrowia
            2-3 zdania przekrojowej oceny.
            ## Kluczowe obserwacje
            - lista 3-5 najważniejszych faktów (niskie wyniki, wskaźniki poza normą)
            ## Zalecenia
            1-2 zdania ogólnych zaleceń.
            """;

    private static final String PILLAR_SYSTEM_PROMPT = """
            Jesteś asystentem zdrowotnym komentującym konkretny filar zdrowia.
            ZASADY: pisz po polsku, używaj Markdown. Odwołuj się do konkretnych wskaźników.
            Nie powtarzaj tytułu. Zaznacz nieaktualne dane. Brak danych = napisz że brak.
            Na końcu 1-2 zdania ogólnej konkluzji.
            """;

    private final HealthPillarAiSummaryRepository repository;
    private final ChatClient chatClient;
    private final AiMetricsRecorder aiMetrics;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String getOrGenerateOverallInsight(String deviceId, List<HealthPillarSummaryResponse> pillars) {
        var entity = repository.findByDeviceIdAndPillarCode(deviceId, "OVERALL")
                .orElseGet(() -> HealthPillarAiSummary.create(deviceId, "OVERALL"));

        if (entity.isCacheValid()) {
            log.debug("Returning cached overall insight for device {}", maskDeviceId(deviceId));
            return entity.getAiInsight();
        }

        var context = buildOverallContext(pillars);
        var insight = callAi(OVERALL_SYSTEM_PROMPT,
                "Wygeneruj ogólne podsumowanie zdrowia na podstawie tych danych:\n\n" + context,
                "pillar_overall");
        entity.cacheInsight(insight);
        repository.save(entity);
        log.info("Generated and cached overall AI insight for device {}", maskDeviceId(deviceId));
        return insight;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String getOrGeneratePillarInsight(String deviceId, String pillarCode, HealthPillarDetailResponse detail) {
        if (!ALL_PILLAR_CODES.contains(pillarCode)) {
            throw new IllegalArgumentException("Invalid pillar code: " + pillarCode);
        }
        var entity = repository.findByDeviceIdAndPillarCode(deviceId, pillarCode)
                .orElseGet(() -> HealthPillarAiSummary.create(deviceId, pillarCode));

        if (entity.isCacheValid()) {
            log.debug("Returning cached insight for pillar {} device {}", pillarCode, maskDeviceId(deviceId));
            return entity.getAiInsight();
        }

        var context = buildPillarContext(detail);
        var insight = callAi(PILLAR_SYSTEM_PROMPT,
                "Wygeneruj komentarz dla filaru zdrowia na podstawie tych danych:\n\n" + context,
                "pillar_" + pillarCode.toLowerCase(Locale.ROOT));
        entity.cacheInsight(insight);
        repository.save(entity);
        log.info("Generated and cached AI insight for pillar {} device {}", pillarCode, maskDeviceId(deviceId));
        return insight;
    }

    @Transactional
    void invalidateForDevice(String deviceId, Instant changedAt) {
        ALL_PILLAR_CODES.forEach(code ->
                repository.upsertLabResultsUpdatedAt(deviceId, code, changedAt));
        log.debug("Invalidated AI pillar insights for device {}", maskDeviceId(deviceId));
    }

    private String buildOverallContext(List<HealthPillarSummaryResponse> pillars) {
        return pillars.stream()
                .map(p -> {
                    var name = PILLAR_NAMES_PL.getOrDefault(p.pillarCode(), p.pillarCode());
                    var score = p.score() != null ? p.score() + "/100" : "brak danych";
                    var outdated = p.isOutdated() ? " (NIEAKTUALNE)" : "";
                    var date = p.latestDataDate() != null ? ", ostatnie badania: " + p.latestDataDate() : "";
                    var hero = p.heroMetric() != null
                            ? ", " + p.heroMetric().markerNamePl() + ": " + p.heroMetric().valueNumeric()
                            + (p.heroMetric().unit() != null ? " " + p.heroMetric().unit() : "")
                            + " [" + p.heroMetric().flag() + "]"
                            : "";
                    return "- " + name + ": " + score + outdated + date + hero;
                })
                .collect(Collectors.joining("\n"));
    }

    private String buildPillarContext(HealthPillarDetailResponse detail) {
        var sb = new StringBuilder();
        var name = PILLAR_NAMES_PL.getOrDefault(detail.pillarCode(), detail.pillarCode());
        sb.append("Filar: ").append(name).append("\n");
        sb.append("Wynik: ").append(detail.score() != null ? detail.score() + "/100" : "brak danych").append("\n");
        sb.append("Nieaktualne: ").append(detail.isOutdated() ? "tak" : "nie").append("\n");

        if (detail.heroMetric() != null) {
            var h = detail.heroMetric();
            sb.append("Główny wskaźnik: ").append(h.markerNamePl())
                    .append(": ").append(h.valueNumeric())
                    .append(h.unit() != null ? " " + h.unit() : "")
                    .append(" [").append(h.flag()).append("]\n");
        }

        if (detail.sections() != null) {
            detail.sections().forEach(section -> {
                sb.append("\nSekcja: ").append(section.sectionNamePl())
                        .append(" (wynik: ").append(section.score() != null ? section.score() : "brak").append(")\n");
                section.markers().forEach(m -> appendMarker(sb, m));
            });
        }
        return sb.toString();
    }

    private void appendMarker(StringBuilder sb, HealthPillarMarkerResult m) {
        sb.append("  - ").append(m.markerNamePl()).append(": ");
        if (m.valueNumeric() != null) {
            sb.append(m.valueNumeric());
            if (m.unit() != null) sb.append(" ").append(m.unit());
        } else if (m.valueText() != null) {
            sb.append(m.valueText());
        } else {
            sb.append("brak wartości");
        }
        sb.append(" [").append(m.flag()).append("]");
        if (m.refRangeLow() != null || m.refRangeHigh() != null) {
            sb.append(" norma: ");
            if (m.refRangeLow() != null) sb.append(m.refRangeLow());
            sb.append("-");
            if (m.refRangeHigh() != null) sb.append(m.refRangeHigh());
        }
        if (m.date() != null) sb.append(", data: ").append(m.date());
        sb.append("\n");
    }

    private String callAi(String systemPrompt, String userMessage, String metricType) {
        var sample = aiMetrics.startTimer();
        var effectiveMessage = userMessage;
        if (effectiveMessage.length() > MAX_AI_INPUT_LENGTH) {
            log.warn("AI input truncated from {} to {} chars", effectiveMessage.length(), MAX_AI_INPUT_LENGTH);
            aiMetrics.recordSummaryInputTruncated();
            effectiveMessage = effectiveMessage.substring(0, MAX_AI_INPUT_LENGTH) + "\n\n[Data truncated]";
        }

        try {
            ChatResponse chatResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(effectiveMessage)
                    .call()
                    .chatResponse();

            String content = chatResponse.getResult() != null
                    ? chatResponse.getResult().getOutput().getText()
                    : null;

            if (content == null || content.isBlank()) {
                throw new AiPillarInsightGenerationException("AI returned empty insight");
            }

            Long promptTokens = null;
            Long completionTokens = null;
            if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : null;
                completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : null;
            }

            aiMetrics.recordSummaryTokens(metricType, promptTokens, completionTokens);
            aiMetrics.recordSummaryRequest(metricType, sample, "success", false);
            log.info("AI pillar insight generated: {} chars for type {}", content.length(), metricType);
            return content;

        } catch (AiPillarInsightGenerationException e) {
            aiMetrics.recordSummaryRequest(metricType, sample, "error", false);
            aiMetrics.recordAiError(metricType, "empty_response");
            throw e;
        } catch (Exception e) {
            aiMetrics.recordSummaryRequest(metricType, sample, "error", false);
            aiMetrics.recordAiError(metricType, "api_error");
            log.error("Failed to generate AI pillar insight for type {}", metricType, e);
            throw new AiPillarInsightGenerationException("Failed to generate AI pillar insight", e);
        }
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) return "***";
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
