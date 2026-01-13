package com.healthassistant.weightimport;

import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import com.healthassistant.weightimport.api.WeightImportFacade;
import com.healthassistant.weightimport.api.dto.WeightImportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class WeightImportService implements WeightImportFacade {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final Set<String> GENERIC_IMAGE_TYPES = Set.of(
            "image/*", "application/octet-stream"
    );
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final int HASH_PREFIX_LENGTH = 8;

    private final WeightImageExtractor imageExtractor;
    private final WeightEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;

    @Override
    public WeightImportResponse importFromImage(MultipartFile image, DeviceId deviceId) {
        validateImage(image);

        try {
            ExtractedWeightData extractedData = imageExtractor.extract(image);

            if (!extractedData.isValid()) {
                log.warn("Weight extraction invalid for device {}: {}",
                        WeightImportSecurityUtils.maskDeviceId(deviceId.value()), extractedData.validationError());
                return WeightImportResponse.failure(mapToSafeErrorMessage(extractedData.validationError()));
            }

            IdempotencyKey idempotencyKey = generateIdempotencyKey(deviceId, extractedData);
            String measurementId = generateMeasurementId(image, extractedData);
            log.info("Creating weight event with key: {}", idempotencyKey.value());

            StoreHealthEventsCommand.EventEnvelope envelope = eventMapper.mapToEventEnvelope(
                    extractedData, measurementId, idempotencyKey, deviceId
            );

            StoreHealthEventsCommand command = new StoreHealthEventsCommand(List.of(envelope), deviceId);
            StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

            if (result.results().isEmpty()) {
                log.error("No event results returned from health events facade");
                return WeightImportResponse.failure("Internal error: no results returned");
            }

            var eventResult = result.results().getFirst();
            if (eventResult.status() == StoreHealthEventsResult.EventStatus.invalid) {
                String errorMessage = Optional.ofNullable(eventResult.error())
                        .map(StoreHealthEventsResult.EventError::message)
                        .orElse("Validation failed");
                log.warn("Weight event validation failed: {}", errorMessage);
                return WeightImportResponse.failure("Validation error: " + errorMessage);
            }

            String eventId = eventResult.eventId() != null
                    ? eventResult.eventId().value()
                    : null;

            boolean overwrote = eventResult.status() == StoreHealthEventsResult.EventStatus.duplicate;

            log.info("Successfully imported weight {} for device {}: {}kg, score={}, BMI={}, status={}, overwrote={}",
                    measurementId, WeightImportSecurityUtils.maskDeviceId(deviceId.value()), extractedData.weightKg(),
                    extractedData.score(), extractedData.bmi(), eventResult.status(), overwrote);

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
                    WeightImportSecurityUtils.maskDeviceId(deviceId.value()), e.getMessage());
            return WeightImportResponse.failure(mapToSafeErrorMessage(e.getMessage()));
        }
    }

    private void validateImage(MultipartFile image) {
        if (image.isEmpty()) {
            throw new IllegalArgumentException("Image file is empty");
        }

        if (image.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Image file exceeds maximum size of 10MB");
        }

        String contentType = image.getContentType();
        if (contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return;
        }

        if (contentType != null && GENERIC_IMAGE_TYPES.contains(contentType)) {
            String detectedType = detectImageType(image);
            if (detectedType != null && ALLOWED_CONTENT_TYPES.contains(detectedType)) {
                log.debug("Detected image type {} from magic bytes (client sent {})", detectedType, contentType);
                return;
            }
        }

        throw new IllegalArgumentException(
                "Invalid image type '" + contentType + "'. Allowed: JPEG, PNG, WebP"
        );
    }

    private String detectImageType(MultipartFile image) {
        try {
            byte[] header = new byte[12];
            int read = image.getInputStream().read(header);
            if (read < 4) {
                return null;
            }

            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return "image/jpeg";
            }

            if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return "image/png";
            }

            if (read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                    && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
                return "image/webp";
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to detect image type from magic bytes", e);
            return null;
        }
    }

    private IdempotencyKey generateIdempotencyKey(DeviceId deviceId, ExtractedWeightData data) {
        String keyValue = String.format("%s|weight|%s",
                deviceId.value(),
                data.measuredAt().toString()
        );
        return IdempotencyKey.of(keyValue);
    }

    private String generateMeasurementId(MultipartFile image, ExtractedWeightData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(image.getBytes());
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
        if (errorMessage.contains("empty response")) {
            return "AI processing failed";
        }
        if (lowerMessage.contains("confidence")) {
            return "Low confidence in extracted data";
        }
        if (lowerMessage.contains("not a weight") || errorMessage.contains("isWeightScreenshot")) {
            return "Image does not appear to be a weight measurement";
        }
        if (lowerMessage.contains("security") || lowerMessage.contains("suspicious")) {
            return "Security validation failed";
        }
        if (errorMessage.contains("required but not found")) {
            return "Could not find required weight data in image";
        }
        return "Failed to process image";
    }
}
