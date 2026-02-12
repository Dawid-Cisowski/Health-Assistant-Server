package com.healthassistant.reports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.dailysummary.api.dto.DailySummaryResponse;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.EnergyRequirementsResponse;
import com.healthassistant.reports.api.dto.GoalEvaluation;
import com.healthassistant.reports.api.dto.GoalResult;
import com.healthassistant.reports.api.dto.PeriodComparison;
import com.healthassistant.reports.api.dto.ReportType;
import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.PersonalRecordsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.healthassistant.config.SecurityUtils.maskDeviceId;

@Service
@RequiredArgsConstructor
@Slf4j
class ReportGenerationService {

    private final DailySummaryFacade dailySummaryFacade;
    private final MealsFacade mealsFacade;
    private final WorkoutFacade workoutFacade;
    private final GoalsEvaluator goalsEvaluator;
    private final PeriodComparisonCalculator comparisonCalculator;
    private final ReportDataSnapshotBuilder snapshotBuilder;
    private final Optional<ReportAiService> reportAiService;
    private final HealthReportJpaRepository reportRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    Optional<Long> generateDailyReport(String deviceId, LocalDate date) {
        log.info("Generating daily report for device {} date: {}", maskDeviceId(deviceId), date);

        Optional<DailySummary> summaryOpt = dailySummaryFacade.getDailySummary(deviceId, date);
        if (summaryOpt.isEmpty()) {
            log.info("No daily summary for device {} date: {}", maskDeviceId(deviceId), date);
            return Optional.empty();
        }
        DailySummary summary = summaryOpt.get();

        Optional<EnergyRequirementsResponse> energyReq = safeGetEnergyRequirements(deviceId, date);
        GoalEvaluation goals = goalsEvaluator.evaluateDaily(summary, energyReq);

        Optional<DailySummary> prevSummaryOpt = dailySummaryFacade.getDailySummary(deviceId, date.minusDays(1));
        PeriodComparison comparison = prevSummaryOpt
                .map(prev -> comparisonCalculator.compareDaily(summary, prev, date.minusDays(1)))
                .orElse(null);

        var dataSnapshot = snapshotBuilder.buildDaily(summary, energyReq);

        Optional<String> aiSummary = reportAiService
                .flatMap(ai -> ai.generateDailyAiSummary(summary, goals, comparison));

        String shortSummary = buildShortSummary(goals);

        return saveReport(deviceId, ReportType.DAILY, date, date,
                aiSummary.orElse(null), shortSummary, goals, comparison, toMap(dataSnapshot));
    }

    @Transactional
    Optional<Long> generateWeeklyReport(String deviceId, LocalDate weekStart, LocalDate weekEnd) {
        log.info("Generating weekly report for device {} period: {} to {}", maskDeviceId(deviceId), weekStart, weekEnd);

        DailySummaryRangeSummaryResponse range = dailySummaryFacade.getRangeSummary(deviceId, weekStart, weekEnd);
        if (range.daysWithData() == null || range.daysWithData() == 0) {
            log.info("No data for device {} week: {} to {}", maskDeviceId(deviceId), weekStart, weekEnd);
            return Optional.empty();
        }

        List<GoalsEvaluator.DayNutritionCheck> dailyChecks = buildDailyNutritionChecks(deviceId, weekStart, weekEnd, range);
        GoalEvaluation goals = goalsEvaluator.evaluateWeekly(range, dailyChecks);

        LocalDate prevStart = weekStart.minusWeeks(1);
        LocalDate prevEnd = weekEnd.minusWeeks(1);
        DailySummaryRangeSummaryResponse prevRange = dailySummaryFacade.getRangeSummary(deviceId, prevStart, prevEnd);
        PeriodComparison comparison = comparisonCalculator.compareRange(range, prevRange, prevStart, prevEnd);

        var rangeSnapshot = snapshotBuilder.buildRange(range);

        Optional<String> aiSummary = reportAiService
                .flatMap(ai -> ai.generateRangeAiSummary(range, goals, comparison, ReportType.WEEKLY));

        String shortSummary = buildShortSummary(goals);

        return saveReport(deviceId, ReportType.WEEKLY, weekStart, weekEnd,
                aiSummary.orElse(null), shortSummary, goals, comparison, toMap(rangeSnapshot));
    }

