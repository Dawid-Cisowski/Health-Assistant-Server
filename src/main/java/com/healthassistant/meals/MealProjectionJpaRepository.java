package com.healthassistant.meals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MealProjectionJpaRepository extends JpaRepository<MealProjectionJpaEntity, Long> {

    Optional<MealProjectionJpaEntity> findByEventId(String eventId);

    List<MealProjectionJpaEntity> findByDateOrderByMealNumberAsc(LocalDate date);

    List<MealProjectionJpaEntity> findByDateBetweenOrderByDateAscMealNumberAsc(LocalDate startDate, LocalDate endDate);
}
