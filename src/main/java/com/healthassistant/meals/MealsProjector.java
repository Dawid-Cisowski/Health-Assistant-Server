package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
class MealsProjector {

    private final MealProjectionJpaRepository mealRepository;
    private final MealDailyProjectionJpaRepository dailyRepository;
    private final MealFactory mealFactory;

    public void projectMeal(StoredEventData eventData) {
        mealFactory.createFromEvent(eventData)
                .ifPresent(this::saveProjection);
    }

    private void saveProjection(Meal meal) {
        try {
            doSaveProjection(meal);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for meal {}/{}, retrying once",
                    meal.deviceId(), meal.date());
            doSaveProjection(meal);
        }
    }

    private void doSaveProjection(Meal meal) {
        Optional<MealProjectionJpaEntity> existingOpt = mealRepository.findByEventId(meal.eventId());

        MealProjectionJpaEntity entity;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.updateFrom(meal);
            log.debug("Updating existing meal projection for event {}", meal.eventId());
        } else {
            int mealNumber = calculateNextMealNumber(meal.deviceId(), meal.date());
            entity = MealProjectionJpaEntity.from(meal, mealNumber);
            log.debug("Creating new meal projection #{} for date {} from event {}",
                    mealNumber, meal.date(), meal.eventId());
        }

        mealRepository.save(entity);
        updateDailyProjection(meal.deviceId(), meal.date());
    }

    private int calculateNextMealNumber(String deviceId, LocalDate date) {
        List<MealProjectionJpaEntity> existingMeals =
                mealRepository.findByDeviceIdAndDateOrderByMealNumberAsc(deviceId, date);
        return existingMeals.isEmpty() ? 1 :
                existingMeals.getLast().getMealNumber() + 1;
    }

    public void deleteByEventId(String eventId) {
        mealRepository.findByEventId(eventId).ifPresent(entity -> {
            String deviceId = entity.getDeviceId();
            LocalDate date = entity.getDate();
            mealRepository.deleteByEventId(eventId);
            log.info("Deleted meal projection for eventId: {}", eventId);
            updateDailyProjection(deviceId, date);
        });
    }

    private void updateDailyProjection(String deviceId, LocalDate date) {
        List<MealProjectionJpaEntity> meals =
                mealRepository.findByDeviceIdAndDateOrderByMealNumberAsc(deviceId, date);

        if (meals.isEmpty()) {
            dailyRepository.findByDeviceIdAndDate(deviceId, date)
                    .ifPresent(daily -> {
                        dailyRepository.delete(daily);
                        log.debug("Deleted empty daily meal projection for {}/{}", deviceId, date);
                    });
            return;
        }

        int totalCalories = meals.stream().mapToInt(MealProjectionJpaEntity::getCaloriesKcal).sum();
        int totalProtein = meals.stream().mapToInt(MealProjectionJpaEntity::getProteinGrams).sum();
        int totalFat = meals.stream().mapToInt(MealProjectionJpaEntity::getFatGrams).sum();
        int totalCarbs = meals.stream().mapToInt(MealProjectionJpaEntity::getCarbohydratesGrams).sum();

        int totalMealCount = meals.size();
        int avgCalories = totalMealCount > 0 ? totalCalories / totalMealCount : 0;

        Map<String, Long> mealTypeCounts = meals.stream()
                .collect(Collectors.groupingBy(MealProjectionJpaEntity::getMealType, Collectors.counting()));

        Map<String, Long> healthRatingCounts = meals.stream()
                .collect(Collectors.groupingBy(MealProjectionJpaEntity::getHealthRating, Collectors.counting()));

        Instant firstMealTime = meals.stream()
                .map(MealProjectionJpaEntity::getOccurredAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        Instant lastMealTime = meals.stream()
                .map(MealProjectionJpaEntity::getOccurredAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        MealDailyProjectionJpaEntity daily = dailyRepository.findByDeviceIdAndDate(deviceId, date)
                .orElseGet(() -> MealDailyProjectionJpaEntity.builder()
                        .deviceId(deviceId)
                        .date(date)
                        .build());

        daily.setTotalMealCount(totalMealCount);
        daily.setBreakfastCount(mealTypeCounts.getOrDefault("BREAKFAST", 0L).intValue());
        daily.setBrunchCount(mealTypeCounts.getOrDefault("BRUNCH", 0L).intValue());
        daily.setLunchCount(mealTypeCounts.getOrDefault("LUNCH", 0L).intValue());
        daily.setDinnerCount(mealTypeCounts.getOrDefault("DINNER", 0L).intValue());
        daily.setSnackCount(mealTypeCounts.getOrDefault("SNACK", 0L).intValue());
        daily.setDessertCount(mealTypeCounts.getOrDefault("DESSERT", 0L).intValue());
        daily.setDrinkCount(mealTypeCounts.getOrDefault("DRINK", 0L).intValue());

        daily.setTotalCaloriesKcal(totalCalories);
        daily.setTotalProteinGrams(totalProtein);
        daily.setTotalFatGrams(totalFat);
        daily.setTotalCarbohydratesGrams(totalCarbs);
        daily.setAverageCaloriesPerMeal(avgCalories);

        daily.setVeryHealthyCount(healthRatingCounts.getOrDefault("VERY_HEALTHY", 0L).intValue());
        daily.setHealthyCount(healthRatingCounts.getOrDefault("HEALTHY", 0L).intValue());
        daily.setNeutralCount(healthRatingCounts.getOrDefault("NEUTRAL", 0L).intValue());
        daily.setUnhealthyCount(healthRatingCounts.getOrDefault("UNHEALTHY", 0L).intValue());
        daily.setVeryUnhealthyCount(healthRatingCounts.getOrDefault("VERY_UNHEALTHY", 0L).intValue());

        daily.setFirstMealTime(firstMealTime);
        daily.setLastMealTime(lastMealTime);

        dailyRepository.save(daily);
    }
}
