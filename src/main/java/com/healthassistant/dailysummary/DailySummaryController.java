package com.healthassistant.dailysummary;

import com.healthassistant.dailysummary.api.DailySummaryFacade;
import com.healthassistant.dailysummary.api.dto.AiDailySummaryResponse;
import com.healthassistant.dailysummary.api.dto.AiHealthReportResponse;
import com.healthassistant.dailysummary.api.dto.DailySummaryRangeSummaryResponse;
import com.healthassistant.dailysummary.api.dto.DailySummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@RestController
@RequestMapping("/v1/daily-summaries")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Daily Summaries", description = "Daily health summaries aggregated from events")
class DailySummaryController {

    private static final int MAX_RANGE_DAYS = 365;

    private final DailySummaryFacade dailySummaryFacade;
    private final Optional<AiDailySummaryService> aiDailySummaryService;

    @GetMapping("/{date}")
    @Operation(
            summary = "Get daily summary for a specific date",
            description = "Retrieves the daily summary for the specified date if it exists. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary found"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "404", description = "Summary not found for the specified date")
    })
    ResponseEntity<DailySummaryResponse> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Retrieving daily summary for device {} and date: {}", maskDeviceId(deviceId), date);

        LocalDate effectiveDate = date.isAfter(LocalDate.now()) ? LocalDate.now() : date;

        return dailySummaryFacade.getDailySummary(deviceId, effectiveDate)
                .map(summary -> ResponseEntity.ok(DailySummaryMapper.INSTANCE.toResponse(summary)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/range")
    @Operation(
            summary = "Get daily summaries range",
            description = "Retrieves aggregated daily summaries for a date range (week, month, etc.). Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Range summary retrieved"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed")
    })
    ResponseEntity<DailySummaryRangeSummaryResponse> getRangeSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > MAX_RANGE_DAYS) {
            log.warn("Date range exceeds maximum {} days: {} to {} ({} days)", MAX_RANGE_DAYS, startDate, endDate, daysBetween);
            return ResponseEntity.badRequest().build();
        }

        LocalDate today = LocalDate.now();
        LocalDate effectiveEndDate = endDate.isAfter(today) ? today : endDate;

        log.info("Retrieving daily summaries range for device {} from {} to {}", maskDeviceId(deviceId), startDate, effectiveEndDate);
        DailySummaryRangeSummaryResponse summary = dailySummaryFacade.getRangeSummary(deviceId, startDate, effectiveEndDate);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{date}/ai-text")
    @Operation(
            summary = "Get AI-generated daily summary text",
            description = "Returns a short (max 3 sentences), casual Polish text summarizing the day's health data. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Summary generated successfully"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "503", description = "AI service unavailable")
    })
    ResponseEntity<AiDailySummaryResponse> getAiSummary(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Generating AI summary for device {} date: {}", maskDeviceId(deviceId), date);

        if (aiDailySummaryService.isEmpty()) {
            log.warn("AI service is not available");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            AiDailySummaryResponse response = aiDailySummaryService.get().generateSummary(deviceId, date);
            return ResponseEntity.ok(response);
        } catch (AiSummaryGenerationException e) {
            log.error("AI summary generation failed for device {} date: {}", maskDeviceId(deviceId), date, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @GetMapping("/{date}/ai-report")
    @Operation(
            summary = "Get AI-generated detailed daily health report",
            description = "Returns a detailed AI-generated health report in Markdown format for the specified date. Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report generated successfully"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "503", description = "AI service unavailable")
    })
    ResponseEntity<AiHealthReportResponse> getDailyAiReport(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        log.info("Generating AI daily report for device {} date: {}", maskDeviceId(deviceId), date);

        LocalDate effectiveDate = date.isAfter(LocalDate.now()) ? LocalDate.now() : date;

        try {
            AiHealthReportResponse response = dailySummaryFacade.generateDailyReport(deviceId, effectiveDate);
            return ResponseEntity.ok(response);
        } catch (AiSummaryGenerationException e) {
            log.error("AI report generation failed for device {} date: {}", maskDeviceId(deviceId), effectiveDate, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @GetMapping("/range/ai-report")
    @Operation(
            summary = "Get AI-generated detailed health report for a date range",
            description = "Returns a detailed AI-generated health report in Markdown format for the specified date range (week, month). Requires HMAC authentication.",
            security = @SecurityRequirement(name = "HmacHeaderAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "HMAC authentication failed"),
            @ApiResponse(responseCode = "503", description = "AI service unavailable")
    })
    ResponseEntity<AiHealthReportResponse> getRangeAiReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        if (startDate.isAfter(endDate)) {
            log.warn("Invalid date range for AI report: start {} is after end {}", startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > MAX_RANGE_DAYS) {
            log.warn("Date range exceeds maximum {} days for AI report: {} to {}", MAX_RANGE_DAYS, startDate, endDate);
            return ResponseEntity.badRequest().build();
        }

        LocalDate today = LocalDate.now();
        LocalDate effectiveEndDate = endDate.isAfter(today) ? today : endDate;

        log.info("Generating AI range report for device {} from {} to {}", maskDeviceId(deviceId), startDate, effectiveEndDate);

        try {
            AiHealthReportResponse response = dailySummaryFacade.generateRangeReport(deviceId, startDate, effectiveEndDate);
            return ResponseEntity.ok(response);
        } catch (AiSummaryGenerationException e) {
            log.error("AI range report generation failed for device {} range {} to {}", maskDeviceId(deviceId), startDate, effectiveEndDate, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "***" + deviceId.substring(deviceId.length() - 4);
    }
}
