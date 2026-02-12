package com.healthassistant.reports;

import com.healthassistant.reports.api.ReportsFacade;
import com.healthassistant.reports.api.dto.HealthReportDetailResponse;
import com.healthassistant.reports.api.dto.HealthReportSummaryResponse;
import com.healthassistant.reports.api.dto.ReportType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.healthassistant.config.SecurityUtils.maskDeviceId;

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reports", description = "Health reports with goals, comparisons and AI summaries")
@SecurityRequirement(name = "HmacHeaderAuth")
class ReportsController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReportsFacade reportsFacade;

    @GetMapping
    @Operation(summary = "List reports with pagination")
    ResponseEntity<Page<HealthReportSummaryResponse>> listReports(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestParam ReportType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("List reports: device={}, type={}, page={}, size={}", maskDeviceId(deviceId), type, page, size);

        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        Page<HealthReportSummaryResponse> reports = reportsFacade.listReports(
                deviceId, type, PageRequest.of(page, effectiveSize));

        return ResponseEntity.ok(reports);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Get full report details")
    ResponseEntity<HealthReportDetailResponse> getReport(
            @RequestHeader("X-Device-Id") String deviceId,
            @PathVariable long reportId) {

        log.info("Get report: device={}, reportId={}", maskDeviceId(deviceId), reportId);

        return reportsFacade.getReport(deviceId, reportId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
