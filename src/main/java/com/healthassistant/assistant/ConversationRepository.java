package com.healthassistant.assistant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndDeviceId(UUID id, String deviceId);

    Page<Conversation> findByDeviceIdOrderByUpdatedAtDesc(String deviceId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.updatedAt < :cutoff")
    int deleteByUpdatedAtBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.deviceId = :deviceId")
    int deleteByDeviceId(@Param("deviceId") String deviceId);
}
