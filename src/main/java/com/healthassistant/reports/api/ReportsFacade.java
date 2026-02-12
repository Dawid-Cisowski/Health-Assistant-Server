package com.healthassistant.reports.api;

import com.healthassistant.reports.api.dto.HealthReportDetailResponse;
import com.healthassistant.reports.api.dto.HealthReportSummaryResponse;
import com.healthassistant.reports.api.dto.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

public interface ReportsFacade {

    Optional<Long> generateReport(String deviceId, ReportType type, LocalDate periodStart, LocalDate periodEnd);

    Page<HealthReportSummaryResponse> listReports(String deviceId, ReportType type, Pageable pageable);

    Optional<HealthReportDetailResponse> getReport(String deviceId, long reportId);
}
