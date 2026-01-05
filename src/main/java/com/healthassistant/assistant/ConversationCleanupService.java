package com.healthassistant.assistant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class ConversationCleanupService {

    private static final int RETENTION_DAYS = 90;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldConversations() {
        var cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        log.info("Starting conversation cleanup for conversations older than {} ({} days retention)", cutoff, RETENTION_DAYS);

        var deletedMessages = messageRepository.deleteByConversationUpdatedBefore(cutoff);
        var deletedConversations = conversationRepository.deleteByUpdatedAtBefore(cutoff);

        log.info("Conversation cleanup completed. Deleted {} conversations and {} messages", deletedConversations, deletedMessages);
    }
}
