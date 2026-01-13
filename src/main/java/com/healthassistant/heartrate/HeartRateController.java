package com.healthassistant.heartrate;

import com.healthassistant.heartrate.api.HeartRateFacade;
import com.healthassistant.heartrate.api.dto.HeartRateRangeResponse;
import com.healthassistant.heartrate.api.dto.RestingHeartRateResponse;
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
import java.util.List;

@RestController
@RequestMapping("/v1/heart-rate")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Heart Rate", description = "Heart rate tracking endpoints")
class HeartRateController {

    private final HeartRateFacade heartRateFacade;

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) return "***";
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get heart rate data for date range",
            description = "Retrieves all heart rate data points within a date range. Use for building charts.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Heart rate data retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<HeartRateRangeResponse> getRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        log.info("Retrieving HR range for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);
        HeartRateRangeResponse response = heartRateFacade.getRange(deviceId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/resting/range")
    @Operation(
            summary = "Get resting heart rate data for date range",
            description = "Retrieves daily resting heart rate values within a date range",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resting heart rate data retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<List<RestingHeartRateResponse>> getRestingRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        log.info("Retrieving resting HR range for device {} from {} to {}", maskDeviceId(deviceId), startDate, endDate);
        List<RestingHeartRateResponse> response = heartRateFacade.getRestingRange(deviceId, startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
