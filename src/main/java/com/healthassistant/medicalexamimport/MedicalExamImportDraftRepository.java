package com.healthassistant.medicalexamimport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MedicalExamImportDraftRepository extends JpaRepository<MedicalExamImportDraft, UUID> {

    Optional<MedicalExamImportDraft> findByIdAndDeviceId(UUID id, String deviceId);

    List<MedicalExamImportDraft> findAllByStatusAndExpiresAtBefore(
            MedicalExamImportDraft.DraftStatus status, Instant before);

    @Modifying
    @Transactional
    @Query("DELETE FROM MedicalExamImportDraft d WHERE d.id IN :ids AND d.status = :status")
    int deleteByIdInAndStatus(
            @Param("ids") List<UUID> ids,
            @Param("status") MedicalExamImportDraft.DraftStatus status
    );
}
