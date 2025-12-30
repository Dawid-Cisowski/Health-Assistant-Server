package com.healthassistant.steps;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface StepsDailyProjectionJpaRepository extends JpaRepository<StepsDailyProjectionJpaEntity, Long> {

    Optional<StepsDailyProjectionJpaEntity> findByDeviceIdAndDate(String deviceId, LocalDate date);

    List<StepsDailyProjectionJpaEntity> findByDeviceIdAndDateBetweenOrderByDateAsc(
            String deviceId,
            LocalDate startDate,
            LocalDate endDate
    );

    void deleteByDeviceId(String deviceId);

    void deleteByDeviceIdAndDate(String deviceId, LocalDate date);

}
