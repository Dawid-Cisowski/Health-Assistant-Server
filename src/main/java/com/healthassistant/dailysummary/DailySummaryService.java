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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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

        List<DailySummary> summaries = entities.stream()
                .map(entity -> objectMapper.convertValue(entity.getSummary(), DailySummary.class))
                .toList();

        int daysWithData = summaries.size();

        int totalSteps = summaries.stream()
                .map(s -> s.activity().steps())
                .filter(steps -> steps != null)
                .mapToInt(Integer::intValue)
                .sum();

        int totalActiveMinutes = summaries.stream()
                .map(s -> s.activity().activeMinutes())
                .filter(minutes -> minutes != null)
                .mapToInt(Integer::intValue)
                .sum();

        int totalActiveCalories = summaries.stream()
                .map(s -> s.activity().activeCalories())
                .filter(calories -> calories != null)
                .mapToInt(Integer::intValue)
                .sum();

        long totalDistanceMeters = summaries.stream()
                .map(s -> s.activity().distanceMeters())
                .filter(distance -> distance != null)
                .mapToLong(Long::longValue)
                .sum();

        int daysWithSteps = (int) summaries.stream()
                .map(s -> s.activity().steps())
                .filter(steps -> steps != null && steps > 0)
                .count();

        DailySummaryRangeSummaryResponse.ActivitySummary activitySummary =
                DailySummaryRangeSummaryResponse.ActivitySummary.builder()
                .totalSteps(totalSteps)
                .averageSteps(daysWithSteps > 0 ? totalSteps / daysWithSteps : 0)
                .totalActiveMinutes(totalActiveMinutes)
                .averageActiveMinutes(daysWithData > 0 ? totalActiveMinutes / daysWithData : 0)
                .totalActiveCalories(totalActiveCalories)
                .averageActiveCalories(daysWithData > 0 ? totalActiveCalories / daysWithData : 0)
                .totalDistanceMeters(totalDistanceMeters)
                .averageDistanceMeters(daysWithData > 0 ? totalDistanceMeters / daysWithData : 0L)
                .build();

        int totalSleepMinutes = summaries.stream()
                .flatMap(s -> s.sleep().stream())
                .map(DailySummary.Sleep::totalMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int daysWithSleep = (int) summaries.stream()
                .filter(s -> !s.sleep().isEmpty())
                .count();

        DailySummaryRangeSummaryResponse.SleepSummary sleepSummary =
                DailySummaryRangeSummaryResponse.SleepSummary.builder()
                .totalSleepMinutes(totalSleepMinutes)
                .averageSleepMinutes(daysWithSleep > 0 ? totalSleepMinutes / daysWithSleep : 0)
                .daysWithSleep(daysWithSleep)
                .build();

        int sumRestingBpm = summaries.stream()
                .map(s -> s.heart().restingBpm())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int daysWithRestingBpm = (int) summaries.stream()
                .map(s -> s.heart().restingBpm())
                .filter(Objects::nonNull)
                .count();

        int sumAvgBpm = summaries.stream()
                .map(s -> s.heart().avgBpm())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int daysWithAvgBpm = (int) summaries.stream()
                .map(s -> s.heart().avgBpm())
                .filter(bpm -> bpm != null)
                .count();

        Integer maxBpmOverall = summaries.stream()
                .map(s -> s.heart().maxBpm())
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);

        int daysWithHeartData = (int) summaries.stream()
                .filter(s -> s.heart().restingBpm() != null || s.heart().avgBpm() != null || s.heart().maxBpm() != null)
                .count();

        DailySummaryRangeSummaryResponse.HeartSummary heartSummary =
                DailySummaryRangeSummaryResponse.HeartSummary.builder()
                .averageRestingBpm(daysWithRestingBpm > 0 ? sumRestingBpm / daysWithRestingBpm : null)
                .averageDailyBpm(daysWithAvgBpm > 0 ? sumAvgBpm / daysWithAvgBpm : null)
                .maxBpmOverall(maxBpmOverall)
                .daysWithData(daysWithHeartData)
                .build();

        int totalCalories = summaries.stream()
                .map(s -> s.nutrition().totalCalories())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int totalProtein = summaries.stream()
                .map(s -> s.nutrition().totalProtein())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int totalFat = summaries.stream()
                .map(s -> s.nutrition().totalFat())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int totalCarbs = summaries.stream()
                .map(s -> s.nutrition().totalCarbs())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int totalMeals = summaries.stream()
                .map(s -> s.nutrition().mealCount())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int daysWithNutrition = (int) summaries.stream()
                .map(s -> s.nutrition().mealCount())
                .filter(count -> count != null && count > 0)
                .count();

        DailySummaryRangeSummaryResponse.NutritionSummary nutritionSummary =
                DailySummaryRangeSummaryResponse.NutritionSummary.builder()
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
                .build();

        int totalWorkouts = summaries.stream()
                .map(s -> s.workouts().size())
                .mapToInt(Integer::intValue)
                .sum();

        int daysWithWorkouts = (int) summaries.stream()
                .filter(s -> !s.workouts().isEmpty())
                .count();

        List<DailySummaryRangeSummaryResponse.WorkoutSummary.WorkoutInfo> workoutList = summaries.stream()
                .flatMap(summary -> summary.workouts().stream()
                        .map(workout -> DailySummaryRangeSummaryResponse.WorkoutSummary.WorkoutInfo.builder()
                                .workoutId(workout.workoutId())
                                .note(workout.note())
                                .date(summary.date())
                                .build()))
                .collect(Collectors.toList());

        DailySummaryRangeSummaryResponse.WorkoutSummary workoutSummary =
                DailySummaryRangeSummaryResponse.WorkoutSummary.builder()
                .totalWorkouts(totalWorkouts)
                .daysWithWorkouts(daysWithWorkouts)
                .workoutList(workoutList)
                .build();

        List<DailySummaryResponse> dailyStats = summaries.stream()
                .map(DailySummaryMapper.INSTANCE::toResponse)
                .collect(Collectors.toList());

        return DailySummaryRangeSummaryResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .daysWithData(daysWithData)
                .activity(activitySummary)
                .sleep(sleepSummary)
                .heart(heartSummary)
                .nutrition(nutritionSummary)
                .workouts(workoutSummary)
                .dailyStats(dailyStats)
                .build();
    }

    @Override
    @Transactional
    public void deleteAllSummaries() {
        log.warn("Deleting all daily summaries");
        jpaRepository.deleteAll();
    }
}
