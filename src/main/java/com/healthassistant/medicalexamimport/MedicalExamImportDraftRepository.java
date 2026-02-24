package com.healthassistant.medicalexamimport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface MedicalExamImportDraftRepository extends JpaRepository<MedicalExamImportDraft, UUID> {

    Optional<MedicalExamImportDraft> findByIdAndDeviceId(UUID id, String deviceId);

    @Modifying
    @Query("DELETE FROM MedicalExamImportDraft d WHERE d.status = :status AND d.expiresAt < :before")
    int deleteByStatusAndExpiresAtBefore(
            @Param("status") MedicalExamImportDraft.DraftStatus status,
            @Param("before") Instant before
    );
}
