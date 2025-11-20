package com.healthassistant.steps;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StepsDailyProjectionJpaRepository extends JpaRepository<StepsDailyProjectionJpaEntity, Long> {

    Optional<StepsDailyProjectionJpaEntity> findByDate(LocalDate date);

    List<StepsDailyProjectionJpaEntity> findByDateBetweenOrderByDateAsc(
            LocalDate startDate,
            LocalDate endDate
    );

}
