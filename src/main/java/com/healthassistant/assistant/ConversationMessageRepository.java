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

    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    @SuppressWarnings("PMD.UseVarargs")
    @Query(value = """
            SELECT DISTINCT ON (conversation_id) *
            FROM conversation_messages
            WHERE conversation_id = ANY(:ids) AND role = 'USER'
            ORDER BY conversation_id, created_at ASC
            """, nativeQuery = true)
    List<ConversationMessage> findFirstUserMessagePerConversation(@Param("ids") UUID[] ids);

    @Query("SELECT m.conversationId, COUNT(m) FROM ConversationMessage m WHERE m.conversationId IN :ids GROUP BY m.conversationId")
    List<Object[]> countByConversationIds(@Param("ids") List<UUID> ids);

    @Modifying
    @Query("DELETE FROM ConversationMessage m WHERE m.conversationId IN (SELECT c.id FROM Conversation c WHERE c.updatedAt < :cutoff)")
    int deleteByConversationUpdatedBefore(@Param("cutoff") Instant cutoff);
}
