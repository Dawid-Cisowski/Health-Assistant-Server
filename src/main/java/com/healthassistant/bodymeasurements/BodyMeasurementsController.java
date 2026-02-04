package com.healthassistant.bodymeasurements;

import com.healthassistant.bodymeasurements.api.BodyMeasurementsFacade;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementLatestResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementRangeSummaryResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyMeasurementSummaryResponse;
import com.healthassistant.bodymeasurements.api.dto.BodyPart;
import com.healthassistant.bodymeasurements.api.dto.BodyPartHistoryResponse;
import com.healthassistant.config.SecurityUtils;
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
@RequestMapping("/v1/body-measurements")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Body Measurements", description = "Body dimension tracking endpoints (biceps, waist, chest, etc.)")
class BodyMeasurementsController {

    private final BodyMeasurementsFacade bodyMeasurementsFacade;

    @GetMapping("/latest")
    @Operation(
            summary = "Get latest body measurement",
            description = "Retrieves the most recent body measurement with trend data compared to previous measurements",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Latest body measurement retrieved"),
            @ApiResponse(responseCode = "404", description = "No body measurement data found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<BodyMeasurementLatestResponse> getLatest(
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.debug("Retrieving latest body measurement for device {}", SecurityUtils.maskDeviceId(deviceId));
        return bodyMeasurementsFacade.getLatestMeasurement(deviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get body measurements for date range",
            description = "Retrieves all body measurements within a date range",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Body measurement range summary retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<BodyMeasurementRangeSummaryResponse> getRangeSummary(
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

        log.debug("Retrieving body measurement range summary for device {} from {} to {}",
                SecurityUtils.maskDeviceId(deviceId), startDate, effectiveEndDate);
        BodyMeasurementRangeSummaryResponse summary = bodyMeasurementsFacade.getRangeSummary(deviceId, startDate, effectiveEndDate);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{measurementId}")
    @Operation(
            summary = "Get specific body measurement",
            description = "Retrieves a specific body measurement by its ID",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Body measurement retrieved"),
            @ApiResponse(responseCode = "404", description = "Measurement not found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<BodyMeasurementResponse> getMeasurement(
            @PathVariable String measurementId,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.debug("Retrieving body measurement {} for device {}", measurementId, SecurityUtils.maskDeviceId(deviceId));
        return bodyMeasurementsFacade.getMeasurementById(deviceId, measurementId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Get body measurements summary for dashboard",
            description = "Retrieves latest value for each body part with change vs previous measurement",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary retrieved"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<BodyMeasurementSummaryResponse> getSummary(
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.debug("Retrieving body measurement summary for device {}", SecurityUtils.maskDeviceId(deviceId));
        return ResponseEntity.ok(bodyMeasurementsFacade.getSummary(deviceId));
    }

    @GetMapping("/history/{bodyPart}")
    @Operation(
            summary = "Get history for a specific body part",
            description = "Retrieves measurement history for a specific body part for charting purposes",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Body part history retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid body part or date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<BodyPartHistoryResponse> getBodyPartHistory(
            @PathVariable String bodyPart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        if (from.isAfter(to)) {
            log.warn("Invalid date range: from {} is after to {}", from, to);
            return ResponseEntity.badRequest().build();
        }

        BodyPart part;
        try {
            part = BodyPart.fromValue(bodyPart);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid body part: {}", SecurityUtils.sanitizeForLog(bodyPart));
            return ResponseEntity.badRequest().build();
        }

        LocalDate today = LocalDate.now();
        LocalDate effectiveTo = to.isAfter(today) ? today : to;

        log.debug("Retrieving {} history for device {} from {} to {}",
                SecurityUtils.sanitizeForLog(bodyPart), SecurityUtils.maskDeviceId(deviceId), from, effectiveTo);
        BodyPartHistoryResponse history = bodyMeasurementsFacade.getBodyPartHistory(deviceId, part, from, effectiveTo);
        return ResponseEntity.ok(history);
    }
}
