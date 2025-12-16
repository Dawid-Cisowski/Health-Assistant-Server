package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
class MealsProjector {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final String MEAL_RECORDED_V1 = "MealRecorded.v1";

    private final MealProjectionJpaRepository mealRepository;
    private final MealDailyProjectionJpaRepository dailyRepository;

    @Transactional
    public void projectMeal(StoredEventData eventData) {
        String eventType = eventData.eventType().value();

        if (MEAL_RECORDED_V1.equals(eventType)) {
            try {
                projectMealRecorded(eventData);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("Race condition during meal projection for event {}, skipping", eventData.eventId().value());
            }
        }
    }

    private void projectMealRecorded(StoredEventData eventData) {
        Map<String, Object> payload = eventData.payload();

        String title = getString(payload, "title");
        String mealType = getString(payload, "mealType");
        Integer caloriesKcal = getInteger(payload, "caloriesKcal");
        Integer proteinGrams = getInteger(payload, "proteinGrams");
        Integer fatGrams = getInteger(payload, "fatGrams");
        Integer carbohydratesGrams = getInteger(payload, "carbohydratesGrams");
        String healthRating = getString(payload, "healthRating");

        if (title == null || mealType == null || healthRating == null) {
            log.warn("MealRecorded event missing required fields, skipping projection");
            return;
        }

        Instant occurredAt = eventData.occurredAt();
        ZonedDateTime zonedOccurredAt = occurredAt.atZone(POLAND_ZONE);
        LocalDate date = zonedOccurredAt.toLocalDate();

        updateMealProjection(
                eventData.eventId().value(),
                date,
                occurredAt,
                title,
                mealType,
                caloriesKcal != null ? caloriesKcal : 0,
                proteinGrams != null ? proteinGrams : 0,
                fatGrams != null ? fatGrams : 0,
                carbohydratesGrams != null ? carbohydratesGrams : 0,
                healthRating
        );
    }

    private synchronized void updateMealProjection(
            String eventId,
            LocalDate date,
            Instant occurredAt,
            String title,
            String mealType,
            int caloriesKcal,
            int proteinGrams,
            int fatGrams,
            int carbohydratesGrams,
            String healthRating
    ) {
        Optional<MealProjectionJpaEntity> existingOpt = mealRepository.findByEventId(eventId);

        MealProjectionJpaEntity meal;

        if (existingOpt.isPresent()) {
            meal = existingOpt.get();
            meal.setTitle(title);
            meal.setMealType(mealType);
            meal.setCaloriesKcal(caloriesKcal);
            meal.setProteinGrams(proteinGrams);
            meal.setFatGrams(fatGrams);
            meal.setCarbohydratesGrams(carbohydratesGrams);
            meal.setHealthRating(healthRating);
            log.debug("Updating existing meal projection for event {}", eventId);
        } else {
            List<MealProjectionJpaEntity> existingMeals =
                    mealRepository.findByDateOrderByMealNumberAsc(date);

            int mealNumber = existingMeals.isEmpty() ? 1 :
                    existingMeals.get(existingMeals.size() - 1).getMealNumber() + 1;

            meal = MealProjectionJpaEntity.builder()
                    .eventId(eventId)
                    .date(date)
                    .mealNumber(mealNumber)
                    .occurredAt(occurredAt)
                    .title(title)
                    .mealType(mealType)
                    .caloriesKcal(caloriesKcal)
                    .proteinGrams(proteinGrams)
                    .fatGrams(fatGrams)
                    .carbohydratesGrams(carbohydratesGrams)
                    .healthRating(healthRating)
                    .build();

            log.debug("Creating new meal projection #{} for date {} from event {}", mealNumber, date, eventId);
        }

        mealRepository.save(meal);

        updateDailyProjection(date);
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

        Optional<MealDailyProjectionJpaEntity> existingOpt = dailyRepository.findByDate(date);

        MealDailyProjectionJpaEntity daily = existingOpt.orElseGet(() -> MealDailyProjectionJpaEntity.builder()
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

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        return value.toString();
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
