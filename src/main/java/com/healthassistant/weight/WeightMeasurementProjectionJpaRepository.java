package com.healthassistant.weight;

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
interface WeightMeasurementProjectionJpaRepository extends JpaRepository<WeightMeasurementProjectionJpaEntity, Long> {

    @Query("SELECT w FROM WeightMeasurementProjectionJpaEntity w WHERE w.eventId = :eventId")
    Optional<WeightMeasurementProjectionJpaEntity> findByEventId(@Param("eventId") String eventId);

    @Query("SELECT w FROM WeightMeasurementProjectionJpaEntity w WHERE w.deviceId = :deviceId AND w.measurementId = :measurementId")
    Optional<WeightMeasurementProjectionJpaEntity> findByDeviceIdAndMeasurementId(
            @Param("deviceId") String deviceId, @Param("measurementId") String measurementId);

    @Query("SELECT w FROM WeightMeasurementProjectionJpaEntity w WHERE w.deviceId = :deviceId ORDER BY w.measuredAt DESC LIMIT 1")
    Optional<WeightMeasurementProjectionJpaEntity> findFirstByDeviceIdOrderByMeasuredAtDesc(
            @Param("deviceId") String deviceId);

    @Query("SELECT w FROM WeightMeasurementProjectionJpaEntity w WHERE w.deviceId = :deviceId AND w.date BETWEEN :startDate AND :endDate ORDER BY w.measuredAt ASC")
    List<WeightMeasurementProjectionJpaEntity> findByDeviceIdAndDateBetweenOrderByMeasuredAtAsc(
            @Param("deviceId") String deviceId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT w FROM WeightMeasurementProjectionJpaEntity w WHERE w.deviceId = :deviceId AND w.measuredAt BETWEEN :startTime AND :endTime ORDER BY w.measuredAt ASC")
    List<WeightMeasurementProjectionJpaEntity> findByDeviceIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(
            @Param("deviceId") String deviceId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("SELECT w FROM WeightMeasurementProjectionJpaEntity w WHERE w.deviceId = :deviceId AND w.measuredAt < :before ORDER BY w.measuredAt DESC LIMIT 1")
    Optional<WeightMeasurementProjectionJpaEntity> findFirstByDeviceIdAndMeasuredAtLessThanOrderByMeasuredAtDesc(
            @Param("deviceId") String deviceId, @Param("before") Instant before);

    @Modifying
    @Query("DELETE FROM WeightMeasurementProjectionJpaEntity w WHERE w.deviceId = :deviceId")
    void deleteByDeviceId(@Param("deviceId") String deviceId);

    @Modifying
    @Query("DELETE FROM WeightMeasurementProjectionJpaEntity w WHERE w.deviceId = :deviceId AND w.date = :date")
    void deleteByDeviceIdAndDate(@Param("deviceId") String deviceId, @Param("date") LocalDate date);

    @Modifying
    @Query("DELETE FROM WeightMeasurementProjectionJpaEntity w WHERE w.eventId = :eventId")
    void deleteByEventId(@Param("eventId") String eventId);

    @Query("SELECT COUNT(w) FROM WeightMeasurementProjectionJpaEntity w WHERE w.deviceId = :deviceId AND w.measuredAt BETWEEN :startTime AND :endTime")
    long countByDeviceIdAndMeasuredAtBetween(
            @Param("deviceId") String deviceId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
}
