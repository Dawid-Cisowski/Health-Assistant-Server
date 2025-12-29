package com.healthassistant.meals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface MealProjectionJpaRepository extends JpaRepository<MealProjectionJpaEntity, Long> {

    Optional<MealProjectionJpaEntity> findByEventId(String eventId);

    List<MealProjectionJpaEntity> findByDeviceIdAndDateOrderByMealNumberAsc(String deviceId, LocalDate date);

    List<MealProjectionJpaEntity> findByDeviceIdAndDateBetweenOrderByDateAscMealNumberAsc(
            String deviceId, LocalDate startDate, LocalDate endDate);
}
