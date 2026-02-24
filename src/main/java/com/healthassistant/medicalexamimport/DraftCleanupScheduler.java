package com.healthassistant.medicalexamimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component("medicalExamDraftCleanupScheduler")
@RequiredArgsConstructor
@Slf4j
class DraftCleanupScheduler {

    private final MedicalExamImportDraftRepository draftRepository;

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void cleanupExpiredDrafts() {
        int deleted = draftRepository.deleteByStatusAndExpiresAtBefore(
                MedicalExamImportDraft.DraftStatus.PENDING, Instant.now()
        );
        if (deleted > 0) {
            log.info("Cleaned up {} expired medical exam import drafts", deleted);
        }
    }
}
