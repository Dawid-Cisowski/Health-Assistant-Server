package com.healthassistant.meals;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
@Transactional
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
        return mealRepository.findMaxMealNumber(deviceId, date) + 1;
    }

    public void deleteByEventId(String eventId) {
        mealRepository.findByEventId(eventId).ifPresent(entity -> {
            String deviceId = entity.getDeviceId();
            LocalDate date = entity.getDate();
            mealRepository.deleteByEventId(eventId);
            log.info("Deleted meal projection for eventId: {}", sanitizeForLog(eventId));
            updateDailyProjection(deviceId, date);
        });
    }

    public void projectCorrectedMeal(String deviceId, java.util.Map<String, Object> payload, Instant occurredAt) {
        mealFactory.createFromCorrectionPayload(deviceId, payload, occurredAt)
                .ifPresent(this::saveProjection);
    }

    private void updateDailyProjection(String deviceId, LocalDate date) {
        try {
            doUpdateDailyProjection(deviceId, date);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for daily meal projection {}/{}, retrying once", deviceId, date);
            doUpdateDailyProjection(deviceId, date);
        }
    }

    private void doUpdateDailyProjection(String deviceId, LocalDate date) {
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

        MacroTotals totals = meals.stream()
                .reduce(
                        MacroTotals.ZERO,
                        (acc, m) -> acc.add(m.getCaloriesKcal(), m.getProteinGrams(), m.getFatGrams(), m.getCarbohydratesGrams()),
                        MacroTotals::combine
                );

        int totalMealCount = meals.size();
        int avgCalories = totalMealCount > 0 ? totals.calories() / totalMealCount : 0;

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

        daily.updateTotals(totalMealCount, totals.calories(), totals.protein(), totals.fat(), totals.carbs(), avgCalories);
        daily.updateMealTypeCounts(mealTypeCounts);
        daily.updateHealthRatingCounts(healthRatingCounts);
        daily.updateMealTimes(firstMealTime, lastMealTime);

        dailyRepository.save(daily);
    }

    private String sanitizeForLog(String value) {
        if (value == null) return "null";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private record MacroTotals(int calories, int protein, int fat, int carbs) {
        static final MacroTotals ZERO = new MacroTotals(0, 0, 0, 0);

        MacroTotals add(int cal, int prot, int f, int c) {
            return new MacroTotals(calories + cal, protein + prot, fat + f, carbs + c);
        }

        static MacroTotals combine(MacroTotals a, MacroTotals b) {
            return new MacroTotals(
                    a.calories + b.calories,
                    a.protein + b.protein,
                    a.fat + b.fat,
                    a.carbs + b.carbs
            );
        }
    }
}
