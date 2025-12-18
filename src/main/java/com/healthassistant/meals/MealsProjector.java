package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void projectMeal(StoredEventData eventData) {
        try {
            mealFactory.createFromEvent(eventData)
                    .ifPresent(this::saveProjection);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Race condition during meal projection for event {}, skipping", eventData.eventId().value());
        }
    }

    private synchronized void saveProjection(Meal meal) {
        Optional<MealProjectionJpaEntity> existingOpt = mealRepository.findByEventId(meal.eventId());

        MealProjectionJpaEntity entity;

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.updateFrom(meal);
            log.debug("Updating existing meal projection for event {}", meal.eventId());
        } else {
            int mealNumber = calculateNextMealNumber(meal.date());
            entity = MealProjectionJpaEntity.from(meal, mealNumber);
            log.debug("Creating new meal projection #{} for date {} from event {}",
                    mealNumber, meal.date(), meal.eventId());
        }

        mealRepository.save(entity);
        updateDailyProjection(meal.date());
    }

    private int calculateNextMealNumber(LocalDate date) {
        List<MealProjectionJpaEntity> existingMeals =
                mealRepository.findByDateOrderByMealNumberAsc(date);
        return existingMeals.isEmpty() ? 1 :
                existingMeals.getLast().getMealNumber() + 1;
    }

    private void updateDailyProjection(LocalDate date) {
        List<MealProjectionJpaEntity> meals =
                mealRepository.findByDateOrderByMealNumberAsc(date);

        if (meals.isEmpty()) {
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

        MealDailyProjectionJpaEntity daily = dailyRepository.findByDate(date)
                .orElseGet(() -> MealDailyProjectionJpaEntity.builder()
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
