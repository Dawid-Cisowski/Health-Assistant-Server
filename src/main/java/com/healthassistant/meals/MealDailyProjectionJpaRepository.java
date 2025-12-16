package com.healthassistant.meals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MealDailyProjectionJpaRepository extends JpaRepository<MealDailyProjectionJpaEntity, Long> {

    Optional<MealDailyProjectionJpaEntity> findByDate(LocalDate date);

    List<MealDailyProjectionJpaEntity> findByDateBetweenOrderByDateAsc(LocalDate startDate, LocalDate endDate);
}
