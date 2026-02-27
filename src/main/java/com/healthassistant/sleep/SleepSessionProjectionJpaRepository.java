package com.healthassistant.sleep;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface SleepSessionProjectionJpaRepository extends JpaRepository<SleepSessionProjectionJpaEntity, Long> {

    Optional<SleepSessionProjectionJpaEntity> findByEventId(String eventId);

    Optional<SleepSessionProjectionJpaEntity> findByDeviceIdAndSleepStart(String deviceId, Instant sleepStart);

    List<SleepSessionProjectionJpaEntity> findByDeviceIdAndDateOrderBySessionNumberAsc(String deviceId, LocalDate date);

    @Query("SELECT s FROM SleepSessionProjectionJpaEntity s WHERE s.deviceId = :deviceId " +
            "AND s.date = :date AND s.sleepStart < :sleepEnd AND s.sleepEnd > :sleepStart")
    List<SleepSessionProjectionJpaEntity> findOverlappingByDeviceIdAndDate(
            @Param("deviceId") String deviceId,
            @Param("date") LocalDate date,
            @Param("sleepStart") Instant sleepStart,
            @Param("sleepEnd") Instant sleepEnd);

    void deleteByDeviceId(String deviceId);

    void deleteByDeviceIdAndDate(String deviceId, LocalDate date);

    void deleteByEventId(String eventId);
}
