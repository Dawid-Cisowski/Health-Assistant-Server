package com.healthassistant.medicalexams;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface LabResultRepository extends JpaRepository<LabResult, UUID> {

    List<LabResult> findByDeviceIdAndMarkerCodeAndDateBetweenOrderByDateAsc(
            String deviceId, String markerCode, LocalDate from, LocalDate to);

    Optional<LabResult> findByIdAndExaminationId(UUID id, UUID examinationId);

    @Query("SELECT r FROM LabResult r JOIN FETCH r.examination e "
            + "WHERE r.deviceId = :deviceId AND r.markerCode = :markerCode "
            + "AND r.date BETWEEN :from AND :to ORDER BY r.date ASC")
    List<LabResult> findTrendData(String deviceId, String markerCode, LocalDate from, LocalDate to);

    @Query(value = "SELECT DISTINCT ON (r.marker_code) r.* FROM lab_results r "
            + "WHERE r.device_id = :deviceId AND r.marker_code IN (:markerCodes) "
            + "ORDER BY r.marker_code, r.date DESC, r.created_at DESC",
            nativeQuery = true)
    List<LabResult> findLatestResultsByMarkerCodes(@Param("deviceId") String deviceId,
                                                    @Param("markerCodes") Collection<String> markerCodes);
}
