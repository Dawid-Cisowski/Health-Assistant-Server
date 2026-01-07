package com.healthassistant.sleep;

import com.healthassistant.sleep.api.SleepFacade;
import com.healthassistant.sleep.api.dto.SleepDailyDetailResponse;
import com.healthassistant.sleep.api.dto.SleepRangeSummaryResponse;
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
import java.util.Map;

@RestController
@RequestMapping("/v1/sleep")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sleep", description = "Sleep tracking and analytics endpoints")
class SleepController {

    private final SleepFacade sleepFacade;

    @GetMapping("/daily/{date}")
    @Operation(
            summary = "Get daily sleep detail",
            description = "Retrieves all sleep sessions for a specific date with detailed metrics",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daily sleep detail retrieved"),
            @ApiResponse(responseCode = "404", description = "No sleep data for specified date"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<SleepDailyDetailResponse> getDailyDetail(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving daily sleep detail for device {} and date: {}", maskDeviceId(deviceId), date);
        SleepDailyDetailResponse detail = sleepFacade.getDailyDetail(deviceId, date);

        if (detail.totalSleepMinutes() == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(detail);
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get sleep range summary",
            description = "Retrieves aggregated sleep data for a date range with daily breakdown",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Range summary retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<SleepRangeSummaryResponse> getRangeSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        log.info("Retrieving sleep range summary for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);
        SleepRangeSummaryResponse summary = sleepFacade.getRangeSummary(deviceId, startDate, endDate);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/admin/rebuild-projections")
    @Operation(
            summary = "Rebuild sleep projections",
            description = "Rebuilds sleep projections from existing events for a specific device. " +
                    "Useful for repairing projections that were not created due to duplicate event handling."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Projections rebuilt successfully"),
            @ApiResponse(responseCode = "400", description = "Missing device ID")
    })
    ResponseEntity<Map<String, Object>> rebuildProjections(
            @RequestParam String deviceId
    ) {
        if (deviceId == null || deviceId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "deviceId is required"));
        }

        log.info("Admin request to rebuild sleep projections for device: {}", maskDeviceId(deviceId));
        int rebuiltCount = sleepFacade.rebuildProjections(deviceId);

        return ResponseEntity.ok(Map.of(
                "deviceId", deviceId,
                "rebuiltProjections", rebuiltCount
        ));
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }
}
