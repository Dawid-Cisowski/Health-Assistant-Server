package com.healthassistant.sleep;

import org.springframework.data.jpa.repository.JpaRepository;
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

    void deleteByDeviceId(String deviceId);

    void deleteByDeviceIdAndDate(String deviceId, LocalDate date);

    void deleteByEventId(String eventId);
}
