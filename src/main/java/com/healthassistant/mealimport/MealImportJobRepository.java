package com.healthassistant.mealimport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MealImportJobRepository extends JpaRepository<MealImportJob, UUID> {

    Optional<MealImportJob> findByIdAndDeviceId(UUID id, String deviceId);

    @Modifying
    @Query("DELETE FROM MealImportJob j WHERE j.status IN :statuses AND j.expiresAt < :before")
    int deleteByStatusInAndExpiresAtBefore(
        @Param("statuses") List<MealImportJobStatus> statuses,
        @Param("before") Instant before
    );
}
