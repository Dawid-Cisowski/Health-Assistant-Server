package com.healthassistant.heartrate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface RestingHeartRateRepository extends JpaRepository<RestingHeartRateProjectionJpaEntity, Long> {

    @Query("SELECT r FROM RestingHeartRateProjectionJpaEntity r WHERE r.eventId = :eventId")
    Optional<RestingHeartRateProjectionJpaEntity> findByEventId(@Param("eventId") String eventId);

    @Query("SELECT r FROM RestingHeartRateProjectionJpaEntity r WHERE r.deviceId = :deviceId AND r.date = :date")
    Optional<RestingHeartRateProjectionJpaEntity> findByDeviceIdAndDate(
            @Param("deviceId") String deviceId, @Param("date") LocalDate date);

    @Query("SELECT r FROM RestingHeartRateProjectionJpaEntity r WHERE r.deviceId = :deviceId AND r.date >= :startDate AND r.date <= :endDate ORDER BY r.date ASC")
    List<RestingHeartRateProjectionJpaEntity> findByDeviceIdAndDateRange(
            @Param("deviceId") String deviceId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Modifying
    @Query("DELETE FROM RestingHeartRateProjectionJpaEntity r WHERE r.deviceId = :deviceId AND r.date = :date")
    void deleteByDeviceIdAndDate(@Param("deviceId") String deviceId, @Param("date") LocalDate date);

    @Modifying
    @Query("DELETE FROM RestingHeartRateProjectionJpaEntity r WHERE r.eventId = :eventId")
    int deleteByEventId(@Param("eventId") String eventId);
}
