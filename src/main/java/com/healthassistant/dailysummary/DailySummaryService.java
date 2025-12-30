package com.healthassistant.dailysummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.DailySummary;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.dailysummary.api.dto.DailySummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
class DailySummaryService implements DailySummaryFacade {

    private final GenerateDailySummaryCommandHandler commandHandler;
    private final DailySummaryJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

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

        RangeSummaryAggregator agg = new RangeSummaryAggregator();
        List<DailySummaryResponse> dailyStats = new ArrayList<>(entities.size());

        entities.stream()
                .map(entity -> objectMapper.convertValue(entity.getSummary(), DailySummary.class))
                .forEach(s -> {
                    agg.aggregate(s);
                    dailyStats.add(DailySummaryMapper.INSTANCE.toResponse(s));
        });

        return agg.buildResponse(startDate, endDate, dailyStats);
    }

    private static class RangeSummaryAggregator {
        int daysWithData;
        int totalSteps;
        int totalActiveMinutes;
        int totalActiveCalories;
        long totalDistanceMeters;
        int daysWithSteps;

        int totalSleepMinutes;
        int daysWithSleep;

        int sumRestingBpm;
        int daysWithRestingBpm;
        int sumAvgBpm;
        int daysWithAvgBpm;
        Integer maxBpmOverall;
        int daysWithHeartData;

        int totalCalories;
        int totalProtein;
        int totalFat;
        int totalCarbs;
        int totalMeals;
        int daysWithNutrition;

        int totalWorkouts;
        int daysWithWorkouts;
        List<DailySummaryRangeSummaryResponse.WorkoutSummary.WorkoutInfo> workoutList = new ArrayList<>();

        void aggregate(DailySummary s) {
            daysWithData++;

            Integer steps = s.activity().steps();
            if (steps != null) {
                totalSteps += steps;
                if (steps > 0) daysWithSteps++;
            }
            Integer activeMinutes = s.activity().activeMinutes();
            if (activeMinutes != null) totalActiveMinutes += activeMinutes;
            Integer activeCalories = s.activity().activeCalories();
            if (activeCalories != null) totalActiveCalories += activeCalories;
            Long distance = s.activity().distanceMeters();
            if (distance != null) totalDistanceMeters += distance;

            if (!s.sleep().isEmpty()) {
                daysWithSleep++;
                for (DailySummary.Sleep sleep : s.sleep()) {
                    if (sleep.totalMinutes() != null) totalSleepMinutes += sleep.totalMinutes();
                }
            }

            Integer restingBpm = s.heart().restingBpm();
            Integer avgBpm = s.heart().avgBpm();
            Integer maxBpm = s.heart().maxBpm();
            if (restingBpm != null) {
                sumRestingBpm += restingBpm;
                daysWithRestingBpm++;
            }
            if (avgBpm != null) {
                sumAvgBpm += avgBpm;
                daysWithAvgBpm++;
            }
            if (maxBpm != null && (maxBpmOverall == null || maxBpm > maxBpmOverall)) {
                maxBpmOverall = maxBpm;
            }
            if (restingBpm != null || avgBpm != null || maxBpm != null) {
                daysWithHeartData++;
            }

            Integer cal = s.nutrition().totalCalories();
            Integer prot = s.nutrition().totalProtein();
            Integer fat = s.nutrition().totalFat();
            Integer carbs = s.nutrition().totalCarbs();
            Integer meals = s.nutrition().mealCount();
            if (cal != null) totalCalories += cal;
            if (prot != null) totalProtein += prot;
            if (fat != null) totalFat += fat;
            if (carbs != null) totalCarbs += carbs;
            if (meals != null) {
                totalMeals += meals;
                if (meals > 0) daysWithNutrition++;
            }

            if (!s.workouts().isEmpty()) {
                daysWithWorkouts++;
                totalWorkouts += s.workouts().size();
                for (DailySummary.Workout w : s.workouts()) {
                    workoutList.add(new DailySummaryRangeSummaryResponse.WorkoutSummary.WorkoutInfo(
                            w.workoutId(), w.note(), s.date()));
                }
            }
        }

        DailySummaryRangeSummaryResponse buildResponse(LocalDate startDate, LocalDate endDate,
                                                        List<DailySummaryResponse> dailyStats) {
            var activity = new DailySummaryRangeSummaryResponse.ActivitySummary(
                    totalSteps,
                    daysWithSteps > 0 ? totalSteps / daysWithSteps : 0,
                    totalActiveMinutes,
                    daysWithData > 0 ? totalActiveMinutes / daysWithData : 0,
                    totalActiveCalories,
                    daysWithData > 0 ? totalActiveCalories / daysWithData : 0,
                    totalDistanceMeters,
                    daysWithData > 0 ? totalDistanceMeters / daysWithData : 0L);

            var sleep = new DailySummaryRangeSummaryResponse.SleepSummary(
                    totalSleepMinutes,
                    daysWithSleep > 0 ? totalSleepMinutes / daysWithSleep : 0,
                    daysWithSleep);

            var heart = new DailySummaryRangeSummaryResponse.HeartSummary(
                    daysWithRestingBpm > 0 ? sumRestingBpm / daysWithRestingBpm : null,
                    daysWithAvgBpm > 0 ? sumAvgBpm / daysWithAvgBpm : null,
                    maxBpmOverall,
                    daysWithHeartData);

            var nutrition = new DailySummaryRangeSummaryResponse.NutritionSummary(
                    totalCalories,
                    daysWithNutrition > 0 ? totalCalories / daysWithNutrition : 0,
                    totalProtein,
                    daysWithNutrition > 0 ? totalProtein / daysWithNutrition : 0,
                    totalFat,
                    daysWithNutrition > 0 ? totalFat / daysWithNutrition : 0,
                    totalCarbs,
                    daysWithNutrition > 0 ? totalCarbs / daysWithNutrition : 0,
                    totalMeals,
                    daysWithNutrition > 0 ? totalMeals / daysWithNutrition : 0,
                    daysWithNutrition);

            var workouts = new DailySummaryRangeSummaryResponse.WorkoutSummary(
                    totalWorkouts, daysWithWorkouts, workoutList);

            return new DailySummaryRangeSummaryResponse(
                    startDate, endDate, daysWithData, activity, sleep, heart, nutrition, workouts, dailyStats);
        }
    }

    @Override
    @Transactional
    public void deleteAllSummaries() {
        log.warn("Deleting all daily summaries");
        jpaRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteSummariesByDeviceId(String deviceId) {
        log.warn("Deleting all daily summaries for deviceId: {}", deviceId);
        jpaRepository.deleteByDeviceId(deviceId);
    }

    @Override
    @Transactional
    public void deleteSummaryForDate(String deviceId, LocalDate date) {
        log.debug("Deleting daily summary for device {} date {}", deviceId, date);
        jpaRepository.deleteByDeviceIdAndDate(deviceId, date);
    }
}
