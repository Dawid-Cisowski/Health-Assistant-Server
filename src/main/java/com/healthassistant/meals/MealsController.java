package com.healthassistant.meals;

import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.meals.api.dto.MealsRangeSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/meals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Meals", description = "Meal tracking and analytics endpoints")
class MealsController {

    private final MealsFacade mealsFacade;

    @GetMapping("/daily/{date}")
    @Operation(
            summary = "Get daily meal detail",
            description = "Retrieves all meals for a specific date with nutritional metrics",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daily meal detail retrieved"),
            @ApiResponse(responseCode = "404", description = "No meal data for specified date"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MealDailyDetailResponse> getDailyDetail(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving daily meal detail for device {} and date: {}", deviceId, date);
        MealDailyDetailResponse detail = mealsFacade.getDailyDetail(deviceId, date);

        if (detail.totalMealCount() == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(detail);
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get meals range summary",
            description = "Retrieves aggregated meal data for a date range with daily breakdown",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Range summary retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<MealsRangeSummaryResponse> getRangeSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        log.info("Retrieving meals range summary for device {} from {} to {}", deviceId, startDate, endDate);
        MealsRangeSummaryResponse summary = mealsFacade.getRangeSummary(deviceId, startDate, endDate);
        return ResponseEntity.ok(summary);
    }
}
