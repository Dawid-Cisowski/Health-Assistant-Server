package com.healthassistant.sleepimport;

import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import com.healthassistant.sleepimport.api.SleepImportFacade;
import com.healthassistant.sleepimport.api.dto.SleepImportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class SleepImportService implements SleepImportFacade {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final Set<String> GENERIC_IMAGE_TYPES = Set.of(
            "image/*", "application/octet-stream"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final SleepImageExtractor imageExtractor;
    private final SleepEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;

    @Override
    public SleepImportResponse importFromImage(MultipartFile image, DeviceId deviceId, Integer year) {
        validateImage(image);

        int effectiveYear = year != null ? year : LocalDate.now(POLAND_ZONE).getYear();

        try {
            ExtractedSleepData extractedData = imageExtractor.extract(image, effectiveYear);

            if (!extractedData.isValid()) {
                log.warn("Sleep extraction invalid for device {}: {}",
                        deviceId.value(), extractedData.validationError());
                return SleepImportResponse.failure(
                        "Could not extract valid sleep data: " + extractedData.validationError()
                );
            }

            Optional<IdempotencyKey> existingKey = healthEventsFacade.findExistingSleepIdempotencyKey(
                    deviceId, extractedData.sleepStart()
            );

            boolean overwrote = existingKey.isPresent();
            IdempotencyKey idempotencyKey;

            if (overwrote) {
                idempotencyKey = existingKey.get();
                log.info("Found existing sleep with same start time, will overwrite: {}", idempotencyKey.value());
            } else {
                idempotencyKey = generateNewIdempotencyKey(deviceId, extractedData);
                log.info("No existing sleep found, creating new with key: {}", idempotencyKey.value());
            }

            String sleepId = generateSleepId(image, extractedData);

            StoreHealthEventsCommand.EventEnvelope envelope = eventMapper.mapToEventEnvelope(
                    extractedData, sleepId, idempotencyKey, deviceId
            );

            StoreHealthEventsCommand command = new StoreHealthEventsCommand(
                    List.of(envelope), deviceId
            );
            StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

            var eventResult = result.results().getFirst();
            if (eventResult.status() == StoreHealthEventsResult.EventStatus.invalid) {
                String errorMessage = eventResult.error() != null
                        ? eventResult.error().message()
                        : "Validation failed";
                log.warn("Sleep event validation failed: {}", errorMessage);
                return SleepImportResponse.failure("Validation error: " + errorMessage);
            }

            String eventId = eventResult.eventId() != null
                    ? eventResult.eventId().value()
                    : null;

            log.info("Successfully imported sleep {} for device {}: {}min, score={}, status={}, overwrote={}",
                    sleepId, deviceId.value(), extractedData.totalSleepMinutes(),
                    extractedData.sleepScore(), eventResult.status(), overwrote);

            return SleepImportResponse.success(
                    sleepId,
                    eventId,
                    extractedData.sleepStart(),
                    extractedData.sleepEnd(),
                    extractedData.totalSleepMinutes(),
                    extractedData.sleepScore(),
                    extractedData.phases().lightSleepMinutes(),
                    extractedData.phases().deepSleepMinutes(),
                    extractedData.phases().remSleepMinutes(),
                    extractedData.phases().awakeMinutes(),
                    extractedData.confidence(),
                    overwrote
            );

        } catch (SleepExtractionException e) {
            log.warn("Sleep extraction failed for device {}: {}", deviceId.value(), e.getMessage());
            return SleepImportResponse.failure(e.getMessage());
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

    private IdempotencyKey generateNewIdempotencyKey(DeviceId deviceId, ExtractedSleepData data) {
        String keyValue = String.format("%s|sleep-import|%s",
                deviceId.value(),
                data.sleepStart().toString()
        );
        return IdempotencyKey.of(keyValue);
    }

    private String generateSleepId(MultipartFile image, ExtractedSleepData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(image.getBytes());
            String imageHash = HexFormat.of().formatHex(hash).substring(0, 8);

            return String.format("ohealth-import-%s-%s", data.sleepDate(), imageHash);

        } catch (Exception e) {
            log.warn("Failed to generate hash-based sleep ID, using UUID", e);
            return "ohealth-import-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
