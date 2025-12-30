package com.healthassistant.googlefit;

import com.healthassistant.config.AppProperties;
import com.healthassistant.config.ReprojectionService;
import com.healthassistant.googlefit.HistoricalSyncTask.SyncTaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
class HistoricalSyncTaskProcessor {

    private static final int MAX_RETRIES = 3;
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final HistoricalSyncTaskRepository taskRepository;
    private final GoogleFitSyncService googleFitSyncService;
    private final ReprojectionService reprojectionService;
    private final AppProperties appProperties;

    HistoricalSyncTaskProcessor(HistoricalSyncTaskRepository taskRepository,
                                @Lazy GoogleFitSyncService googleFitSyncService,
                                ReprojectionService reprojectionService,
                                AppProperties appProperties) {
        this.taskRepository = taskRepository;
        this.googleFitSyncService = googleFitSyncService;
        this.reprojectionService = reprojectionService;
        this.appProperties = appProperties;
    }

    /**
     * Process pending historical sync tasks.
     * Called on-demand when historical sync is requested.
     */
    public void processNextBatch() {
        log.debug("Checking for pending historical sync tasks");

        List<HistoricalSyncTask> pendingTasks = taskRepository.findTop10ByStatusOrderByCreatedAtAsc(SyncTaskStatus.PENDING);

        if (pendingTasks.isEmpty()) {
            log.debug("No pending historical sync tasks");
            return;
        }

        log.info("Processing {} pending historical sync tasks", pendingTasks.size());
        processTasks(pendingTasks);
    }

    private void processTasks(List<HistoricalSyncTask> tasks) {
        for (HistoricalSyncTask task : tasks) {
            markTaskInProgress(task);
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = tasks.stream()
                    .map(task -> CompletableFuture.runAsync(() -> processTask(task), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        log.info("All sync tasks completed");
    }

    @Transactional
    protected void markTaskInProgress(HistoricalSyncTask task) {
        task.markInProgress();
        taskRepository.save(task);
        log.debug("Marked task {} for date {} as IN_PROGRESS", task.getId(), task.getSyncDate());
    }

    private void processTask(HistoricalSyncTask task) {
        LocalDate date = task.getSyncDate();
        log.info("Processing historical sync for date: {}", date);

        try {
            ZonedDateTime dayStart = date.atStartOfDay(POLAND_ZONE);
            ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(POLAND_ZONE);

            Instant from = dayStart.toInstant();
            Instant to = dayEnd.toInstant();

            int eventsSynced = googleFitSyncService.syncTimeWindow(from, to);

            // Reproject for this specific date
            String deviceId = appProperties.getGoogleFit().getDeviceId();
            reprojectionService.reprojectForDate(deviceId, date);

            markTaskCompleted(task, eventsSynced);
            log.info("Successfully synced and reprojected date {}: {} events", date, eventsSynced);

        } catch (Exception e) {
            log.error("Failed to sync date {}: {}", date, e.getMessage(), e);
            markTaskFailed(task, e.getMessage());
        }
    }

    @Transactional
    protected void markTaskCompleted(HistoricalSyncTask task, int eventsSynced) {
        task.markCompleted(eventsSynced);
        taskRepository.save(task);
        log.debug("Marked task {} as COMPLETED with {} events", task.getId(), eventsSynced);
    }

    @Transactional
    protected void markTaskFailed(HistoricalSyncTask task, String errorMessage) {
        task.markFailed(errorMessage);
        taskRepository.save(task);

        if (task.getStatus() == SyncTaskStatus.FAILED) {
            log.warn("Task {} for date {} permanently FAILED after {} retries",
                    task.getId(), task.getSyncDate(), MAX_RETRIES);
        } else {
            log.info("Task {} for date {} will be retried (attempt {}/{})",
                    task.getId(), task.getSyncDate(), task.getRetryCount(), MAX_RETRIES);
        }
    }
}
