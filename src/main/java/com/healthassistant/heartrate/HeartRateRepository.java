package com.healthassistant.heartrate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
interface HeartRateRepository extends JpaRepository<HeartRateProjectionJpaEntity, Long> {

    @Query("SELECT h FROM HeartRateProjectionJpaEntity h WHERE h.eventId = :eventId")
    Optional<HeartRateProjectionJpaEntity> findByEventId(@Param("eventId") String eventId);

    @Query("SELECT h FROM HeartRateProjectionJpaEntity h WHERE h.deviceId = :deviceId AND h.measuredAt >= :startTime AND h.measuredAt < :endTime ORDER BY h.measuredAt ASC")
    List<HeartRateProjectionJpaEntity> findByDeviceIdAndTimeRange(
            @Param("deviceId") String deviceId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    @Modifying
    @Query("DELETE FROM HeartRateProjectionJpaEntity h WHERE h.deviceId = :deviceId AND h.measuredAt >= :startTime AND h.measuredAt < :endTime")
    void deleteByDeviceIdAndTimeRange(
            @Param("deviceId") String deviceId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    @Modifying
    @Query("DELETE FROM HeartRateProjectionJpaEntity h WHERE h.eventId = :eventId")
    int deleteByEventId(@Param("eventId") String eventId);
}
