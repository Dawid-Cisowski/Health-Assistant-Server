package com.healthassistant.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface ActivityDailyProjectionJpaRepository extends JpaRepository<ActivityDailyProjectionJpaEntity, Long> {

    Optional<ActivityDailyProjectionJpaEntity> findByDeviceIdAndDate(String deviceId, LocalDate date);

    List<ActivityDailyProjectionJpaEntity> findByDeviceIdAndDateBetweenOrderByDateAsc(
            String deviceId,
            LocalDate startDate,
            LocalDate endDate
    );

    void deleteByDeviceId(String deviceId);

    void deleteByDeviceIdAndDate(String deviceId, LocalDate date);
}
