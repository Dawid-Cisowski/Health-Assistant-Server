package com.healthassistant.infrastructure.web.rest;

import com.healthassistant.application.steps.query.StepsQueryService;
import com.healthassistant.dto.StepsDailyBreakdownResponse;
import com.healthassistant.dto.StepsRangeSummaryResponse;
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
@Tag(name = "Steps", description = "Query step counts at different time scales")
class StepsQueryController {

    private final StepsQueryService stepsQueryService;

    @GetMapping("/daily/{date}")
    @Operation(
            summary = "Get daily hourly breakdown",
            description = "Retrieves 24-hour breakdown of steps for a specific date. " +
                         "Used for daily dashboard view with hourly bars. " +
                         "Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daily breakdown found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "404", description = "No step data for this date")
    })
    ResponseEntity<StepsDailyBreakdownResponse> getDailyBreakdown(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Retrieving daily steps breakdown for date: {}", date);
        return stepsQueryService.getDailyBreakdown(date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get range summary",
            description = "Retrieves daily totals for a date range. " +
                         "Client decides if it's week (7 days), month (~30 days), or year (365 days). " +
                         "Returns summary with daily statistics for each day in range. " +
                         "Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Range summary retrieved"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "400", description = "Invalid date range (endDate before startDate)")
    })
    ResponseEntity<StepsRangeSummaryResponse> getRangeSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (endDate.isBefore(startDate)) {
            log.warn("Invalid date range: endDate {} is before startDate {}", endDate, startDate);
            return ResponseEntity.badRequest().build();
        }

        log.info("Retrieving steps range summary from {} to {}", startDate, endDate);
        StepsRangeSummaryResponse summary = stepsQueryService.getRangeSummary(startDate, endDate);
        return ResponseEntity.ok(summary);
    }
}
