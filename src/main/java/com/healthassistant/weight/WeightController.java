package com.healthassistant.weight;

import com.healthassistant.config.SecurityUtils;
import com.healthassistant.weight.api.WeightFacade;
import com.healthassistant.weight.api.dto.WeightLatestResponse;
import com.healthassistant.weight.api.dto.WeightMeasurementResponse;
import com.healthassistant.weight.api.dto.WeightRangeSummaryResponse;
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
@RequestMapping("/v1/weight")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Weight", description = "Weight and body composition tracking endpoints")
class WeightController {

    private final WeightFacade weightFacade;

    @GetMapping("/latest")
    @Operation(
            summary = "Get latest weight measurement",
            description = "Retrieves the most recent weight measurement with trend data compared to previous measurements",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Latest weight measurement retrieved"),
            @ApiResponse(responseCode = "404", description = "No weight data found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<WeightLatestResponse> getLatest(
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving latest weight measurement for device {}", SecurityUtils.maskDeviceId(deviceId));
        return weightFacade.getLatestMeasurement(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get weight measurements for date range",
            description = "Retrieves all weight measurements within a date range with summary statistics",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Weight range summary retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<WeightRangeSummaryResponse> getRangeSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        LocalDate today = LocalDate.now();
        LocalDate effectiveEndDate = endDate.isAfter(today) ? today : endDate;

        log.info("Retrieving weight range summary for device {} from {} to {}",
                SecurityUtils.maskDeviceId(deviceId), startDate, effectiveEndDate);
        WeightRangeSummaryResponse summary = weightFacade.getRangeSummary(deviceId, startDate, effectiveEndDate);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{measurementId}")
    @Operation(
            summary = "Get specific weight measurement",
            description = "Retrieves a specific weight measurement by its ID",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Weight measurement retrieved"),
            @ApiResponse(responseCode = "404", description = "Measurement not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<WeightMeasurementResponse> getMeasurement(
            @PathVariable String measurementId,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving weight measurement {} for device {}", measurementId, SecurityUtils.maskDeviceId(deviceId));
        return weightFacade.getMeasurementById(deviceId, measurementId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
