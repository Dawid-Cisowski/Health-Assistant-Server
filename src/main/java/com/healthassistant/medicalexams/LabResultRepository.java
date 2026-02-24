package com.healthassistant.medicalexams;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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
}
