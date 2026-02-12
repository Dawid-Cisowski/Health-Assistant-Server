package com.healthassistant.reports;

import tools.jackson.databind.ObjectMapper;
import com.healthassistant.reports.api.ReportsFacade;
import com.healthassistant.reports.api.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static com.healthassistant.config.SecurityUtils.maskDeviceId;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
class ReportsService implements ReportsFacade {

    private final ReportGenerationService generationService;
    private final HealthReportJpaRepository reportRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Optional<Long> generateReport(String deviceId, ReportType type, LocalDate periodStart, LocalDate periodEnd) {
        log.info("Generate report requested: device={}, type={}, period={} to {}",
                maskDeviceId(deviceId), type, periodStart, periodEnd);

        return switch (type) {
            case DAILY -> generationService.generateDailyReport(deviceId, periodStart);
            case WEEKLY -> generationService.generateWeeklyReport(deviceId, periodStart, periodEnd);
            case MONTHLY -> generationService.generateMonthlyReport(deviceId, periodStart, periodEnd);
        };
    }

    @Override
    public Page<HealthReportSummaryResponse> listReports(String deviceId, ReportType type, Pageable pageable) {
        return reportRepository
                .findByDeviceIdAndReportTypeOrderByGeneratedAtDesc(deviceId, type.name(), pageable)
                .map(this::toSummaryResponse);
    }

    @Override
    public Optional<HealthReportDetailResponse> getReport(String deviceId, long reportId) {
        return reportRepository.findById(reportId)
                .filter(entity -> entity.belongsTo(deviceId))
                .map(this::toDetailResponse);
    }

    private HealthReportSummaryResponse toSummaryResponse(HealthReportJpaEntity entity) {
        return new HealthReportSummaryResponse(
                entity.getId(),
                ReportType.valueOf(entity.getReportType()),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getShortSummary(),
                entity.getGoalsAchieved(),
                entity.getGoalsTotal(),
                entity.getGeneratedAt()
        );
    }

    private HealthReportDetailResponse toDetailResponse(HealthReportJpaEntity entity) {
        GoalEvaluation goals = entity.getGoalsJson() != null
                ? objectMapper.convertValue(entity.getGoalsJson(), GoalEvaluation.class)
                : null;
        PeriodComparison comparison = entity.getComparisonJson() != null
                ? objectMapper.convertValue(entity.getComparisonJson(), PeriodComparison.class)
                : null;

        ReportType type = ReportType.valueOf(entity.getReportType());

        ReportDataSnapshot dailyData = null;
        RangeReportDataSnapshot rangeData = null;
        if (entity.getDataJson() != null) {
            if (type == ReportType.DAILY) {
                dailyData = objectMapper.convertValue(entity.getDataJson(), ReportDataSnapshot.class);
            } else {
                rangeData = objectMapper.convertValue(entity.getDataJson(), RangeReportDataSnapshot.class);
            }
        }

        return new HealthReportDetailResponse(
                entity.getId(),
                type,
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getGeneratedAt(),
                entity.getShortSummary(),
                entity.getAiSummary(),
                goals,
                comparison,
                dailyData,
                rangeData
        );
    }
}
