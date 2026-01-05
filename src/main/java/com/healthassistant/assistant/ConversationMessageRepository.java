package com.healthassistant.assistant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    @Modifying
    @Query("DELETE FROM ConversationMessage m WHERE m.conversationId IN (SELECT c.id FROM Conversation c WHERE c.updatedAt < :cutoff)")
    int deleteByConversationUpdatedBefore(@Param("cutoff") Instant cutoff);
}
