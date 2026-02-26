package com.healthassistant.medicalexamimport;

import com.healthassistant.medicalexams.api.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component("medicalExamDraftCleanupScheduler")
@RequiredArgsConstructor
@Slf4j
class DraftCleanupScheduler {

    private final MedicalExamImportDraftRepository draftRepository;
    private final FileStorageService fileStorageService;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredDrafts() {
        var now = Instant.now();
        var expired = draftRepository.findAllByStatusAndExpiresAtBefore(
                MedicalExamImportDraft.DraftStatus.PENDING, now);

        if (expired.isEmpty()) {
            log.debug("No expired medical exam import drafts to clean up");
            return;
        }

        List<UUID> expiredIds = expired.stream()
                .map(MedicalExamImportDraft::getId)
                .toList();

        List<String> storageKeys = expired.stream()
                .filter(d -> d.getStoredFiles() != null)
                .flatMap(d -> d.getStoredFiles().stream())
                .map(MedicalExamImportDraft.StoredFile::storageKey)
                .filter(key -> key != null)
                .toList();

        storageKeys.forEach(fileStorageService::delete);

        int deleted = draftRepository.deleteByIdInAndStatus(
                expiredIds, MedicalExamImportDraft.DraftStatus.PENDING);

        log.info("Cleaned up {} expired medical exam import drafts ({} stored files removal attempted)",
                deleted, storageKeys.size());
    }
}
