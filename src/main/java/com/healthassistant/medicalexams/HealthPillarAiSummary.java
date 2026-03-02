package com.healthassistant.medicalexams;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.time.Instant;

@Entity
@Table(name = "health_pillar_ai_summaries")
@IdClass(HealthPillarAiSummaryId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class HealthPillarAiSummary {

    @Id
    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Id
    @Column(name = "pillar_code", nullable = false)
    private String pillarCode;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "ai_insight", columnDefinition = "TEXT")
    private String aiInsight;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "lab_results_updated_at")
    private Instant labResultsUpdatedAt;

    static HealthPillarAiSummary create(String deviceId, String pillarCode) {
        var entity = new HealthPillarAiSummary();
        entity.deviceId = deviceId;
        entity.pillarCode = pillarCode;
        return entity;
    }

    void cacheInsight(String insight) {
        this.aiInsight = insight;
        this.generatedAt = Instant.now();
    }

    void markLabResultsChanged(Instant changedAt) {
        this.labResultsUpdatedAt = changedAt;
    }

    boolean isCacheValid() {
        if (aiInsight == null || generatedAt == null) {
            return false;
        }
        if (labResultsUpdatedAt == null) {
            return true;
        }
        return !labResultsUpdatedAt.isAfter(generatedAt);
    }

    String getAiInsight() {
        return aiInsight;
    }

    String getDeviceId() {
        return deviceId;
    }

    String getPillarCode() {
        return pillarCode;
    }
}
