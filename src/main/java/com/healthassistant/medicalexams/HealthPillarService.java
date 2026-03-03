package com.healthassistant.medicalexams;

import com.healthassistant.medicalexams.HealthPillarDefinitions.PillarDefinition;
import com.healthassistant.medicalexams.HealthPillarDefinitions.SectionDefinition;
import com.healthassistant.medicalexams.api.dto.HealthPillarDetailResponse;
import com.healthassistant.medicalexams.api.dto.HealthPillarHeroMetric;
import com.healthassistant.medicalexams.api.dto.HealthPillarMarkerResult;
import com.healthassistant.medicalexams.api.dto.HealthPillarSectionResponse;
import com.healthassistant.medicalexams.api.dto.HealthPillarSummaryResponse;
import com.healthassistant.medicalexams.api.dto.HealthPillarsDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
class HealthPillarService {

    private static final String HOMA_IR_CODE = "HOMA_IR";
    private static final String HOMA_IR_NAME_PL = "HOMA-IR";
    private static final BigDecimal HOMA_IR_THRESHOLD = new BigDecimal("2.0");
    private static final BigDecimal HOMA_IR_DIVISOR = new BigDecimal("405");

    private final LabResultRepository labResultRepository;
    private final MarkerDefinitionRepository markerDefinitionRepository;
    private final Optional<HealthPillarAiService> healthPillarAiService;

    @Transactional(readOnly = true)
    HealthPillarsDashboardResponse getHealthPillars(String deviceId) {
        var allCodes = HealthPillarDefinitions.allMarkerCodes();
        var latestResults = fetchLatestResults(deviceId, allCodes);
        var markerNames = fetchMarkerNames(allCodes);

        var summaries = HealthPillarDefinitions.PILLARS.stream()
                .map(pillar -> buildSummary(pillar, latestResults, markerNames))
                .toList();

        var overallInsight = safeGetOverallInsight(deviceId, summaries);
        return new HealthPillarsDashboardResponse(overallInsight, summaries);
    }

