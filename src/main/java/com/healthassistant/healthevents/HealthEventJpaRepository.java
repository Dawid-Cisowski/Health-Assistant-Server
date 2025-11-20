package com.healthassistant.healthevents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface HealthEventJpaRepository extends JpaRepository<HealthEventJpaEntity, Long> {

    List<HealthEventJpaEntity> findByIdempotencyKeyIn(List<String> idempotencyKeys);

    List<HealthEventJpaEntity> findByOccurredAtBetween(Instant start, Instant end);
}
