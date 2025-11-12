package com.healthassistant.application.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HealthEventJpaRepository extends JpaRepository<HealthEventJpaEntity, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}

