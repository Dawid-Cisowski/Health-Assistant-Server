package com.healthassistant.steps;

import com.healthassistant.steps.api.StepsFacade;
import com.healthassistant.steps.api.dto.StepsDailyBreakdownResponse;
import com.healthassistant.steps.api.dto.StepsRangeSummaryResponse;
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
@RequestMapping("/v1/steps")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Steps", description = "Steps tracking and analytics endpoints")
class StepsController {

    private final StepsFacade stepsFacade;

    @GetMapping("/daily/{date}")
    @Operation(
            summary = "Get daily steps breakdown",
            description = "Retrieves hourly breakdown of steps for a specific date",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daily breakdown retrieved"),
            @ApiResponse(responseCode = "404", description = "No data for specified date"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<StepsDailyBreakdownResponse> getDailyBreakdown(
            @RequestHeader("X-Device-Id") String deviceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Retrieving daily steps breakdown for device: {} date: {}", deviceId, date);
        StepsDailyBreakdownResponse breakdown = stepsFacade.getDailyBreakdown(deviceId, date);

        if (breakdown.totalSteps() == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(breakdown);
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get steps range summary",
            description = "Retrieves aggregated steps data for a date range",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Range summary retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<StepsRangeSummaryResponse> getRangeSummary(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        log.info("Retrieving steps range summary for device: {} from {} to {}", deviceId, startDate, endDate);
        StepsRangeSummaryResponse summary = stepsFacade.getRangeSummary(deviceId, startDate, endDate);
        return ResponseEntity.ok(summary);
    }
}
