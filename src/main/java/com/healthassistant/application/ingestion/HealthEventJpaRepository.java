package com.healthassistant.application.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface HealthEventJpaRepository extends JpaRepository<HealthEventJpaEntity, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
    
    List<HealthEventJpaEntity> findByOccurredAtBetween(Instant start, Instant end);
}

