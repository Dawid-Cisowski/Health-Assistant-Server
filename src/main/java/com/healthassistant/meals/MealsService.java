package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.StoredEventData;
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
class MealsService implements MealsFacade {

    private final MealDailyProjectionJpaRepository dailyRepository;
    private final MealProjectionJpaRepository mealRepository;
    private final MealsProjector mealsProjector;

    @Override
    public MealDailyDetailResponse getDailyDetail(String deviceId, LocalDate date) {
        Optional<MealDailyDetailResponse> result = getDailyDetailInternal(deviceId, date);
        return result.orElseGet(() -> createEmptyDetail(date));
    }

    private Optional<MealDailyDetailResponse> getDailyDetailInternal(String deviceId, LocalDate date) {
        Optional<MealDailyProjectionJpaEntity> dailyOpt = dailyRepository.findByDeviceIdAndDate(deviceId, date);

        if (dailyOpt.isEmpty()) {
            return Optional.empty();
        }

        MealDailyProjectionJpaEntity daily = dailyOpt.get();
        List<MealProjectionJpaEntity> meals =
            mealRepository.findByDeviceIdAndDateOrderByMealNumberAsc(deviceId, date);

        List<MealDailyDetailResponse.MealDetail> mealDetails = meals.stream()
            .map(m -> new MealDailyDetailResponse.MealDetail(
                m.getMealNumber(), m.getOccurredAt(), m.getTitle(), m.getMealType(),
                m.getCaloriesKcal(), m.getProteinGrams(), m.getFatGrams(),
                m.getCarbohydratesGrams(), m.getHealthRating()))
            .toList();

        var mealTypeCounts = new MealDailyDetailResponse.MealTypeCounts(
            daily.getBreakfastCount(), daily.getBrunchCount(), daily.getLunchCount(),
            daily.getDinnerCount(), daily.getSnackCount(), daily.getDessertCount(), daily.getDrinkCount());

        var healthRatingCounts = new MealDailyDetailResponse.HealthRatingCounts(
            daily.getVeryHealthyCount(), daily.getHealthyCount(), daily.getNeutralCount(),
            daily.getUnhealthyCount(), daily.getVeryUnhealthyCount());

        return Optional.of(new MealDailyDetailResponse(
            daily.getDate(), daily.getTotalMealCount(), daily.getTotalCaloriesKcal(),
            daily.getTotalProteinGrams(), daily.getTotalFatGrams(), daily.getTotalCarbohydratesGrams(),
            daily.getAverageCaloriesPerMeal(), mealTypeCounts, healthRatingCounts,
            daily.getFirstMealTime(), daily.getLastMealTime(), mealDetails
        ));
    }

    private MealDailyDetailResponse createEmptyDetail(LocalDate date) {
        return new MealDailyDetailResponse(
            date, 0, 0, 0, 0, 0, 0,
            new MealDailyDetailResponse.MealTypeCounts(0, 0, 0, 0, 0, 0, 0),
            new MealDailyDetailResponse.HealthRatingCounts(0, 0, 0, 0, 0),
            null, null, List.of()
        );
    }

    @Override
    public MealsRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<MealDailyProjectionJpaEntity> dailyData =
            dailyRepository.findByDeviceIdAndDateBetweenOrderByDateAsc(deviceId, startDate, endDate);

        Map<LocalDate, MealDailyProjectionJpaEntity> dataByDate = dailyData.stream()
            .collect(Collectors.toMap(MealDailyProjectionJpaEntity::getDate, d -> d));

        List<MealsRangeSummaryResponse.DailyStats> dailyStats = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            MealDailyProjectionJpaEntity dayData = dataByDate.get(current);

            dailyStats.add(new MealsRangeSummaryResponse.DailyStats(
                current,
                dayData != null ? dayData.getTotalMealCount() : 0,
                dayData != null ? dayData.getTotalCaloriesKcal() : 0,
                dayData != null ? dayData.getTotalProteinGrams() : 0,
                dayData != null ? dayData.getTotalFatGrams() : 0,
                dayData != null ? dayData.getTotalCarbohydratesGrams() : 0
            ));

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
            .map(d -> new MealsRangeSummaryResponse.DayExtreme(d.date(), d.totalCaloriesKcal()))
            .orElse(null);

        MealsRangeSummaryResponse.DayExtremeMeals dayWithMostMeals = dailyStats.stream()
            .filter(d -> d.totalMealCount() > 0)
            .max((d1, d2) -> Integer.compare(d1.totalMealCount(), d2.totalMealCount()))
            .map(d -> new MealsRangeSummaryResponse.DayExtremeMeals(d.date(), d.totalMealCount()))
            .orElse(null);

        return new MealsRangeSummaryResponse(
            startDate, endDate, totalMealCount, daysWithData,
            totalCalories, totalProtein, totalFat, totalCarbs,
            averageCaloriesPerDay, averageMealsPerDay,
            dayWithMostCalories, dayWithMostMeals, dailyStats
        );
    }

    @Override
    @Transactional
    public void deleteAllProjections() {
        log.warn("Deleting all meal projections");
        mealRepository.deleteAll();
        dailyRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteProjectionsByDeviceId(String deviceId) {
        log.warn("Deleting meal projections for device: {}", deviceId);
        mealRepository.deleteByDeviceId(deviceId);
        dailyRepository.deleteByDeviceId(deviceId);
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        log.debug("Deleting meal projections for device {} date {}", deviceId, date);
        mealRepository.deleteByDeviceIdAndDate(deviceId, date);
        dailyRepository.deleteByDeviceIdAndDate(deviceId, date);
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} meal events directly", events.size());
        for (StoredEventData event : events) {
            try {
                mealsProjector.projectMeal(event);
            } catch (Exception e) {
                log.error("Failed to project meal event: {}", event.eventId().value(), e);
            }
        }
    }
}
