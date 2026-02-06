package com.healthassistant.dailysummary;

import tools.jackson.databind.ObjectMapper;
import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.AiHealthReportResponse;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.dailysummary.api.dto.DailySummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
class DailySummaryService implements DailySummaryFacade {

    private final GenerateDailySummaryCommandHandler commandHandler;
    private final DailySummaryJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;
    private final Optional<AiHealthReportService> aiHealthReportService;

    @Override
    public void generateDailySummary(String deviceId, LocalDate date) {
        commandHandler.handle(GenerateDailySummaryCommand.forDeviceAndDate(deviceId, date));
    }

    @Override
    public Optional<DailySummary> getDailySummary(String deviceId, LocalDate date) {
        return jpaRepository.findByDeviceIdAndDate(deviceId, date)
                .map(entity -> objectMapper.convertValue(entity.getSummary(), DailySummary.class));
    }

    @Override
    public DailySummaryRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<DailySummaryJpaEntity> entities = jpaRepository.findByDeviceIdAndDateBetweenOrderByDateAsc(deviceId, startDate, endDate);

        List<DailySummary> summaries = entities.stream()
                .map(entity -> objectMapper.convertValue(entity.getSummary(), DailySummary.class))
                .toList();

        List<DailySummaryResponse> dailyStats = summaries.stream()
                .map(DailySummaryMapper.INSTANCE::toResponse)
                .toList();

        RangeSummaryAccumulator accumulated = summaries.stream()
                .reduce(RangeSummaryAccumulator.empty(), RangeSummaryAccumulator::aggregate, RangeSummaryAccumulator::merge);

