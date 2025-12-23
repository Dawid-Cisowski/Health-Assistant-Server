package com.healthassistant.mealimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
class DraftCleanupScheduler {

    private final MealImportDraftRepository draftRepository;

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredDrafts() {
        var now = Instant.now();
        int deleted = draftRepository.deleteByStatusAndExpiresAtBefore(
            MealImportDraft.DraftStatus.PENDING, now
        );
        if (deleted > 0) {
            log.info("Cleaned up {} expired meal import drafts", deleted);
        }
    }
}
