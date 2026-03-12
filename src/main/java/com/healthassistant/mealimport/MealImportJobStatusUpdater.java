package com.healthassistant.mealimport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
class MealImportJobStatusUpdater {

    private final MealImportJobRepository jobRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markProcessing(UUID jobId) {
        var job = jobRepository.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException(jobId));
        job.markProcessing();
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markDone(UUID jobId, String resultJson) {
        var job = jobRepository.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException(jobId));
        job.markDone(resultJson);
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(UUID jobId, String safeErrorMessage) {
        jobRepository.findById(jobId).ifPresentOrElse(
            job -> {
                job.markFailed(safeErrorMessage);
                jobRepository.save(job);
            },
            () -> log.warn("Job {} not found when trying to mark as FAILED", jobId)
        );
    }
}
