package com.healthassistant.meals.api.dto;

import com.healthassistant.healthevents.api.dto.payload.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecordMealFromCatalogRequest(
        @NotEmpty @Size(max = 50) List<@Valid CatalogProductSelection> products,
        @NotNull MealType mealType,
        @Size(max = 500) String title,
        Instant occurredAt
) {
    public record CatalogProductSelection(
            @NotNull Long productId,
            @DecimalMin("0.1") @DecimalMax("10.0") BigDecimal portionMultiplier
    ) {
        public CatalogProductSelection {
            if (portionMultiplier == null) {
                portionMultiplier = BigDecimal.ONE;
            }
        }

        public double effectiveMultiplier() {
            return portionMultiplier.doubleValue();
        }
    }
}
