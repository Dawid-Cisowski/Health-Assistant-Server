package com.healthassistant.bodymeasurements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface BodyMeasurementProjectionJpaRepository extends JpaRepository<BodyMeasurementProjectionJpaEntity, Long> {

    @Query("SELECT b FROM BodyMeasurementProjectionJpaEntity b WHERE b.eventId = :eventId")
    Optional<BodyMeasurementProjectionJpaEntity> findByEventId(@Param("eventId") String eventId);

    @Query("SELECT b FROM BodyMeasurementProjectionJpaEntity b WHERE b.deviceId = :deviceId AND b.measurementId = :measurementId")
    Optional<BodyMeasurementProjectionJpaEntity> findByDeviceIdAndMeasurementId(
            @Param("deviceId") String deviceId, @Param("measurementId") String measurementId);

    @Query("SELECT b FROM BodyMeasurementProjectionJpaEntity b WHERE b.deviceId = :deviceId ORDER BY b.measuredAt DESC LIMIT 1")
    Optional<BodyMeasurementProjectionJpaEntity> findFirstByDeviceIdOrderByMeasuredAtDesc(
            @Param("deviceId") String deviceId);

    @Query("SELECT b FROM BodyMeasurementProjectionJpaEntity b WHERE b.deviceId = :deviceId ORDER BY b.measuredAt DESC")
    List<BodyMeasurementProjectionJpaEntity> findByDeviceIdOrderByMeasuredAtDesc(
            @Param("deviceId") String deviceId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT b FROM BodyMeasurementProjectionJpaEntity b WHERE b.deviceId = :deviceId AND b.date BETWEEN :startDate AND :endDate ORDER BY b.measuredAt ASC")
    List<BodyMeasurementProjectionJpaEntity> findByDeviceIdAndDateBetweenOrderByMeasuredAtAsc(
            @Param("deviceId") String deviceId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT b FROM BodyMeasurementProjectionJpaEntity b WHERE b.deviceId = :deviceId AND b.measuredAt BETWEEN :startTime AND :endTime ORDER BY b.measuredAt ASC")
    List<BodyMeasurementProjectionJpaEntity> findByDeviceIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            @Param("deviceId") String deviceId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("SELECT b FROM BodyMeasurementProjectionJpaEntity b WHERE b.deviceId = :deviceId AND b.measuredAt < :before ORDER BY b.measuredAt DESC LIMIT 1")
    Optional<BodyMeasurementProjectionJpaEntity> findFirstByDeviceIdAndMeasuredAtLessThanOrderByMeasuredAtDesc(
            @Param("deviceId") String deviceId, @Param("before") Instant before);

    @Modifying
    @Query("DELETE FROM BodyMeasurementProjectionJpaEntity b WHERE b.deviceId = :deviceId")
    void deleteByDeviceId(@Param("deviceId") String deviceId);

    @Modifying
    @Query("DELETE FROM BodyMeasurementProjectionJpaEntity b WHERE b.deviceId = :deviceId AND b.date = :date")
    void deleteByDeviceIdAndDate(@Param("deviceId") String deviceId, @Param("date") LocalDate date);

    @Modifying
    @Query("DELETE FROM BodyMeasurementProjectionJpaEntity b WHERE b.eventId = :eventId")
    void deleteByEventId(@Param("eventId") String eventId);

    @Query("SELECT COUNT(b) FROM BodyMeasurementProjectionJpaEntity b WHERE b.deviceId = :deviceId AND b.measuredAt BETWEEN :startTime AND :endTime")
    long countByDeviceIdAndMeasuredAtBetween(
            @Param("deviceId") String deviceId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
}
