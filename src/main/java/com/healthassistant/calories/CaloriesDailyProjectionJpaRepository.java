package com.healthassistant.calories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface CaloriesDailyProjectionJpaRepository extends JpaRepository<CaloriesDailyProjectionJpaEntity, Long> {

    Optional<CaloriesDailyProjectionJpaEntity> findByDeviceIdAndDate(String deviceId, LocalDate date);

    List<CaloriesDailyProjectionJpaEntity> findByDeviceIdAndDateBetweenOrderByDateAsc(
            String deviceId,
            LocalDate startDate,
            LocalDate endDate
    );

    void deleteByDeviceId(String deviceId);
}
