package com.healthassistant.mealimport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface MealImportDraftRepository extends JpaRepository<MealImportDraft, UUID> {

    Optional<MealImportDraft> findByIdAndDeviceId(UUID id, String deviceId);

    @Modifying
    @Query("DELETE FROM MealImportDraft d WHERE d.status = :status AND d.expiresAt < :before")
    int deleteByStatusAndExpiresAtBefore(
        @Param("status") MealImportDraft.DraftStatus status,
        @Param("before") Instant before
    );
}
