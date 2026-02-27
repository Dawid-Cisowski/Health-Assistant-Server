package com.healthassistant.medicalexams;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ExaminationRepository extends JpaRepository<Examination, UUID> {

    @Query("SELECT DISTINCT e FROM Examination e LEFT JOIN FETCH e.results LEFT JOIN FETCH e.attachments "
            + "WHERE e.deviceId = :deviceId ORDER BY e.date DESC")
    List<Examination> findAllByDeviceIdWithDetails(String deviceId);

    @Query("SELECT DISTINCT e FROM Examination e LEFT JOIN FETCH e.results LEFT JOIN FETCH e.attachments "
            + "WHERE e.deviceId = :deviceId AND e.date BETWEEN :from AND :to ORDER BY e.date DESC")
    List<Examination> findAllByDeviceIdAndDateBetweenWithDetails(String deviceId, LocalDate from, LocalDate to);

    @Query("SELECT DISTINCT e FROM Examination e LEFT JOIN FETCH e.results LEFT JOIN FETCH e.attachments "
            + "WHERE e.deviceId = :deviceId AND e.examType.code = :examTypeCode ORDER BY e.date DESC")
    List<Examination> findAllByDeviceIdAndExamTypeCodeWithDetails(String deviceId, String examTypeCode);

    @Query("SELECT DISTINCT e FROM Examination e LEFT JOIN FETCH e.results LEFT JOIN FETCH e.attachments "
            + "WHERE e.deviceId = :deviceId AND e.examType.code = :examTypeCode "
            + "AND e.date BETWEEN :from AND :to ORDER BY e.date DESC")
    List<Examination> findAllByDeviceIdAndExamTypeCodeAndDateBetweenWithDetails(
            String deviceId, String examTypeCode, LocalDate from, LocalDate to);

    @Query("SELECT DISTINCT e FROM Examination e LEFT JOIN FETCH e.results LEFT JOIN FETCH e.attachments "
            + "WHERE e.deviceId = :deviceId "
            + "AND e.date >= :from AND e.date <= :to "
            + "AND (LOWER(e.title) LIKE :q ESCAPE '\\' "
            + "     OR EXISTS (SELECT 1 FROM LabResult r WHERE r.examination = e "
            + "                AND (LOWER(r.markerName) LIKE :q ESCAPE '\\' "
            + "                     OR LOWER(r.markerCode) LIKE :q ESCAPE '\\' "
            + "                     OR LOWER(r.category) LIKE :q ESCAPE '\\'))) "
            + "ORDER BY e.date DESC")
    List<Examination> findAllByDeviceIdWithSearch(
            @Param("deviceId") String deviceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("q") String q);

    @Query("SELECT e FROM Examination e LEFT JOIN FETCH e.results LEFT JOIN FETCH e.attachments "
            + "WHERE e.deviceId = :deviceId AND e.id = :id")
    Optional<Examination> findByDeviceIdAndIdWithDetails(String deviceId, UUID id);
}
