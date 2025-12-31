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
    private final HistoricalSyncTaskProcessor self;

    HistoricalSyncTaskProcessor(HistoricalSyncTaskRepository taskRepository,
                                @Lazy GoogleFitSyncService googleFitSyncService,
                                ReprojectionService reprojectionService,
                                AppProperties appProperties,
                                @Lazy HistoricalSyncTaskProcessor self) {
        this.taskRepository = taskRepository;
        this.googleFitSyncService = googleFitSyncService;
        this.reprojectionService = reprojectionService;
        this.appProperties = appProperties;
        this.self = self;
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
        log.info("=== STARTING BATCH PROCESSING OF {} TASKS ===", tasks.size());

        // Extract task info before async processing (tasks may become detached)
        record TaskInfo(Long id, LocalDate date) {}
        List<TaskInfo> taskInfos = tasks.stream()
                .map(t -> new TaskInfo(t.getId(), t.getSyncDate()))
                .toList();

        log.info("Task IDs to process: {}", taskInfos.stream().map(TaskInfo::id).toList());
        log.info("Task dates to process: {}", taskInfos.stream().map(TaskInfo::date).toList());

        for (TaskInfo taskInfo : taskInfos) {
            log.info("Marking task {} (date={}) as IN_PROGRESS...", taskInfo.id(), taskInfo.date());
            self.markTaskInProgress(taskInfo.id());
        }

        log.info("All {} tasks marked as IN_PROGRESS, starting async processing...", taskInfos.size());

        // Process tasks asynchronously - don't block the HTTP request
        Thread.startVirtualThread(() -> {
            log.info("Virtual thread started for batch processing");
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = taskInfos.stream()
                        .map(taskInfo -> CompletableFuture.runAsync(
                                () -> processTask(taskInfo.id(), taskInfo.date()), executor))
                        .toList();

                log.info("Waiting for {} parallel tasks to complete...", futures.size());
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception e) {
                log.error("Error in batch processing virtual thread: {}", e.getMessage(), e);
            }
            log.info("=== ALL {} SYNC TASKS COMPLETED ===", taskInfos.size());
        });

        log.info("Batch processing started in background, returning to caller");
    }

    @Transactional
    protected void markTaskInProgress(Long taskId) {
        log.info("[TASK {}] Loading from DB to mark IN_PROGRESS", taskId);
        HistoricalSyncTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));
        log.info("[TASK {}] Current status: {}, date: {}", taskId, task.getStatus(), task.getSyncDate());
        task.markInProgress();
        taskRepository.save(task);
        log.info("[TASK {}] Saved as IN_PROGRESS", taskId);
    }

    private void processTask(Long taskId, LocalDate date) {
        log.info("[TASK {}] ========== PROCESSING START (date={}) ==========", taskId, date);

        try {
            ZonedDateTime dayStart = date.atStartOfDay(POLAND_ZONE);
            ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(POLAND_ZONE);

            Instant from = dayStart.toInstant();
            Instant to = dayEnd.toInstant();

            log.info("[TASK {}] Syncing time window: {} to {}", taskId, from, to);
            int eventsSynced = googleFitSyncService.syncTimeWindow(from, to);
            log.info("[TASK {}] Sync complete: {} events synced", taskId, eventsSynced);

            // Reproject for this specific date
            String deviceId = appProperties.getGoogleFit().getDeviceId();
            log.info("[TASK {}] Starting reprojection for device={} date={}", taskId, deviceId, date);
            reprojectionService.reprojectForDate(deviceId, date);
            log.info("[TASK {}] Reprojection complete", taskId);

            log.info("[TASK {}] Marking as COMPLETED...", taskId);
            self.markTaskCompleted(taskId, eventsSynced);
            log.info("[TASK {}] ========== PROCESSING SUCCESS (date={}, events={}) ==========", taskId, date, eventsSynced);

        } catch (Exception e) {
            log.error("[TASK {}] ========== PROCESSING FAILED (date={}) ==========", taskId, date);
            log.error("[TASK {}] Error: {}", taskId, e.getMessage(), e);
            try {
                self.markTaskFailed(taskId, e.getMessage());
            } catch (Exception markError) {
                log.error("[TASK {}] Failed to mark task as failed: {}", taskId, markError.getMessage(), markError);
            }
        }
    }

    @Transactional
    protected void markTaskCompleted(Long taskId, int eventsSynced) {
        log.info("[TASK {}] Loading from DB to mark COMPLETED", taskId);
        HistoricalSyncTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));
        log.info("[TASK {}] Current status before completion: {}", taskId, task.getStatus());
        task.markCompleted(eventsSynced);
        taskRepository.save(task);
        log.info("[TASK {}] Saved as COMPLETED with {} events", taskId, eventsSynced);
    }

    @Transactional
    protected void markTaskFailed(Long taskId, String errorMessage) {
        log.info("[TASK {}] Loading from DB to mark FAILED", taskId);
        HistoricalSyncTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));
        log.info("[TASK {}] Current status before failure: {}, retryCount: {}", taskId, task.getStatus(), task.getRetryCount());
        task.markFailed(errorMessage);
        taskRepository.save(task);

        if (task.getStatus() == SyncTaskStatus.FAILED) {
            log.warn("[TASK {}] Permanently FAILED after {} retries. Error: {}",
                    taskId, MAX_RETRIES, errorMessage);
        } else {
            log.info("[TASK {}] Will be retried (attempt {}/{}). Error: {}",
                    taskId, task.getRetryCount(), MAX_RETRIES, errorMessage);
        }
    }
}
