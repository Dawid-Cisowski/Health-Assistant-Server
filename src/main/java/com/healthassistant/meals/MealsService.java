package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.meals.api.dto.MealsRangeSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
class MealsService implements MealsFacade {

    private static final int MAX_RANGE_DAYS = 365;
    private static final int MAX_MEALS_PER_DAY = 50;

    private final MealDailyProjectionJpaRepository dailyRepository;
    private final MealProjectionJpaRepository mealRepository;
    private final MealsProjector mealsProjector;

    @Override
    public MealDailyDetailResponse getDailyDetail(String deviceId, LocalDate date) {
        return getDailyDetailInternal(deviceId, date)
                .orElseGet(() -> createEmptyDetail(date));
    }

    private Optional<MealDailyDetailResponse> getDailyDetailInternal(String deviceId, LocalDate date) {
        return dailyRepository.findByDeviceIdAndDate(deviceId, date)
                .map(daily -> {
                    List<MealProjectionJpaEntity> meals = mealRepository.findByDeviceIdAndDateOrderByMealNumberAsc(
                            deviceId, date, PageRequest.of(0, MAX_MEALS_PER_DAY));

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

                    return new MealDailyDetailResponse(
                            daily.getDate(), daily.getTotalMealCount(), daily.getTotalCaloriesKcal(),
                            daily.getTotalProteinGrams(), daily.getTotalFatGrams(), daily.getTotalCarbohydratesGrams(),
                            daily.getAverageCaloriesPerMeal(), mealTypeCounts, healthRatingCounts,
                            daily.getFirstMealTime(), daily.getLastMealTime(), mealDetails
                    );
                });
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
        validateDateRange(startDate, endDate);

        List<MealDailyProjectionJpaEntity> dailyData =
                dailyRepository.findByDeviceIdAndDateBetweenOrderByDateAsc(deviceId, startDate, endDate);

        Map<LocalDate, MealDailyProjectionJpaEntity> dataByDate = dailyData.stream()
                .collect(Collectors.toMap(MealDailyProjectionJpaEntity::getDate, Function.identity()));

        List<MealsRangeSummaryResponse.DailyStats> dailyStats = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> mapToDailyStats(date, dataByDate.get(date)))
                .toList();

        RangeTotals totals = dailyStats.stream()
                .reduce(
                        RangeTotals.ZERO,
                        (acc, d) -> acc.add(d.totalMealCount(), d.totalCaloriesKcal(), d.totalProteinGrams(),
                                d.totalFatGrams(), d.totalCarbohydratesGrams(), d.totalMealCount() > 0 ? 1 : 0),
                        RangeTotals::combine
                );

        int averageCaloriesPerDay = totals.daysWithData() > 0 ? totals.calories() / totals.daysWithData() : 0;
        int averageMealsPerDay = totals.daysWithData() > 0 ? totals.mealCount() / totals.daysWithData() : 0;

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
                startDate, endDate, totals.mealCount(), totals.daysWithData(),
                totals.calories(), totals.protein(), totals.fat(), totals.carbs(),
                averageCaloriesPerDay, averageMealsPerDay,
                dayWithMostCalories, dayWithMostMeals, dailyStats
        );
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (daysBetween <= 0) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        if (daysBetween > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range exceeds maximum of " + MAX_RANGE_DAYS + " days");
        }
    }

    private MealsRangeSummaryResponse.DailyStats mapToDailyStats(LocalDate date, MealDailyProjectionJpaEntity dayData) {
        return new MealsRangeSummaryResponse.DailyStats(
                date,
                dayData != null ? dayData.getTotalMealCount() : 0,
                dayData != null ? dayData.getTotalCaloriesKcal() : 0,
                dayData != null ? dayData.getTotalProteinGrams() : 0,
                dayData != null ? dayData.getTotalFatGrams() : 0,
                dayData != null ? dayData.getTotalCarbohydratesGrams() : 0
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
        boolean hasData = mealRepository.existsByDeviceId(deviceId);
        if (hasData) {
            log.warn("Deleting meal projections for device: {}", sanitizeForLog(deviceId));
            mealRepository.deleteByDeviceId(deviceId);
            dailyRepository.deleteByDeviceId(deviceId);
        } else {
            log.debug("No meal projections to delete for device: {}", sanitizeForLog(deviceId));
        }
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        boolean hasData = mealRepository.existsByDeviceIdAndDate(deviceId, date);
        if (hasData) {
            log.debug("Deleting meal projections for device {} date {}", sanitizeForLog(deviceId), date);
            mealRepository.deleteByDeviceIdAndDate(deviceId, date);
            dailyRepository.deleteByDeviceIdAndDate(deviceId, date);
        } else {
            log.debug("No meal projections to delete for device {} date {}", sanitizeForLog(deviceId), date);
        }
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} meal events directly", events.size());
        events.forEach(event -> {
            try {
                mealsProjector.projectMeal(event);
            } catch (Exception e) {
                log.error("Failed to project meal event: {}", sanitizeForLog(event.eventId().value()), e);
            }
        });
    }

    private String sanitizeForLog(String value) {
        if (value == null) return "null";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private record RangeTotals(int mealCount, int calories, int protein, int fat, int carbs, int daysWithData) {
        static final RangeTotals ZERO = new RangeTotals(0, 0, 0, 0, 0, 0);

        RangeTotals add(int meals, int cal, int prot, int f, int c, int days) {
            return new RangeTotals(
                    mealCount + meals,
                    calories + cal,
                    protein + prot,
                    fat + f,
                    carbs + c,
                    daysWithData + days
            );
        }

        static RangeTotals combine(RangeTotals a, RangeTotals b) {
            return new RangeTotals(
                    a.mealCount + b.mealCount,
                    a.calories + b.calories,
                    a.protein + b.protein,
                    a.fat + b.fat,
                    a.carbs + b.carbs,
                    a.daysWithData + b.daysWithData
            );
        }
    }
}
