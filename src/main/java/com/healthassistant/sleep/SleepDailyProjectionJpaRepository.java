package com.healthassistant.sleep;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
interface SleepDailyProjectionJpaRepository extends JpaRepository<SleepDailyProjectionJpaEntity, Long> {

    Optional<SleepDailyProjectionJpaEntity> findByDate(LocalDate date);

    List<SleepDailyProjectionJpaEntity> findByDateBetweenOrderByDateAsc(LocalDate startDate, LocalDate endDate);
}
