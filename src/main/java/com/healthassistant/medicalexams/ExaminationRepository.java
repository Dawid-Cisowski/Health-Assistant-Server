package com.healthassistant.medicalexams;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    @Query("SELECT e FROM Examination e LEFT JOIN FETCH e.results LEFT JOIN FETCH e.attachments "
            + "WHERE e.deviceId = :deviceId AND e.id = :id")
    Optional<Examination> findByDeviceIdAndIdWithDetails(String deviceId, UUID id);
}
