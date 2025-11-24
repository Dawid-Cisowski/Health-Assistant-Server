package com.healthassistant.assistant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    List<ConversationMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
