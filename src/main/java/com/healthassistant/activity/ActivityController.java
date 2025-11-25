package com.healthassistant.activity;

import com.healthassistant.activity.api.ActivityFacade;
import com.healthassistant.activity.api.dto.ActivityDailyBreakdownResponse;
import com.healthassistant.activity.api.dto.ActivityRangeSummaryResponse;
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
@RequestMapping("/v1/activity")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Activity", description = "Activity (active minutes) tracking and analytics endpoints")
class ActivityController {

    private final ActivityFacade activityFacade;

    @GetMapping("/daily/{date}")
    @Operation(
            summary = "Get daily activity breakdown",
            description = "Retrieves hourly breakdown of active minutes for a specific date",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daily breakdown retrieved"),
            @ApiResponse(responseCode = "404", description = "No data for specified date"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<ActivityDailyBreakdownResponse> getDailyBreakdown(
            @RequestHeader("X-Device-Id") String deviceId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        log.info("Retrieving daily activity breakdown for device: {} date: {}", deviceId, date);
        ActivityDailyBreakdownResponse breakdown = activityFacade.getDailyBreakdown(deviceId, date);

        if (breakdown.totalActiveMinutes() == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(breakdown);
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get activity range summary",
            description = "Retrieves aggregated activity data for a date range",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Range summary retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<ActivityRangeSummaryResponse> getRangeSummary(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        log.info("Retrieving activity range summary for device: {} from {} to {}", deviceId, startDate, endDate);
        ActivityRangeSummaryResponse summary = activityFacade.getRangeSummary(deviceId, startDate, endDate);
        return ResponseEntity.ok(summary);
    }
}