    @Transactional
    Optional<Long> generateMonthlyReport(String deviceId, LocalDate monthStart, LocalDate monthEnd) {
        log.info("Generating monthly report for device {} period: {} to {}", maskDeviceId(deviceId), monthStart, monthEnd);

        DailySummaryRangeSummaryResponse range = dailySummaryFacade.getRangeSummary(deviceId, monthStart, monthEnd);
        if (range.daysWithData() == null || range.daysWithData() == 0) {
            log.info("No data for device {} month: {} to {}", maskDeviceId(deviceId), monthStart, monthEnd);
            return Optional.empty();
        }

        List<GoalsEvaluator.DayNutritionCheck> dailyChecks = buildDailyNutritionChecks(deviceId, monthStart, monthEnd, range);

        int newPrsCount = countNewPersonalRecords(deviceId, monthStart, monthEnd);

        GoalEvaluation goals = goalsEvaluator.evaluateMonthly(range, dailyChecks, newPrsCount);

        LocalDate prevStart = monthStart.minusMonths(1);
        LocalDate prevEnd = prevStart.withDayOfMonth(prevStart.lengthOfMonth());
        DailySummaryRangeSummaryResponse prevRange = dailySummaryFacade.getRangeSummary(deviceId, prevStart, prevEnd);
        PeriodComparison comparison = comparisonCalculator.compareRange(range, prevRange, prevStart, prevEnd);

        var rangeSnapshot = snapshotBuilder.buildRange(range);

        Optional<String> aiSummary = reportAiService
                .flatMap(ai -> ai.generateRangeAiSummary(range, goals, comparison, ReportType.MONTHLY));

        String shortSummary = buildShortSummary(goals);

        return saveReport(deviceId, ReportType.MONTHLY, monthStart, monthEnd,
                aiSummary.orElse(null), shortSummary, goals, comparison, toMap(rangeSnapshot));
    }

    private List<GoalsEvaluator.DayNutritionCheck> buildDailyNutritionChecks(
            String deviceId, LocalDate start, LocalDate end,
            DailySummaryRangeSummaryResponse range) {
        // Pre-build consumed lookup from range summary (already fetched) to avoid repeated list scans
        Map<LocalDate, DailySummaryResponse> dailyStatsIndex = range.dailyStats() != null
                ? range.dailyStats().stream().collect(Collectors.toMap(DailySummaryResponse::date, d -> d, (a, b) -> a))
                : Map.of();

        // N.B. getEnergyRequirements is called per-day because targets differ based on daily steps/training.
        // This runs in background scheduler so latency is acceptable.
        return start.datesUntil(end.plusDays(1))
                .map(date -> {
                    Optional<EnergyRequirementsResponse> req = safeGetEnergyRequirements(deviceId, date);
                    int targetCal = req.map(EnergyRequirementsResponse::targetCaloriesKcal).orElse(0);
                    int targetProtein = req
                            .filter(r -> r.macroTargets() != null && r.macroTargets().proteinGrams() != null)
                            .map(r -> r.macroTargets().proteinGrams())
                            .orElse(0);

                    DailySummaryResponse dayStat = dailyStatsIndex.get(date);
                    int consumedCal = dayStat != null && dayStat.nutrition() != null && dayStat.nutrition().totalCalories() != null
                            ? dayStat.nutrition().totalCalories() : 0;
                    int consumedProtein = dayStat != null && dayStat.nutrition() != null && dayStat.nutrition().totalProtein() != null
                            ? dayStat.nutrition().totalProtein() : 0;

                    return new GoalsEvaluator.DayNutritionCheck(date, targetCal, consumedCal, targetProtein, consumedProtein);
                })
                .toList();
    }

    private int countNewPersonalRecords(String deviceId, LocalDate monthStart, LocalDate monthEnd) {
        try {
            PersonalRecordsResponse prs = workoutFacade.getAllPersonalRecords(deviceId);
            if (prs.personalRecords() == null) return 0;
            return (int) prs.personalRecords().stream()
                    .filter(pr -> pr.prDate() != null)
                    .filter(pr -> !pr.prDate().isBefore(monthStart) && !pr.prDate().isAfter(monthEnd))
                    .count();
        } catch (Exception e) {
            log.error("Failed to count personal records for device {} (month: {} to {}): {}",
                    maskDeviceId(deviceId), monthStart, monthEnd, e.getMessage());
            return 0;
        }
    }

    private Optional<EnergyRequirementsResponse> safeGetEnergyRequirements(String deviceId, LocalDate date) {
        try {
            return mealsFacade.getEnergyRequirements(deviceId, date);
        } catch (Exception e) {
            log.debug("Energy requirements unavailable for device {} date {}: {}",
                    maskDeviceId(deviceId), date, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Long> saveReport(String deviceId, ReportType type, LocalDate periodStart, LocalDate periodEnd,
                                      String aiSummary, String shortSummary,
                                      GoalEvaluation goals, PeriodComparison comparison,
                                      Map<String, Object> dataMap) {
        HealthReportJpaEntity entity = reportRepository
                .findByDeviceIdAndReportTypeAndPeriodStartAndPeriodEnd(deviceId, type.name(), periodStart, periodEnd)
                .orElseGet(() -> HealthReportJpaEntity.create(deviceId, type.name(), periodStart, periodEnd));

        entity.populateReport(
                aiSummary, shortSummary,
                toMap(goals), goals.achieved(), goals.total(),
                comparison != null ? toMap(comparison) : null,
                dataMap
        );

        HealthReportJpaEntity saved = reportRepository.save(entity);
        log.info("Report saved: id={}, type={}, period={} to {}", saved.getId(), type, periodStart, periodEnd);
        return Optional.of(saved.getId());
    }

    private String buildShortSummary(GoalEvaluation goals) {
        String achievedNames = goals.details().stream()
                .filter(GoalResult::achieved)
                .map(GoalResult::displayName)
                .collect(Collectors.joining(", "));
        return goals.achieved() + "/" + goals.total() + " celow"
                + (achievedNames.isEmpty() ? "" : ": " + achievedNames);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }
}
