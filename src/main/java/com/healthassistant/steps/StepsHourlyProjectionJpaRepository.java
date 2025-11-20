package com.healthassistant.steps;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StepsHourlyProjectionJpaRepository extends JpaRepository<StepsHourlyProjectionJpaEntity, Long> {

    Optional<StepsHourlyProjectionJpaEntity> findByDateAndHour(LocalDate date, Integer hour);

    List<StepsHourlyProjectionJpaEntity> findByDateOrderByHourAsc(LocalDate date);

}
