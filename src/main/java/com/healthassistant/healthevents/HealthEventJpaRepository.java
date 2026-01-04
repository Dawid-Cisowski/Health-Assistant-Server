package com.healthassistant.healthevents;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
interface HealthEventJpaRepository extends JpaRepository<HealthEventJpaEntity, Long> {

    List<HealthEventJpaEntity> findByIdempotencyKeyIn(List<String> idempotencyKeys);

    List<HealthEventJpaEntity> findByOccurredAtBetween(Instant start, Instant end);

    List<HealthEventJpaEntity> findByDeviceIdAndOccurredAtBetween(String deviceId, Instant start, Instant end);

    List<HealthEventJpaEntity> findByDeviceIdAndEventType(String deviceId, String eventType);

    List<HealthEventJpaEntity> findByDeviceId(String deviceId);

    @Query(value = """
        SELECT idempotency_key AS idempotencyKey, event_id AS eventId FROM health_events
        WHERE device_id = :deviceId
        AND event_type = 'SleepSessionRecorded.v1'
        AND payload->>'sleepStart' = :sleepStart
        AND deleted_at IS NULL
        AND superseded_by_event_id IS NULL
        LIMIT 1
        """, nativeQuery = true)
    Optional<SleepInfoProjection> findSleepInfoByDeviceIdAndSleepStart(
            @Param("deviceId") String deviceId,
            @Param("sleepStart") String sleepStart
    );

    void deleteByDeviceId(String deviceId);

    Page<HealthEventJpaEntity> findAllByOrderByIdAsc(Pageable pageable);

    Optional<HealthEventJpaEntity> findByEventId(String eventId);

    @Query("SELECT e FROM HealthEventJpaEntity e WHERE e.deviceId = :deviceId " +
           "AND e.occurredAt BETWEEN :start AND :end " +
           "AND e.deletedAt IS NULL AND e.supersededByEventId IS NULL")
    List<HealthEventJpaEntity> findActiveByDeviceIdAndOccurredAtBetween(
            @Param("deviceId") String deviceId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT e FROM HealthEventJpaEntity e " +
           "WHERE e.deletedAt IS NULL AND e.supersededByEventId IS NULL " +
           "ORDER BY e.id ASC")
    Page<HealthEventJpaEntity> findAllActiveOrderByIdAsc(Pageable pageable);
}
