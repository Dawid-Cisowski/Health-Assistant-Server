package com.healthassistant.repository;

import com.healthassistant.domain.HealthEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for health events
 */
@Repository
public interface HealthEventRepository extends JpaRepository<HealthEvent, Long> {

    /**
     * Find event by idempotency key
     */
    Optional<HealthEvent> findByIdempotencyKey(String idempotencyKey);

    /**
     * Check if event exists by idempotency key
     */
    boolean existsByIdempotencyKey(String idempotencyKey);
}

