package com.healthassistant.audit;

import com.healthassistant.audit.api.AuditFacade;
import com.healthassistant.audit.api.dto.AuditTimelineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/v1/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit", description = "Timeline of health events grouped by day for audit/UI display")
class AuditController {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_RANGE_DAYS = 90;

    private final AuditFacade auditFacade;

    @GetMapping("/health-events")
    @Operation(
            summary = "Get audit timeline of health events",
            description = "Returns health events for the calling device grouped by calendar day "
                    + "(Europe/Warsaw). Defaults to the last 7 days when no range is provided.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Timeline built"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<AuditTimelineResponse> getHealthEventsTimeline(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate effectiveStart = startDate != null
                ? startDate
                : effectiveEnd.minusDays(DEFAULT_RANGE_DAYS - 1L);

        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new ResponseStatusException(BAD_REQUEST, "startDate must be on or before endDate");
        }

        long days = ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Date range exceeds maximum of " + MAX_RANGE_DAYS + " days");
        }

        log.info("Building audit timeline for device {} from {} to {}",
                maskDeviceId(deviceId), effectiveStart, effectiveEnd);

        AuditTimelineResponse response = auditFacade.buildTimeline(deviceId, effectiveStart, effectiveEnd);
        return ResponseEntity.ok(response);
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
