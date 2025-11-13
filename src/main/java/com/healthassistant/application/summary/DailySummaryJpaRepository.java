package com.healthassistant.application.summary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
interface DailySummaryJpaRepository extends JpaRepository<DailySummaryJpaEntity, Long> {
    Optional<DailySummaryJpaEntity> findByDate(LocalDate date);

    boolean existsByDate(LocalDate date);
}

