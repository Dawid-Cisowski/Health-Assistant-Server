package com.healthassistant.application.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GoogleFitSyncStateRepository extends JpaRepository<GoogleFitSyncState, Long> {
    Optional<GoogleFitSyncState> findByUserId(String userId);
}