    @Transactional(readOnly = true)
    HealthPillarDetailResponse getHealthPillarDetail(String deviceId, String pillarCode) {
        var pillar = HealthPillarDefinitions.findPillar(pillarCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown pillar code"));

        var pillarCodes = HealthPillarDefinitions.pillarMarkerCodes(pillar);
        var latestResults = fetchLatestResults(deviceId, pillarCodes);
        var markerNames = fetchMarkerNames(pillarCodes);

        var detail = buildDetail(pillar, latestResults, markerNames);
        var insight = safeGetPillarInsight(deviceId, pillarCode, detail);
        return new HealthPillarDetailResponse(
                detail.pillarCode(), detail.pillarNamePl(), detail.score(), detail.isOutdated(),
                detail.latestDataDate(), detail.heroMetric(), detail.sections(), insight);
    }

    private Map<String, LabResult> fetchLatestResults(String deviceId, List<String> codes) {
        return labResultRepository.findLatestResultsByMarkerCodes(deviceId, codes)
                .stream()
                .collect(Collectors.toMap(LabResult::getMarkerCode, r -> r, (first, second) -> first));
    }

    private Map<String, MarkerDefinition> fetchMarkerNames(List<String> codes) {
        return markerDefinitionRepository.findAllByCodeIn(codes).stream()
                .collect(Collectors.toMap(MarkerDefinition::getCode, md -> md, (a, b) -> a));
    }

    private HealthPillarSummaryResponse buildSummary(PillarDefinition pillar,
                                                       Map<String, LabResult> results,
                                                       Map<String, MarkerDefinition> names) {
        var data = buildPillarData(pillar, results, names);
        return new HealthPillarSummaryResponse(
                pillar.code(), pillar.namePl(), data.score(), data.isOutdated(),
                data.latestDataDate(), data.heroMetric(), null);
    }

    private HealthPillarDetailResponse buildDetail(PillarDefinition pillar,
                                                    Map<String, LabResult> results,
                                                    Map<String, MarkerDefinition> names) {
        var data = buildPillarData(pillar, results, names);
        return new HealthPillarDetailResponse(
                pillar.code(), pillar.namePl(), data.score(), data.isOutdated(),
                data.latestDataDate(), data.heroMetric(), data.sections(), null);
    }

    private PillarData buildPillarData(PillarDefinition pillar,
                                        Map<String, LabResult> results,
                                        Map<String, MarkerDefinition> names) {
        var latestDate = latestDataDate(pillar, results);
        var outdated = isOutdated(latestDate, pillar.ttlMonths());
        var sections = buildSections(pillar, results, names);
        var score = pillarScore(sections);
        var hero = buildHeroMetric(pillar.heroMarkerCode(), results, names);
        return new PillarData(score, outdated, latestDate, hero, sections);
    }

    private List<HealthPillarSectionResponse> buildSections(PillarDefinition pillar,
                                                              Map<String, LabResult> results,
                                                              Map<String, MarkerDefinition> names) {
        return pillar.sections().stream()
                .map(section -> buildSection(section, results, names, pillar.code()))
                .filter(s -> !s.markers().isEmpty())
                .toList();
    }

    private HealthPillarSectionResponse buildSection(SectionDefinition section,
                                                       Map<String, LabResult> results,
                                                       Map<String, MarkerDefinition> names,
                                                       String pillarCode) {
        var baseMarkers = section.markerCodes().stream()
                .filter(results::containsKey)
                .map(code -> buildMarkerResult(code, results.get(code), names))
                .toList();

        var markers = "METABOLISM".equals(pillarCode) && "GLUCOSE_METABOLISM".equals(section.code())
                ? injectHomaIr(baseMarkers, results)
                : baseMarkers;

        var sectionScore = computeAvgScore(markers.stream()
                .map(HealthPillarMarkerResult::score)
                .toList());

        return new HealthPillarSectionResponse(section.code(), section.namePl(), sectionScore, markers);
    }

    private HealthPillarMarkerResult buildMarkerResult(String code, LabResult result,
                                                        Map<String, MarkerDefinition> names) {
        var def = names.get(code);
        var namePl = def != null ? def.getNamePl() : code;
        var score = flagToScore(result.getFlag());
        var refLow = result.getRefRangeLow() != null ? result.getRefRangeLow()
                : (def != null ? def.getRefRangeLowDefault() : null);
        var refHigh = result.getRefRangeHigh() != null ? result.getRefRangeHigh()
                : (def != null ? def.getRefRangeHighDefault() : null);
        return new HealthPillarMarkerResult(
                code, namePl, result.getValueNumeric(), result.getUnit(), result.getValueText(),
                result.getFlag(), score, result.getDate(), refLow, refHigh);
    }

    private List<HealthPillarMarkerResult> injectHomaIr(List<HealthPillarMarkerResult> existing,
                                                          Map<String, LabResult> results) {
        var glu = results.get("GLU");
        var insulin = results.get("INSULIN");
        if (glu == null || insulin == null
                || glu.getValueNumeric() == null || insulin.getValueNumeric() == null) {
            return existing;
        }
        // HOMA-IR formula (GLU × INSULIN) / 405 requires GLU in mg/dL; skip if incompatible unit
        var gluUnit = glu.getUnit();
        if (gluUnit != null && !gluUnit.isBlank() && !"mg/dL".equalsIgnoreCase(gluUnit)) {
            return existing;
        }

        var homaIr = glu.getValueNumeric().multiply(insulin.getValueNumeric())
                .divide(HOMA_IR_DIVISOR, 4, RoundingMode.HALF_UP);
        var flag = homaIr.compareTo(HOMA_IR_THRESHOLD) >= 0 ? "HIGH" : "NORMAL";
        var score = flagToScore(flag);
        var date = Stream.of(glu.getDate(), insulin.getDate())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        var homaMarker = new HealthPillarMarkerResult(
                HOMA_IR_CODE, HOMA_IR_NAME_PL, homaIr, null, null, flag, score, date, null, HOMA_IR_THRESHOLD);

        var withHoma = new ArrayList<>(existing);
        withHoma.add(homaMarker);
        return List.copyOf(withHoma);
    }

    private HealthPillarHeroMetric buildHeroMetric(String heroCode, Map<String, LabResult> results,
                                                    Map<String, MarkerDefinition> names) {
        return Optional.ofNullable(results.get(heroCode))
                .map(r -> {
                    var def = names.get(heroCode);
                    var namePl = def != null ? def.getNamePl() : heroCode;
                    return new HealthPillarHeroMetric(heroCode, namePl, r.getValueNumeric(),
                            r.getUnit(), r.getFlag(), r.getDate());
                })
                .orElse(null);
    }

    private LocalDate latestDataDate(PillarDefinition pillar, Map<String, LabResult> results) {
        return pillar.sections().stream()
                .flatMap(s -> s.markerCodes().stream())
                .filter(results::containsKey)
                .map(code -> results.get(code).getDate())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean isOutdated(LocalDate latestDate, int ttlMonths) {
        if (latestDate == null) return true;
        return latestDate.isBefore(LocalDate.now().minusMonths(ttlMonths));
    }

    private Integer pillarScore(List<HealthPillarSectionResponse> sections) {
        return computeAvgScore(sections.stream()
                .map(HealthPillarSectionResponse::score)
                .toList());
    }

    private Integer computeAvgScore(List<Integer> scores) {
        var nonNull = scores.stream().filter(Objects::nonNull).toList();
        if (nonNull.isEmpty()) return null;
        var sum = nonNull.stream().mapToInt(Integer::intValue).sum();
        return (int) Math.round((double) sum / nonNull.size());
    }

    private Integer flagToScore(String flag) {
        return switch (flag) {
            case "NORMAL" -> 100;
            case "WARNING" -> 75;
            case "HIGH", "LOW" -> 50;
            default -> null;
        };
    }

    private String safeGetOverallInsight(String deviceId, List<HealthPillarSummaryResponse> summaries) {
        return healthPillarAiService.map(svc -> {
            try {
                return svc.getOrGenerateOverallInsight(deviceId, summaries);
            } catch (Exception e) {
                log.warn("Failed to generate overall AI insight for device {}, returning null", maskDeviceId(deviceId), e);
                return null;
            }
        }).orElse(null);
    }

    private String safeGetPillarInsight(String deviceId, String pillarCode, HealthPillarDetailResponse detail) {
        return healthPillarAiService.map(svc -> {
            try {
                return svc.getOrGeneratePillarInsight(deviceId, pillarCode, detail);
            } catch (Exception e) {
                log.warn("Failed to generate AI insight for pillar {} device {}, returning null",
                        pillarCode, maskDeviceId(deviceId), e);
                return null;
            }
        }).orElse(null);
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) return "***";
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }

    private record PillarData(
            Integer score,
            boolean isOutdated,
            LocalDate latestDataDate,
            HealthPillarHeroMetric heroMetric,
            List<HealthPillarSectionResponse> sections
    ) {}
}
