package com.healthassistant.mealimport;

import tools.jackson.databind.ObjectMapper;
import com.healthassistant.config.SecurityUtils;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.mealimport.api.dto.MealDraftResponse;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
class MealImportJobProcessor {

    private static final String SAFE_IMPORT_ERROR = "Meal import processing failed. Please try again.";
    private static final String SAFE_NOT_MEAL_ERROR = "Content does not appear to be food-related.";
    private static final String SAFE_EXTRACTION_ERROR = "Could not extract valid meal data from provided content.";

    private final MealImportJobRepository jobRepository;
    private final MealImportJobStatusUpdater statusUpdater;
    private final MealImportService mealImportService;
    private final ObjectMapper objectMapper;

    @Async("mealImportExecutor")
    void processJob(UUID jobId) {
        log.info("Processing meal import job {}", jobId);
        statusUpdater.markProcessing(jobId);

        var jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.error("Job {} not found for processing", jobId);
            return;
        }

        var job = jobOpt.get();
        var deviceId = DeviceId.of(job.getDeviceId());

        try {
            List<MultipartFile> images = reconstructImages(job);

            if (job.getJobType() == MealImportJobType.IMPORT) {
                processImportJob(jobId, job, deviceId, images);
            } else {
                processAnalyzeJob(jobId, job, deviceId, images);
            }
        } catch (Exception e) {
            log.warn("Meal import job {} failed for device {}: {}",
                jobId, SecurityUtils.maskDeviceId(job.getDeviceId()), e.getMessage());
            statusUpdater.markFailed(jobId, SAFE_IMPORT_ERROR);
        }
    }

    private void processImportJob(UUID jobId, MealImportJob job, DeviceId deviceId, List<MultipartFile> images) throws Exception {
        MealImportResponse result = mealImportService.executeImport(job.getDescription(), images, deviceId);
        if ("failed".equals(result.status())) {
            var safeError = determineSafeError(result.errorMessage());
            statusUpdater.markFailed(jobId, safeError);
            return;
        }
        var resultJson = objectMapper.writeValueAsString(result);
        statusUpdater.markDone(jobId, resultJson);
        log.info("Meal import job {} completed successfully", jobId);
    }

    private void processAnalyzeJob(UUID jobId, MealImportJob job, DeviceId deviceId, List<MultipartFile> images) throws Exception {
        MealDraftResponse result = mealImportService.executeAnalyze(job.getDescription(), images, deviceId);
        if ("failed".equals(result.status())) {
            var safeError = determineSafeError(result.errorMessage());
            statusUpdater.markFailed(jobId, safeError);
            return;
        }
        var resultJson = objectMapper.writeValueAsString(result);
        statusUpdater.markDone(jobId, resultJson);
        log.info("Meal analyze job {} completed successfully", jobId);
    }

    private List<MultipartFile> reconstructImages(MealImportJob job) {
        if (job.getImageData() == null || job.getImageData().isEmpty()) {
            return List.of();
        }
        return job.getImageData().stream()
            .<MultipartFile>map(entry -> new InMemoryMultipartFile(
                entry.fileName(),
                entry.contentType(),
                Base64.getDecoder().decode(entry.base64Bytes())
            ))
            .toList();
    }

    private String determineSafeError(String errorMessage) {
        if (errorMessage != null && errorMessage.toLowerCase(Locale.ROOT).contains("not food")) {
            return SAFE_NOT_MEAL_ERROR;
        }
        if (errorMessage != null && errorMessage.toLowerCase(Locale.ROOT).contains("extract")) {
            return SAFE_EXTRACTION_ERROR;
        }
        return SAFE_IMPORT_ERROR;
    }
}
