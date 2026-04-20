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
class MealImportJobProcessor implements MealImportJobProcessorPort {

    private static final String SAFE_IMPORT_ERROR = "Meal import processing failed. Please try again.";
    private static final String SAFE_NOT_MEAL_ERROR = "Content does not appear to be food-related.";
    private static final String SAFE_EXTRACTION_ERROR = "Could not extract valid meal data from provided content.";

    private final MealImportJobStatusUpdater statusUpdater;
    private final MealImportService mealImportService;
    private final ObjectMapper objectMapper;

    @Async("mealImportExecutor")
    public void processJob(UUID jobId, MealImportJobType jobType, String deviceIdStr,
                           String description, List<MealImportJob.ImageEntry> imageData) {
        log.info("Processing meal import job {} type={} device={}",
            jobId, jobType, SecurityUtils.maskDeviceId(deviceIdStr));
        statusUpdater.markProcessing(jobId);

        var deviceId = DeviceId.of(deviceIdStr);
        List<MultipartFile> images = reconstructImages(imageData);

        try {
            if (jobType == MealImportJobType.IMPORT) {
                processImportJob(jobId, description, images, deviceId);
            } else {
                processAnalyzeJob(jobId, description, images, deviceId);
            }
        } catch (Exception e) {
            log.warn("Meal import job {} failed for device {}: {}",
                jobId, SecurityUtils.maskDeviceId(deviceIdStr), e.getMessage());
            statusUpdater.markFailed(jobId, SAFE_IMPORT_ERROR);
        }
    }

    private void processImportJob(UUID jobId, String description, List<MultipartFile> images, DeviceId deviceId) throws Exception {
        MealImportResponse result = mealImportService.executeImport(description, images, deviceId);
        if ("failed".equals(result.status())) {
            statusUpdater.markFailed(jobId, determineSafeError(result.errorMessage()));
            return;
        }
        statusUpdater.markDone(jobId, objectMapper.writeValueAsString(result));
        log.info("Meal import job {} completed successfully", jobId);
    }

    private void processAnalyzeJob(UUID jobId, String description, List<MultipartFile> images, DeviceId deviceId) throws Exception {
        MealDraftResponse result = mealImportService.executeAnalyze(description, images, deviceId);
        if ("failed".equals(result.status())) {
            statusUpdater.markFailed(jobId, determineSafeError(result.errorMessage()));
            return;
        }
        statusUpdater.markDone(jobId, objectMapper.writeValueAsString(result));
        log.info("Meal analyze job {} completed successfully", jobId);
    }

    private List<MultipartFile> reconstructImages(List<MealImportJob.ImageEntry> imageData) {
        if (imageData == null || imageData.isEmpty()) {
            return List.of();
        }
        return imageData.stream()
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
