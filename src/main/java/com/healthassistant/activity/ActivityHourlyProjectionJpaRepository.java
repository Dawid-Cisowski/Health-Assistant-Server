package com.healthassistant.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface ActivityHourlyProjectionJpaRepository extends JpaRepository<ActivityHourlyProjectionJpaEntity, Long> {

    Optional<ActivityHourlyProjectionJpaEntity> findByDeviceIdAndDateAndHour(String deviceId, LocalDate date, Integer hour);

    List<ActivityHourlyProjectionJpaEntity> findByDeviceIdAndDateOrderByHourAsc(String deviceId, LocalDate date);
}
