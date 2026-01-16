package com.healthassistant.meals;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.EnergyRequirementsResponse;
import com.healthassistant.meals.api.dto.EnergyRequirementsResponse.ConsumedNutrition;
import com.healthassistant.meals.api.dto.EnergyRequirementsResponse.RemainingNutrition;
import com.healthassistant.meals.api.dto.MacroTargets;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.steps.api.StepsFacade;
import com.healthassistant.steps.api.dto.StepsDailyBreakdownResponse;
import com.healthassistant.weight.api.WeightFacade;
import com.healthassistant.weight.api.dto.WeightMeasurementResponse;
import com.healthassistant.workout.api.WorkoutFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
class EnergyRequirementsService {

    // Average of last 3 measurements to smooth out daily fluctuations while remaining responsive to trends.
    // 3 days balances noise reduction with recency (backed by sports nutrition research on water weight variance).
    private static final int LBM_MEASUREMENTS_FOR_AVERAGING = 3;

    private final WeightFacade weightFacade;
    private final StepsFacade stepsFacade;
    private final WorkoutFacade workoutFacade;
    private final MealsFacade mealsFacade;
    private final KatchMcArdleCalculator calorieCalculator;
    private final MacroCalculator macroCalculator;

    Optional<EnergyRequirementsResponse> calculateEnergyRequirements(DeviceId deviceId, LocalDate date) {
        String deviceIdValue = deviceId.value();

        List<WeightMeasurementResponse> recentMeasurements = weightFacade.getRecentMeasurements(
                deviceIdValue,
                LBM_MEASUREMENTS_FOR_AVERAGING
        );

        if (recentMeasurements.isEmpty()) {
            log.debug("No weight measurements found for device {}", maskDeviceId(deviceIdValue));
            return Optional.empty();
        }

        List<BigDecimal> validLbmValues = recentMeasurements.stream()
                .map(WeightMeasurementResponse::leanBodyMassKg)
                .filter(lbm -> lbm != null && lbm.compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (validLbmValues.isEmpty()) {
            log.debug("No valid LBM measurements found for device {} - all values are null or zero", maskDeviceId(deviceIdValue));
            return Optional.empty();
        }

        BigDecimal effectiveLbm = calculateEffectiveLbm(validLbmValues);
        BigDecimal currentWeight = recentMeasurements.getFirst().weightKg();

        int steps = Optional.ofNullable(stepsFacade.getDailyBreakdown(deviceIdValue, date))
                .map(StepsDailyBreakdownResponse::totalSteps)
                .orElse(0);

        boolean isTrainingDay = workoutFacade.hasWorkoutOnDate(deviceIdValue, date);

        KatchMcArdleCalculator.CalorieBreakdown calorieBreakdown = calorieCalculator.calculate(
                effectiveLbm,
                steps,
                isTrainingDay
        );

        MacroTargets macroTargets = macroCalculator.calculate(
                effectiveLbm,
                calorieBreakdown.totalKcal(),
                isTrainingDay
        );

        MealDailyDetailResponse mealsData = mealsFacade.getDailyDetail(deviceIdValue, date);
        ConsumedNutrition consumed = buildConsumedNutrition(mealsData);
        RemainingNutrition remaining = buildRemainingNutrition(calorieBreakdown.totalKcal(), macroTargets, consumed);

        return Optional.of(new EnergyRequirementsResponse(
                date,
                effectiveLbm,
                validLbmValues.size(),
                currentWeight,
                calorieBreakdown.bmrKcal(),
                calorieBreakdown.baseKcal(),
                calorieBreakdown.surplusKcal(),
                steps,
                calorieBreakdown.stepIntervals(),
                calorieBreakdown.stepBonusKcal(),
                isTrainingDay,
                calorieBreakdown.trainingBonusKcal(),
                calorieBreakdown.totalKcal(),
                macroTargets,
                consumed,
                remaining
        ));
    }

    private BigDecimal calculateEffectiveLbm(List<BigDecimal> validLbmValues) {
        return validLbmValues.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(validLbmValues.size()), 2, RoundingMode.HALF_UP);
    }

    private ConsumedNutrition buildConsumedNutrition(MealDailyDetailResponse mealsData) {
        if (mealsData == null || mealsData.totalMealCount() == null || mealsData.totalMealCount() == 0) {
            return null;
        }
        return new ConsumedNutrition(
                nullToZero(mealsData.totalCaloriesKcal()),
                nullToZero(mealsData.totalProteinGrams()),
                nullToZero(mealsData.totalFatGrams()),
                nullToZero(mealsData.totalCarbohydratesGrams())
        );
    }

    private RemainingNutrition buildRemainingNutrition(int targetCalories, MacroTargets targets, ConsumedNutrition consumed) {
        if (consumed == null) {
            return null;
        }
        return new RemainingNutrition(
                targetCalories - consumed.caloriesKcal(),
                targets.proteinGrams() - consumed.proteinGrams(),
                targets.fatGrams() - consumed.fatGrams(),
                targets.carbsGrams() - consumed.carbsGrams()
        );
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }
}
