package com.healthassistant.mealimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component("mealImportDraftCleanupScheduler")
@RequiredArgsConstructor
@Slf4j
class DraftCleanupScheduler {

    private final MealImportDraftRepository draftRepository;
    private final MealImportJobRepository jobRepository;

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredDrafts() {
        var now = Instant.now();
        int deletedDrafts = draftRepository.deleteByStatusAndExpiresAtBefore(
            MealImportDraft.DraftStatus.PENDING, now
        );
        if (deletedDrafts > 0) {
            log.info("Cleaned up {} expired meal import drafts", deletedDrafts);
        }

        int deletedJobs = jobRepository.deleteByStatusInAndExpiresAtBefore(
            List.of(MealImportJobStatus.DONE, MealImportJobStatus.FAILED,
                    MealImportJobStatus.PROCESSING, MealImportJobStatus.PENDING), now
        );
        if (deletedJobs > 0) {
            log.info("Cleaned up {} expired meal import jobs", deletedJobs);
        }
    }
}
