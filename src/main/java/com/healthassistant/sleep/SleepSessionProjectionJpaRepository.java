package com.healthassistant.sleep;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SleepSessionProjectionJpaRepository extends JpaRepository<SleepSessionProjectionJpaEntity, Long> {

    Optional<SleepSessionProjectionJpaEntity> findByEventId(String eventId);

    List<SleepSessionProjectionJpaEntity> findByDateOrderBySessionNumberAsc(LocalDate date);

    List<SleepSessionProjectionJpaEntity> findByDateBetweenOrderByDateAscSessionNumberAsc(LocalDate startDate, LocalDate endDate);
}
