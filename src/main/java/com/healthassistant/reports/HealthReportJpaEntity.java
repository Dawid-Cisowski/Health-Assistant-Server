package com.healthassistant.reports;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "health_reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_health_reports_device_type_period",
                        columnNames = {"device_id", "report_type", "period_start", "period_end"}
                )
        },
        indexes = {
                @Index(name = "idx_health_reports_device_type", columnList = "device_id, report_type"),
                @Index(name = "idx_health_reports_device_type_generated", columnList = "device_id, report_type, generated_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class HealthReportJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "short_summary", length = 500)
    private String shortSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "goals_json", columnDefinition = "jsonb")
    private Map<String, Object> goalsJson;

    @Column(name = "goals_achieved", nullable = false)
    private int goalsAchieved;

    @Column(name = "goals_total", nullable = false)
    private int goalsTotal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comparison_json", columnDefinition = "jsonb")
    private Map<String, Object> comparisonJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_json", columnDefinition = "jsonb")
    private Map<String, Object> dataJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static HealthReportJpaEntity create(String deviceId, String reportType, LocalDate periodStart, LocalDate periodEnd) {
        var entity = new HealthReportJpaEntity();
        entity.deviceId = deviceId;
        entity.reportType = reportType;
        entity.periodStart = periodStart;
        entity.periodEnd = periodEnd;
        entity.generatedAt = Instant.now();
        return entity;
    }

    void populateReport(String aiSummary, String shortSummary,
                        Map<String, Object> goalsJson, int goalsAchieved, int goalsTotal,
                        Map<String, Object> comparisonJson, Map<String, Object> dataJson) {
        this.aiSummary = aiSummary;
        this.shortSummary = shortSummary;
        this.goalsJson = goalsJson;
        this.goalsAchieved = goalsAchieved;
        this.goalsTotal = goalsTotal;
        this.comparisonJson = comparisonJson;
        this.dataJson = dataJson;
        this.generatedAt = Instant.now();
    }

    boolean belongsTo(String deviceId) {
        return this.deviceId.equals(deviceId);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }
}
