package com.healthassistant.workoutimport;

import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import com.healthassistant.workoutimport.api.WorkoutImportFacade;
import com.healthassistant.workoutimport.api.dto.WorkoutImportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class WorkoutImportService implements WorkoutImportFacade {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final Set<String> GENERIC_IMAGE_TYPES = Set.of(
        "image/*", "application/octet-stream"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final WorkoutImageExtractor imageExtractor;
    private final WorkoutEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;
    private final WorkoutFacade workoutFacade;

    private String sanitizeForLog(String input) {
        if (input == null) {
            return "null";
        }
        return input.replaceAll("[\\n\\r\\t]", "_");
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() < 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    @Override
    @Transactional
    public WorkoutImportResponse importFromImage(MultipartFile image, DeviceId deviceId) {
        validateImage(image);

        try {
            ExtractedWorkoutData extractedData = imageExtractor.extract(image);

            if (!extractedData.isValid()) {
                log.warn("Workout extraction invalid for device {}: {}",
                    maskDeviceId(deviceId.value()), sanitizeForLog(extractedData.validationError()));
                return WorkoutImportResponse.failure(
                    "Could not extract valid workout data: " + extractedData.validationError()
                );
            }

            String workoutId = generateWorkoutId(image, extractedData);

            StoreHealthEventsCommand.EventEnvelope envelope = eventMapper.mapToEventEnvelope(
                extractedData, workoutId, deviceId
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
                log.warn("Workout event validation failed: {}", sanitizeForLog(errorMessage));
                return WorkoutImportResponse.failure("Validation error: " + errorMessage);
            }

            int exerciseCount = extractedData.exercises().size();
            int totalSets = extractedData.exercises().stream()
                .mapToInt(e -> e.sets().size())
                .sum();

            String eventId = eventResult.eventId() != null
                ? eventResult.eventId().value()
                : null;

            WorkoutDetailResponse workoutDetails = workoutFacade.getWorkoutDetails(workoutId)
                .orElse(null);

            log.info("Successfully imported workout {} for device {}: {} exercises, {} sets, status={}",
                sanitizeForLog(workoutId), maskDeviceId(deviceId.value()), exerciseCount, totalSets, eventResult.status());

            return WorkoutImportResponse.success(
                workoutId,
                eventId,
                extractedData.performedAt(),
                extractedData.note(),
                exerciseCount,
                totalSets,
                extractedData.confidence(),
                workoutDetails
            );

        } catch (WorkoutExtractionException e) {
            log.warn("Workout extraction failed for device {}: {}", maskDeviceId(deviceId.value()), sanitizeForLog(e.getMessage()));
            return WorkoutImportResponse.failure(e.getMessage());
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
        try (var inputStream = image.getInputStream()) {
            byte[] header = new byte[12];
            int read = inputStream.read(header);
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

    private String generateWorkoutId(MultipartFile image, ExtractedWorkoutData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(image.getBytes());
            String imageHash = HexFormat.of().formatHex(hash).substring(0, 8);

            LocalDate date = data.performedAt().atZone(POLAND_ZONE).toLocalDate();
            return String.format("gymrun-screenshot-%s-%s", date, imageHash);

        } catch (Exception e) {
            log.warn("Failed to generate hash-based workout ID, using UUID", e);
            return "gymrun-screenshot-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
