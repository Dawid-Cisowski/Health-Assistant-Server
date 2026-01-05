package com.healthassistant.assistant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndDeviceId(UUID id, String deviceId);

    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.updatedAt < :cutoff")
    int deleteByUpdatedAtBefore(@Param("cutoff") Instant cutoff);
}
