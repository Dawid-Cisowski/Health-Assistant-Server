package com.healthassistant.assistant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    List<ConversationMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    @Query(value = """
        SELECT * FROM (
            SELECT * FROM conversation_messages
            WHERE conversation_id = :conversationId
            ORDER BY created_at DESC
            LIMIT 20
        ) sub ORDER BY created_at ASC
        """, nativeQuery = true)
    List<ConversationMessage> findLast20ByConversationIdChronological(@Param("conversationId") UUID conversationId);
}
