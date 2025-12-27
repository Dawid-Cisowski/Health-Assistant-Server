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
    public void generateDailySummary(LocalDate date) {
        commandHandler.handle(GenerateDailySummaryCommand.forDate(date));
    }

    @Override
    public Optional<DailySummary> getDailySummary(LocalDate date) {
        return jpaRepository.findByDate(date)
                .map(entity -> objectMapper.convertValue(entity.getSummary(), DailySummary.class));
    }

    @Override
    public DailySummaryRangeSummaryResponse getRangeSummary(LocalDate startDate, LocalDate endDate) {
        List<DailySummaryJpaEntity> entities = jpaRepository.findByDateBetweenOrderByDateAsc(startDate, endDate);

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
        int totalSteps, totalActiveMinutes, totalActiveCalories;
        long totalDistanceMeters;
        int daysWithSteps;

        int totalSleepMinutes, daysWithSleep;

        int sumRestingBpm, daysWithRestingBpm;
        int sumAvgBpm, daysWithAvgBpm;
        Integer maxBpmOverall;
        int daysWithHeartData;

        int totalCalories, totalProtein, totalFat, totalCarbs, totalMeals;
        int daysWithNutrition;

        int totalWorkouts, daysWithWorkouts;
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
                    workoutList.add(DailySummaryRangeSummaryResponse.WorkoutSummary.WorkoutInfo.builder()
                            .workoutId(w.workoutId())
                            .note(w.note())
                            .date(s.date())
                            .build());
                }
            }
        }

        DailySummaryRangeSummaryResponse buildResponse(LocalDate startDate, LocalDate endDate,
                                                        List<DailySummaryResponse> dailyStats) {
            return DailySummaryRangeSummaryResponse.builder()
                    .startDate(startDate)
                    .endDate(endDate)
                    .daysWithData(daysWithData)
                    .activity(DailySummaryRangeSummaryResponse.ActivitySummary.builder()
                            .totalSteps(totalSteps)
                            .averageSteps(daysWithSteps > 0 ? totalSteps / daysWithSteps : 0)
                            .totalActiveMinutes(totalActiveMinutes)
                            .averageActiveMinutes(daysWithData > 0 ? totalActiveMinutes / daysWithData : 0)
                            .totalActiveCalories(totalActiveCalories)
                            .averageActiveCalories(daysWithData > 0 ? totalActiveCalories / daysWithData : 0)
                            .totalDistanceMeters(totalDistanceMeters)
                            .averageDistanceMeters(daysWithData > 0 ? totalDistanceMeters / daysWithData : 0L)
                            .build())
                    .sleep(DailySummaryRangeSummaryResponse.SleepSummary.builder()
                            .totalSleepMinutes(totalSleepMinutes)
                            .averageSleepMinutes(daysWithSleep > 0 ? totalSleepMinutes / daysWithSleep : 0)
                            .daysWithSleep(daysWithSleep)
                            .build())
                    .heart(DailySummaryRangeSummaryResponse.HeartSummary.builder()
                            .averageRestingBpm(daysWithRestingBpm > 0 ? sumRestingBpm / daysWithRestingBpm : null)
                            .averageDailyBpm(daysWithAvgBpm > 0 ? sumAvgBpm / daysWithAvgBpm : null)
                            .maxBpmOverall(maxBpmOverall)
                            .daysWithData(daysWithHeartData)
                            .build())
                    .nutrition(DailySummaryRangeSummaryResponse.NutritionSummary.builder()
                            .totalCalories(totalCalories)
                            .averageCalories(daysWithNutrition > 0 ? totalCalories / daysWithNutrition : 0)
                            .totalProtein(totalProtein)
                            .averageProtein(daysWithNutrition > 0 ? totalProtein / daysWithNutrition : 0)
                            .totalFat(totalFat)
                            .averageFat(daysWithNutrition > 0 ? totalFat / daysWithNutrition : 0)
                            .totalCarbs(totalCarbs)
                            .averageCarbs(daysWithNutrition > 0 ? totalCarbs / daysWithNutrition : 0)
                            .totalMeals(totalMeals)
                            .averageMealsPerDay(daysWithNutrition > 0 ? totalMeals / daysWithNutrition : 0)
                            .daysWithData(daysWithNutrition)
                            .build())
                    .workouts(DailySummaryRangeSummaryResponse.WorkoutSummary.builder()
                            .totalWorkouts(totalWorkouts)
                            .daysWithWorkouts(daysWithWorkouts)
                            .workoutList(workoutList)
                            .build())
                    .dailyStats(dailyStats)
                    .build();
        }
    }

    @Override
    @Transactional
    public void deleteAllSummaries() {
        log.warn("Deleting all daily summaries");
        jpaRepository.deleteAll();
    }
}
