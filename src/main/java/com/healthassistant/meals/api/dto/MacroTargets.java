package com.healthassistant.meals.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Daily macronutrient targets in grams")
public record MacroTargets(
    @JsonProperty("proteinGrams")
    @Schema(description = "Target protein intake in grams", example = "161")
    Integer proteinGrams,

    @JsonProperty("fatGrams")
    @Schema(description = "Target fat intake in grams", example = "50")
    Integer fatGrams,

    @JsonProperty("carbsGrams")
    @Schema(description = "Target carbohydrate intake in grams", example = "450")
    Integer carbsGrams
) {}
