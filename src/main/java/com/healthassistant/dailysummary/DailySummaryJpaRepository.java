package com.healthassistant.dailysummary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
interface DailySummaryJpaRepository extends JpaRepository<DailySummaryJpaEntity, Long> {
    Optional<DailySummaryJpaEntity> findByDeviceIdAndDate(String deviceId, LocalDate date);

    List<DailySummaryJpaEntity> findByDeviceIdAndDateBetweenOrderByDateAsc(
            String deviceId, LocalDate startDate, LocalDate endDate);

    @Modifying
    @Transactional
    @Query("UPDATE DailySummaryJpaEntity d SET d.lastEventAt = :timestamp WHERE d.deviceId = :deviceId AND d.date IN :dates")
    void updateLastEventAtForDates(
            @Param("deviceId") String deviceId,
            @Param("dates") Set<LocalDate> dates,
            @Param("timestamp") Instant timestamp);

    void deleteByDeviceId(String deviceId);
}
