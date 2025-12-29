package com.healthassistant.calories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface CaloriesHourlyProjectionJpaRepository extends JpaRepository<CaloriesHourlyProjectionJpaEntity, Long> {

    Optional<CaloriesHourlyProjectionJpaEntity> findByDeviceIdAndDateAndHour(String deviceId, LocalDate date, Integer hour);

    List<CaloriesHourlyProjectionJpaEntity> findByDeviceIdAndDateOrderByHourAsc(String deviceId, LocalDate date);

    void deleteByDeviceId(String deviceId);
}
