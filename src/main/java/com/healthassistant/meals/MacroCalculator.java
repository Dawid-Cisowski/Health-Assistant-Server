package com.healthassistant.meals;

import com.healthassistant.config.AppProperties;
import com.healthassistant.meals.api.dto.MacroTargets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
class MacroCalculator {

    private static final int PROTEIN_KCAL_PER_GRAM = 4;
    private static final int FAT_KCAL_PER_GRAM = 9;
    private static final int CARBS_KCAL_PER_GRAM = 4;

    private final AppProperties appProperties;

    MacroTargets calculate(BigDecimal effectiveLbm, int targetCalories, boolean isTrainingDay) {
        if (effectiveLbm == null) {
            throw new IllegalArgumentException("Effective LBM cannot be null");
        }
        if (effectiveLbm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Effective LBM must be positive, got: " + effectiveLbm);
        }
        if (targetCalories < 0) {
            throw new IllegalArgumentException("Target calories cannot be negative, got: " + targetCalories);
        }

        var config = appProperties.getEnergyRequirements();
        double lbm = effectiveLbm.doubleValue();

        int proteinGrams = (int) Math.round(config.getProteinPerLbm() * lbm);
        int fatGrams = isTrainingDay ? config.getFatTrainingDay() : config.getFatRestDay();

        int proteinCalories = proteinGrams * PROTEIN_KCAL_PER_GRAM;
        int fatCalories = fatGrams * FAT_KCAL_PER_GRAM;
        int remainingCalories = targetCalories - proteinCalories - fatCalories;
        int carbsGrams = Math.max(0, remainingCalories / CARBS_KCAL_PER_GRAM);

        return new MacroTargets(proteinGrams, fatGrams, carbsGrams);
    }
}
