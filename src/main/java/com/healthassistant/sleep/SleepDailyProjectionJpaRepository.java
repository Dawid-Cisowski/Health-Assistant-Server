package com.healthassistant.sleep;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface SleepDailyProjectionJpaRepository extends JpaRepository<SleepDailyProjectionJpaEntity, Long> {

    Optional<SleepDailyProjectionJpaEntity> findByDeviceIdAndDate(String deviceId, LocalDate date);

    List<SleepDailyProjectionJpaEntity> findByDeviceIdAndDateBetweenOrderByDateAsc(
            String deviceId, LocalDate startDate, LocalDate endDate);

    void deleteByDeviceId(String deviceId);

    void deleteByDeviceIdAndDate(String deviceId, LocalDate date);
}
