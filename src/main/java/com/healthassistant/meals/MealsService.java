package com.healthassistant.meals;

import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.meals.api.dto.MealsRangeSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MealsService implements MealsFacade {

    private final MealDailyProjectionJpaRepository dailyRepository;
    private final MealProjectionJpaRepository mealRepository;

    @Override
    public MealDailyDetailResponse getDailyDetail(LocalDate date) {
        Optional<MealDailyDetailResponse> result = getDailyDetailInternal(date);
        return result.orElseGet(() -> createEmptyDetail(date));
    }

    private Optional<MealDailyDetailResponse> getDailyDetailInternal(LocalDate date) {
        Optional<MealDailyProjectionJpaEntity> dailyOpt = dailyRepository.findByDate(date);

        if (dailyOpt.isEmpty()) {
            return Optional.empty();
        }

        MealDailyProjectionJpaEntity daily = dailyOpt.get();
        List<MealProjectionJpaEntity> meals =
            mealRepository.findByDateOrderByMealNumberAsc(date);

        List<MealDailyDetailResponse.MealDetail> mealDetails = meals.stream()
            .map(m -> MealDailyDetailResponse.MealDetail.builder()
                .mealNumber(m.getMealNumber())
                .occurredAt(m.getOccurredAt())
                .title(m.getTitle())
                .mealType(m.getMealType())
                .caloriesKcal(m.getCaloriesKcal())
                .proteinGrams(m.getProteinGrams())
                .fatGrams(m.getFatGrams())
                .carbohydratesGrams(m.getCarbohydratesGrams())
                .healthRating(m.getHealthRating())
                .build())
            .toList();

        MealDailyDetailResponse.MealTypeCounts mealTypeCounts = MealDailyDetailResponse.MealTypeCounts.builder()
            .breakfast(daily.getBreakfastCount())
            .brunch(daily.getBrunchCount())
            .lunch(daily.getLunchCount())
            .dinner(daily.getDinnerCount())
            .snack(daily.getSnackCount())
            .dessert(daily.getDessertCount())
            .drink(daily.getDrinkCount())
            .build();

        MealDailyDetailResponse.HealthRatingCounts healthRatingCounts = MealDailyDetailResponse.HealthRatingCounts.builder()
            .veryHealthy(daily.getVeryHealthyCount())
            .healthy(daily.getHealthyCount())
            .neutral(daily.getNeutralCount())
            .unhealthy(daily.getUnhealthyCount())
            .veryUnhealthy(daily.getVeryUnhealthyCount())
            .build();

        return Optional.of(MealDailyDetailResponse.builder()
            .date(daily.getDate())
            .totalMealCount(daily.getTotalMealCount())
            .totalCaloriesKcal(daily.getTotalCaloriesKcal())
            .totalProteinGrams(daily.getTotalProteinGrams())
            .totalFatGrams(daily.getTotalFatGrams())
            .totalCarbohydratesGrams(daily.getTotalCarbohydratesGrams())
            .averageCaloriesPerMeal(daily.getAverageCaloriesPerMeal())
            .mealTypeCounts(mealTypeCounts)
            .healthRatingCounts(healthRatingCounts)
            .firstMealTime(daily.getFirstMealTime())
            .lastMealTime(daily.getLastMealTime())
            .meals(mealDetails)
            .build());
    }

    private MealDailyDetailResponse createEmptyDetail(LocalDate date) {
        return MealDailyDetailResponse.builder()
            .date(date)
            .totalMealCount(0)
            .totalCaloriesKcal(0)
            .totalProteinGrams(0)
            .totalFatGrams(0)
            .totalCarbohydratesGrams(0)
            .averageCaloriesPerMeal(0)
            .mealTypeCounts(MealDailyDetailResponse.MealTypeCounts.builder()
                .breakfast(0)
                .brunch(0)
                .lunch(0)
                .dinner(0)
                .snack(0)
                .dessert(0)
                .drink(0)
                .build())
            .healthRatingCounts(MealDailyDetailResponse.HealthRatingCounts.builder()
                .veryHealthy(0)
                .healthy(0)
                .neutral(0)
                .unhealthy(0)
                .veryUnhealthy(0)
                .build())
            .firstMealTime(null)
            .lastMealTime(null)
            .meals(List.of())
            .build();
    }

    @Override
    public MealsRangeSummaryResponse getRangeSummary(LocalDate startDate, LocalDate endDate) {
        List<MealDailyProjectionJpaEntity> dailyData =
            dailyRepository.findByDateBetweenOrderByDateAsc(startDate, endDate);

        Map<LocalDate, MealDailyProjectionJpaEntity> dataByDate = dailyData.stream()
            .collect(Collectors.toMap(MealDailyProjectionJpaEntity::getDate, d -> d));

        List<MealsRangeSummaryResponse.DailyStats> dailyStats = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            MealDailyProjectionJpaEntity dayData = dataByDate.get(current);

            dailyStats.add(MealsRangeSummaryResponse.DailyStats.builder()
                .date(current)
                .totalMealCount(dayData != null ? dayData.getTotalMealCount() : 0)
                .totalCaloriesKcal(dayData != null ? dayData.getTotalCaloriesKcal() : 0)
                .totalProteinGrams(dayData != null ? dayData.getTotalProteinGrams() : 0)
                .totalFatGrams(dayData != null ? dayData.getTotalFatGrams() : 0)
                .totalCarbohydratesGrams(dayData != null ? dayData.getTotalCarbohydratesGrams() : 0)
                .build());

            current = current.plusDays(1);
        }

        int totalMealCount = dailyStats.stream()
            .mapToInt(MealsRangeSummaryResponse.DailyStats::totalMealCount)
            .sum();

        int totalCalories = dailyStats.stream()
            .mapToInt(MealsRangeSummaryResponse.DailyStats::totalCaloriesKcal)
            .sum();

        int totalProtein = dailyStats.stream()
            .mapToInt(MealsRangeSummaryResponse.DailyStats::totalProteinGrams)
            .sum();

        int totalFat = dailyStats.stream()
            .mapToInt(MealsRangeSummaryResponse.DailyStats::totalFatGrams)
            .sum();

        int totalCarbs = dailyStats.stream()
            .mapToInt(MealsRangeSummaryResponse.DailyStats::totalCarbohydratesGrams)
            .sum();

        int daysWithData = (int) dailyStats.stream()
            .filter(d -> d.totalMealCount() > 0)
            .count();

        int averageCaloriesPerDay = daysWithData > 0 ? totalCalories / daysWithData : 0;
        int averageMealsPerDay = daysWithData > 0 ? totalMealCount / daysWithData : 0;

        MealsRangeSummaryResponse.DayExtreme dayWithMostCalories = dailyStats.stream()
            .filter(d -> d.totalCaloriesKcal() > 0)
            .max((d1, d2) -> Integer.compare(d1.totalCaloriesKcal(), d2.totalCaloriesKcal()))
            .map(d -> MealsRangeSummaryResponse.DayExtreme.builder()
                .date(d.date())
                .calories(d.totalCaloriesKcal())
                .build())
            .orElse(null);

        MealsRangeSummaryResponse.DayExtremeMeals dayWithMostMeals = dailyStats.stream()
            .filter(d -> d.totalMealCount() > 0)
            .max((d1, d2) -> Integer.compare(d1.totalMealCount(), d2.totalMealCount()))
            .map(d -> MealsRangeSummaryResponse.DayExtremeMeals.builder()
                .date(d.date())
                .mealCount(d.totalMealCount())
                .build())
            .orElse(null);

        return MealsRangeSummaryResponse.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalMealCount(totalMealCount)
            .daysWithData(daysWithData)
            .totalCaloriesKcal(totalCalories)
            .totalProteinGrams(totalProtein)
            .totalFatGrams(totalFat)
            .totalCarbohydratesGrams(totalCarbs)
            .averageCaloriesPerDay(averageCaloriesPerDay)
            .averageMealsPerDay(averageMealsPerDay)
            .dayWithMostCalories(dayWithMostCalories)
            .dayWithMostMeals(dayWithMostMeals)
            .dailyStats(dailyStats)
            .build();
    }

    @Override
    @Transactional
    public void deleteAllProjections() {
        log.warn("Deleting all meal projections");
        mealRepository.deleteAll();
        dailyRepository.deleteAll();
    }
}
