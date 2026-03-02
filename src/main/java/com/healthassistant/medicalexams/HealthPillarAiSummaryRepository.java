package com.healthassistant.medicalexams;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface HealthPillarAiSummaryRepository extends JpaRepository<HealthPillarAiSummary, HealthPillarAiSummaryId> {

    List<HealthPillarAiSummary> findAllByDeviceId(String deviceId);

    Optional<HealthPillarAiSummary> findByDeviceIdAndPillarCode(String deviceId, String pillarCode);

    @Modifying
    @Query(value = """
            INSERT INTO health_pillar_ai_summaries (device_id, pillar_code, lab_results_updated_at, version)
            VALUES (:deviceId, :pillarCode, :changedAt, 0)
            ON CONFLICT (device_id, pillar_code)
            DO UPDATE SET lab_results_updated_at = EXCLUDED.lab_results_updated_at,
                          version = health_pillar_ai_summaries.version + 1
            """, nativeQuery = true)
    void upsertLabResultsUpdatedAt(@Param("deviceId") String deviceId,
                                   @Param("pillarCode") String pillarCode,
                                   @Param("changedAt") Instant changedAt);
}
