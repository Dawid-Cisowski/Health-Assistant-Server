package com.healthassistant.meals;

import com.healthassistant.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
class KatchMcArdleCalculator {

    private final AppProperties appProperties;

    record CalorieBreakdown(
        int bmrKcal,
        int baseKcal,
        int surplusKcal,
        int stepIntervals,
        int stepBonusKcal,
        int trainingBonusKcal,
        int totalKcal
    ) {}

    int calculateBmr(BigDecimal leanBodyMassKg) {
        validateLeanBodyMass(leanBodyMassKg);
        double lbm = leanBodyMassKg.doubleValue();
        return (int) Math.round(370 + (21.6 * lbm));
    }

    int calculateStepIntervals(int steps) {
        if (steps < 0) {
            throw new IllegalArgumentException("Steps cannot be negative, got: " + steps);
        }
        var config = appProperties.getEnergyRequirements();
        if (steps <= config.getStepsThreshold()) {
            return 0;
        }
        int intervals = (steps - config.getStepsThreshold()) / config.getStepsInterval();
        return Math.min(intervals, config.getMaxIntervals());
    }

    CalorieBreakdown calculate(BigDecimal effectiveLbm, int steps, boolean isTrainingDay) {
        validateLeanBodyMass(effectiveLbm);
        if (steps < 0) {
            throw new IllegalArgumentException("Steps cannot be negative, got: " + steps);
        }

        var config = appProperties.getEnergyRequirements();

        int bmr = calculateBmr(effectiveLbm);
        int baseKcal = (int) Math.round(bmr * config.getBaseMultiplier());

        int stepIntervals = calculateStepIntervals(steps);
        int stepBonusKcal = stepIntervals * config.getKcalPerInterval();

        int trainingBonusKcal = isTrainingDay ? config.getTrainingBonus() : 0;

        int totalKcal = baseKcal + stepBonusKcal + trainingBonusKcal + config.getSurplusKcal();

        return new CalorieBreakdown(
            bmr,
            baseKcal,
            config.getSurplusKcal(),
            stepIntervals,
            stepBonusKcal,
            trainingBonusKcal,
            totalKcal
        );
    }

    private void validateLeanBodyMass(BigDecimal leanBodyMassKg) {
        if (leanBodyMassKg == null) {
            throw new IllegalArgumentException("Lean body mass cannot be null");
        }
        if (leanBodyMassKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Lean body mass must be positive, got: " + leanBodyMassKg);
        }
    }
}
