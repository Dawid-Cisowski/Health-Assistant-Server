package com.healthassistant.meals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface MealDailyProjectionJpaRepository extends JpaRepository<MealDailyProjectionJpaEntity, Long> {

    Optional<MealDailyProjectionJpaEntity> findByDeviceIdAndDate(String deviceId, LocalDate date);

    List<MealDailyProjectionJpaEntity> findByDeviceIdAndDateBetweenOrderByDateAsc(
            String deviceId, LocalDate startDate, LocalDate endDate);
}
