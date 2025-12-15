package com.healthassistant.workoutimport;

import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import com.healthassistant.workoutimport.api.WorkoutImportFacade;
import com.healthassistant.workoutimport.api.dto.WorkoutImportResponse;
import com.healthassistant.workoutimport.dto.ExtractedWorkoutData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
        "image/jpeg", "image/png", "image/webp"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");

    private final WorkoutImageExtractor imageExtractor;
    private final WorkoutEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;
    private final WorkoutFacade workoutFacade;

    @Override
    public WorkoutImportResponse importFromImage(MultipartFile image, DeviceId deviceId) {
        validateImage(image);

        try {
            // Extract workout data using AI
            ExtractedWorkoutData extractedData = imageExtractor.extract(image);

            // Validate extraction result
            if (!extractedData.isValid()) {
                log.warn("Workout extraction invalid for device {}: {}",
                    deviceId.value(), extractedData.validationError());
                return WorkoutImportResponse.failure(
                    "Could not extract valid workout data: " + extractedData.validationError()
                );
            }

            // Generate idempotency key based on image hash + extracted date
            String workoutId = generateWorkoutId(image, extractedData);

            // Map to event envelope
            StoreHealthEventsCommand.EventEnvelope envelope = eventMapper.mapToEventEnvelope(
                extractedData, workoutId, deviceId
            );

            // Store event via HealthEventsFacade
            StoreHealthEventsCommand command = new StoreHealthEventsCommand(
                List.of(envelope), deviceId
            );
            StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

            // Check result
            var eventResult = result.results().get(0);
            if (eventResult.status() == StoreHealthEventsResult.EventStatus.invalid) {
                String errorMessage = eventResult.error() != null
                    ? eventResult.error().message()
                    : "Validation failed";
                log.warn("Workout event validation failed: {}", errorMessage);
                return WorkoutImportResponse.failure("Validation error: " + errorMessage);
            }

            // Calculate totals
            int exerciseCount = extractedData.exercises().size();
            int totalSets = extractedData.exercises().stream()
                .mapToInt(e -> e.sets().size())
                .sum();

            // Get event ID
            String eventId = eventResult.eventId() != null
                ? eventResult.eventId().value()
                : null;

            // Fetch workout details for response (after projection)
            WorkoutDetailResponse workoutDetails = workoutFacade.getWorkoutDetails(workoutId)
                .orElse(null);

            log.info("Successfully imported workout {} for device {}: {} exercises, {} sets, status={}",
                workoutId, deviceId.value(), exerciseCount, totalSets, eventResult.status());

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
            log.warn("Workout extraction failed for device {}: {}", deviceId.value(), e.getMessage());
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
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                "Invalid image type. Allowed: JPEG, PNG, WebP"
            );
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
