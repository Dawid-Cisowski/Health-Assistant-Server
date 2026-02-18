package com.healthassistant.weightimport;

import com.healthassistant.config.AiMetricsRecorder;
import com.healthassistant.config.ImageValidationUtils;
import com.healthassistant.config.ImportConstants;
import com.healthassistant.config.SecurityUtils;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import com.healthassistant.weightimport.api.WeightImportFacade;
import com.healthassistant.weightimport.api.dto.WeightImportResponse;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class WeightImportService implements WeightImportFacade {

    private static final int MAX_IMAGES = 5;
    private static final int HASH_PREFIX_LENGTH = 8;

    private final WeightImageExtractor imageExtractor;
    private final WeightEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;
    private final AiMetricsRecorder aiMetrics;

    @Override
    public WeightImportResponse importFromImages(List<MultipartFile> images, DeviceId deviceId) {
        validateImages(images);
        var sample = aiMetrics.startTimer();
        aiMetrics.recordImportImageCount("weight", images.size());

        try {
            ExtractedWeightData extractedData = imageExtractor.extract(images);

            if (!extractedData.isValid()) {
                log.warn("Weight extraction invalid for device {}: {}",
                        SecurityUtils.maskDeviceId(deviceId.value()), extractedData.validationError());
                aiMetrics.recordImportRequest("weight", sample, "error", "direct");
                return WeightImportResponse.failure(mapToSafeErrorMessage(extractedData.validationError()));
            }

            IdempotencyKey idempotencyKey = generateIdempotencyKey(deviceId, extractedData);
            String measurementId = generateMeasurementId(images, extractedData);
            log.info("Creating weight event with key: {}", idempotencyKey.value());

            StoreHealthEventsCommand.EventEnvelope envelope = eventMapper.mapToEventEnvelope(
                    extractedData, measurementId, idempotencyKey
            );

            StoreHealthEventsCommand command = new StoreHealthEventsCommand(List.of(envelope), deviceId);
            StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

            if (result.results().isEmpty()) {
                log.error("No event results returned from health events facade");
                aiMetrics.recordImportRequest("weight", sample, "error", "direct");
                return WeightImportResponse.failure("Internal error: no results returned");
            }

            var eventResult = result.results().getFirst();
            if (eventResult.status() == StoreHealthEventsResult.EventStatus.INVALID) {
                String errorMessage = Optional.ofNullable(eventResult.error())
                        .map(StoreHealthEventsResult.EventError::message)
                        .orElse("Validation failed");
                log.warn("Weight event validation failed: {}", errorMessage);
                aiMetrics.recordImportRequest("weight", sample, "error", "direct");
                return WeightImportResponse.failure("Validation error: " + errorMessage);
            }

            String eventId = eventResult.eventId() != null
                    ? eventResult.eventId().value()
                    : null;

            boolean overwrote = eventResult.status() == StoreHealthEventsResult.EventStatus.DUPLICATE;

            log.info("Successfully imported weight {} for device {}: {}kg, score={}, BMI={}, status={}, overwrote={}",
                    measurementId, SecurityUtils.maskDeviceId(deviceId.value()), extractedData.weightKg(),
                    extractedData.score(), extractedData.bmi(), eventResult.status(), overwrote);

            aiMetrics.recordImportConfidence("weight", extractedData.confidence());
            aiMetrics.recordImportRequest("weight", sample, "success", "direct");

            return WeightImportResponse.success(
                    measurementId,
                    eventId,
                    extractedData.measurementDate(),
                    extractedData.measuredAt(),
                    extractedData.score(),
                    extractedData.weightKg(),
                    extractedData.bmi(),
                    extractedData.bodyFatPercent(),
                    extractedData.musclePercent(),
                    extractedData.hydrationPercent(),
                    extractedData.boneMassKg(),
                    extractedData.bmrKcal(),
                    extractedData.visceralFatLevel(),
                    extractedData.metabolicAge(),
                    extractedData.confidence(),
                    overwrote
            );

        } catch (WeightExtractionException e) {
            log.warn("Weight extraction failed for device {}: {}",
                    SecurityUtils.maskDeviceId(deviceId.value()), e.getMessage());
            aiMetrics.recordImportRequest("weight", sample, "error", "direct");
            return WeightImportResponse.failure(mapToSafeErrorMessage(e.getMessage()));
        }
    }

    private void validateImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("At least one image is required");
        }

        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Maximum " + MAX_IMAGES + " images allowed");
        }

        images.forEach(ImageValidationUtils::validateImage);
    }

    private IdempotencyKey generateIdempotencyKey(DeviceId deviceId, ExtractedWeightData data) {
        String keyValue = String.format("%s|weight|%s",
                deviceId.value(),
                data.measuredAt().toString()
        );
        return IdempotencyKey.of(keyValue);
    }

    private String generateMeasurementId(List<MultipartFile> images, ExtractedWeightData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            images.forEach(image -> {
                try {
                    digest.update(image.getBytes());
                } catch (Exception e) {
                    log.warn("Failed to read image bytes for hash", e);
                }
            });
            byte[] hash = digest.digest();
            String imageHash = HexFormat.of().formatHex(hash).substring(0, HASH_PREFIX_LENGTH);

            return String.format("scale-import-%s-%s", data.measurementDate(), imageHash);

        } catch (Exception e) {
            log.warn("Failed to generate hash-based measurement ID, using UUID", e);
            return "scale-import-" + UUID.randomUUID().toString().substring(0, HASH_PREFIX_LENGTH);
        }
    }

    private String mapToSafeErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return "Failed to extract weight data";
        }
        String lowerMessage = errorMessage.toLowerCase(java.util.Locale.ROOT);
        if (lowerMessage.contains("empty response")) {
            return "AI processing failed";
        }
        if (lowerMessage.contains("confidence")) {
            return "Low confidence in extracted data";
        }
        if (lowerMessage.contains("not a weight") || lowerMessage.contains("isweightscreenshot")) {
            return "Image does not appear to be a weight measurement";
        }
        if (lowerMessage.contains("security") || lowerMessage.contains("suspicious")) {
            return "Security validation failed";
        }
        if (lowerMessage.contains("required but not found")) {
            return "Could not find required weight data in image";
        }
        return "Failed to process image";
    }
}
