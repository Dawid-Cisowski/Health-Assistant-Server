package com.healthassistant.calories;

import com.healthassistant.calories.api.CaloriesFacade;
import com.healthassistant.calories.api.dto.CaloriesDailyBreakdownResponse;
import com.healthassistant.calories.api.dto.CaloriesRangeSummaryResponse;
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
@RequestMapping("/v1/calories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Calories", description = "Calories tracking and analytics endpoints")
class CaloriesController {

    private final CaloriesFacade caloriesFacade;

    @GetMapping("/daily/{date}")
    @Operation(
            summary = "Get daily calories breakdown",
            description = "Retrieves hourly breakdown of calories for a specific date",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daily breakdown retrieved"),
            @ApiResponse(responseCode = "404", description = "No data for specified date"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<CaloriesDailyBreakdownResponse> getDailyBreakdown(
            @RequestHeader("X-Device-Id") String deviceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Retrieving daily calories breakdown for device: {} date: {}", deviceId, date);
        CaloriesDailyBreakdownResponse breakdown = caloriesFacade.getDailyBreakdown(deviceId, date);

        if (breakdown.totalCalories() == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(breakdown);
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get calories range summary",
            description = "Retrieves aggregated calories data for a date range",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Range summary retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<CaloriesRangeSummaryResponse> getRangeSummary(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        LocalDate today = LocalDate.now();
        LocalDate effectiveEndDate = endDate.isAfter(today) ? today : endDate;

        log.info("Retrieving calories range summary for device: {} from {} to {}", deviceId, startDate, effectiveEndDate);
        CaloriesRangeSummaryResponse summary = caloriesFacade.getRangeSummary(deviceId, startDate, effectiveEndDate);
        return ResponseEntity.ok(summary);
    }
}