        return accumulated.buildResponse(startDate, endDate, dailyStats);
    }

    private record RangeSummaryAccumulator(
            int daysWithData,
            int totalSteps,
            int totalActiveMinutes,
            int totalActiveCalories,
            long totalDistanceMeters,
            int daysWithSteps,
            int totalSleepMinutes,
            int daysWithSleep,
            int sumRestingBpm,
            int daysWithRestingBpm,
            int sumAvgBpm,
            int daysWithAvgBpm,
            Integer maxBpmOverall,
            int daysWithHeartData,
            int totalCalories,
            int totalProtein,
            int totalFat,
            int totalCarbs,
            int totalMeals,
            int daysWithNutrition,
            int totalWorkouts,
            int daysWithWorkouts,
            List<DailySummaryRangeSummaryResponse.WorkoutSummary.WorkoutInfo> workoutList
    ) {
        static RangeSummaryAccumulator empty() {
            return new RangeSummaryAccumulator(
                    0, 0, 0, 0, 0L, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()
            );
        }

        RangeSummaryAccumulator aggregate(DailySummary s) {
            Integer steps = s.activity().steps();
            Integer activeMinutes = s.activity().activeMinutes();
            Integer activeCalories = s.activity().activeCalories();
            Long distance = s.activity().distanceMeters();

            int sleepMins = s.sleep().stream()
                    .map(DailySummary.Sleep::totalMinutes)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            Integer restingBpm = s.heart().restingBpm();
            Integer avgBpm = s.heart().avgBpm();
            Integer maxBpm = s.heart().maxBpm();

            Integer cal = s.nutrition().totalCalories();
            Integer prot = s.nutrition().totalProtein();
            Integer fat = s.nutrition().totalFat();
            Integer carbs = s.nutrition().totalCarbs();
            Integer meals = s.nutrition().mealCount();

            List<DailySummaryRangeSummaryResponse.WorkoutSummary.WorkoutInfo> newWorkouts = s.workouts().isEmpty()
                    ? workoutList
                    : Stream.concat(
                            workoutList.stream(),
                            s.workouts().stream().map(w -> new DailySummaryRangeSummaryResponse.WorkoutSummary.WorkoutInfo(
                                    w.workoutId(), w.note(), s.date()))
                    ).toList();

            Integer newMaxBpm = maxBpm != null && (maxBpmOverall == null || maxBpm > maxBpmOverall)
                    ? maxBpm : maxBpmOverall;

            return new RangeSummaryAccumulator(
                    daysWithData + 1,
                    totalSteps + orZero(steps),
                    totalActiveMinutes + orZero(activeMinutes),
                    totalActiveCalories + orZero(activeCalories),
                    totalDistanceMeters + orZeroLong(distance),
                    daysWithSteps + (steps != null && steps > 0 ? 1 : 0),
                    totalSleepMinutes + sleepMins,
                    daysWithSleep + (!s.sleep().isEmpty() ? 1 : 0),
                    sumRestingBpm + orZero(restingBpm),
                    daysWithRestingBpm + (restingBpm != null ? 1 : 0),
                    sumAvgBpm + orZero(avgBpm),
                    daysWithAvgBpm + (avgBpm != null ? 1 : 0),
                    newMaxBpm,
                    daysWithHeartData + (restingBpm != null || avgBpm != null || maxBpm != null ? 1 : 0),
                    totalCalories + orZero(cal),
                    totalProtein + orZero(prot),
                    totalFat + orZero(fat),
                    totalCarbs + orZero(carbs),
                    totalMeals + orZero(meals),
                    daysWithNutrition + (meals != null && meals > 0 ? 1 : 0),
                    totalWorkouts + s.workouts().size(),
                    daysWithWorkouts + (!s.workouts().isEmpty() ? 1 : 0),
                    newWorkouts
            );
        }

        RangeSummaryAccumulator merge(RangeSummaryAccumulator other) {
            Integer mergedMax = Optional.ofNullable(maxBpmOverall)
                    .map(m -> Optional.ofNullable(other.maxBpmOverall).map(o -> Math.max(m, o)).orElse(m))
                    .orElse(other.maxBpmOverall);

            return new RangeSummaryAccumulator(
                    daysWithData + other.daysWithData,
                    totalSteps + other.totalSteps,
                    totalActiveMinutes + other.totalActiveMinutes,
                    totalActiveCalories + other.totalActiveCalories,
                    totalDistanceMeters + other.totalDistanceMeters,
                    daysWithSteps + other.daysWithSteps,
                    totalSleepMinutes + other.totalSleepMinutes,
                    daysWithSleep + other.daysWithSleep,
                    sumRestingBpm + other.sumRestingBpm,
                    daysWithRestingBpm + other.daysWithRestingBpm,
                    sumAvgBpm + other.sumAvgBpm,
                    daysWithAvgBpm + other.daysWithAvgBpm,
                    mergedMax,
                    daysWithHeartData + other.daysWithHeartData,
                    totalCalories + other.totalCalories,
                    totalProtein + other.totalProtein,
                    totalFat + other.totalFat,
                    totalCarbs + other.totalCarbs,
                    totalMeals + other.totalMeals,
                    daysWithNutrition + other.daysWithNutrition,
                    totalWorkouts + other.totalWorkouts,
                    daysWithWorkouts + other.daysWithWorkouts,
                    Stream.concat(workoutList.stream(), other.workoutList.stream()).toList()
            );
        }

        DailySummaryRangeSummaryResponse buildResponse(LocalDate startDate, LocalDate endDate,
                                                        List<DailySummaryResponse> dailyStats) {
            var activity = new DailySummaryRangeSummaryResponse.ActivitySummary(
                    totalSteps,
                    safeDivide(totalSteps, daysWithSteps),
                    totalActiveMinutes,
                    safeDivide(totalActiveMinutes, daysWithData),
                    totalActiveCalories,
                    safeDivide(totalActiveCalories, daysWithData),
                    totalDistanceMeters,
                    safeDivideLong(totalDistanceMeters, daysWithData));

            var sleep = new DailySummaryRangeSummaryResponse.SleepSummary(
                    totalSleepMinutes,
                    safeDivide(totalSleepMinutes, daysWithSleep),
                    daysWithSleep);

            var heart = new DailySummaryRangeSummaryResponse.HeartSummary(
                    daysWithRestingBpm > 0 ? sumRestingBpm / daysWithRestingBpm : null,
                    daysWithAvgBpm > 0 ? sumAvgBpm / daysWithAvgBpm : null,
                    maxBpmOverall,
                    daysWithHeartData);

            var nutrition = new DailySummaryRangeSummaryResponse.NutritionSummary(
                    totalCalories,
                    safeDivide(totalCalories, daysWithNutrition),
                    totalProtein,
                    safeDivide(totalProtein, daysWithNutrition),
                    totalFat,
                    safeDivide(totalFat, daysWithNutrition),
                    totalCarbs,
                    safeDivide(totalCarbs, daysWithNutrition),
                    totalMeals,
                    safeDivide(totalMeals, daysWithNutrition),
                    daysWithNutrition);

            var workouts = new DailySummaryRangeSummaryResponse.WorkoutSummary(
                    totalWorkouts, daysWithWorkouts, workoutList);

            return new DailySummaryRangeSummaryResponse(
                    startDate, endDate, daysWithData, activity, sleep, heart, nutrition, workouts, dailyStats);
        }

        private static int safeDivide(int numerator, int denominator) {
            return denominator > 0 ? numerator / denominator : 0;
        }

        private static long safeDivideLong(long numerator, int denominator) {
            return denominator > 0 ? numerator / denominator : 0L;
        }

        private static int orZero(Integer value) {
            return value != null ? value : 0;
        }

        private static long orZeroLong(Long value) {
            return value != null ? value : 0L;
        }
    }

    @Override
    public AiHealthReportResponse generateDailyReport(String deviceId, LocalDate date) {
        if (aiHealthReportService.isEmpty()) {
            throw new AiSummaryGenerationException("AI service is not available");
        }
        return aiHealthReportService.get().generateDailyReport(deviceId, date);
    }

    @Override
    public AiHealthReportResponse generateRangeReport(String deviceId, LocalDate startDate, LocalDate endDate) {
        if (aiHealthReportService.isEmpty()) {
            throw new AiSummaryGenerationException("AI service is not available");
        }
        return aiHealthReportService.get().generateRangeReport(deviceId, startDate, endDate);
    }

    @Override
    @Transactional
    public void deleteSummaryForDate(String deviceId, LocalDate date) {
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId must not be blank");
        }
        // TODO: Add authorization check - verify caller is allowed to delete data for this deviceId
        log.debug("Deleting daily summary for device {} date {}", maskDeviceId(deviceId), date);
        jpaRepository.deleteByDeviceIdAndDate(deviceId, date);
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
