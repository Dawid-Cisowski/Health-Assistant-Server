package com.healthassistant.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RoutineRepository extends JpaRepository<RoutineEntity, UUID> {

    List<RoutineEntity> findByDeviceIdOrderByCreatedAtDesc(String deviceId);

    @Query("SELECT r FROM RoutineEntity r LEFT JOIN FETCH r.exercises WHERE r.id = :id AND r.deviceId = :deviceId")
    Optional<RoutineEntity> findByIdAndDeviceIdWithExercises(@Param("id") UUID id, @Param("deviceId") String deviceId);

    void deleteByIdAndDeviceId(UUID id, String deviceId);

    boolean existsByIdAndDeviceId(UUID id, String deviceId);
}
