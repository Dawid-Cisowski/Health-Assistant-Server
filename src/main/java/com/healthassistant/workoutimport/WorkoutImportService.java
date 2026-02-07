package com.healthassistant.workoutimport;

import com.healthassistant.config.AiMetricsRecorder;
import com.healthassistant.config.ImageValidationUtils;
import com.healthassistant.config.ImportConstants;
import com.healthassistant.config.SecurityUtils;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import com.healthassistant.workoutimport.api.WorkoutImportFacade;
import com.healthassistant.workoutimport.api.dto.WorkoutImportResponse;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class WorkoutImportService implements WorkoutImportFacade {

    private final WorkoutImageExtractor imageExtractor;
    private final WorkoutEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;
    private final WorkoutFacade workoutFacade;
    private final AiMetricsRecorder aiMetrics;

    @Override
    @Transactional
    public WorkoutImportResponse importFromImage(MultipartFile image, DeviceId deviceId) {
        ImageValidationUtils.validateImage(image);
        var sample = aiMetrics.startTimer();

        try {
            ExtractedWorkoutData extractedData = imageExtractor.extract(image);

            if (!extractedData.isValid()) {
                log.warn("Workout extraction invalid for device {}: {}",
                    SecurityUtils.maskDeviceId(deviceId.value()), SecurityUtils.sanitizeForLog(extractedData.validationError()));
                aiMetrics.recordImportRequest("workout", sample, "error", "direct");
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
                log.warn("Workout event validation failed: {}", SecurityUtils.sanitizeForLog(errorMessage));
                aiMetrics.recordImportRequest("workout", sample, "error", "direct");
                return WorkoutImportResponse.failure("Validation error: " + errorMessage);
            }

            int exerciseCount = extractedData.exercises().size();
            int totalSets = extractedData.exercises().stream()
                .mapToInt(e -> e.sets().size())
                .sum();

            String eventId = eventResult.eventId() != null
                ? eventResult.eventId().value()
                : null;

            WorkoutDetailResponse workoutDetails = workoutFacade.getWorkoutDetails(deviceId.value(), workoutId)
                .orElse(null);

            log.info("Successfully imported workout {} for device {}: {} exercises, {} sets, status={}",
                SecurityUtils.sanitizeForLog(workoutId), SecurityUtils.maskDeviceId(deviceId.value()), exerciseCount, totalSets, eventResult.status());

            aiMetrics.recordImportConfidence("workout", extractedData.confidence());
            aiMetrics.recordImportRequest("workout", sample, "success", "direct");

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
            log.warn("Workout extraction failed for device {}: {}", SecurityUtils.maskDeviceId(deviceId.value()), SecurityUtils.sanitizeForLog(e.getMessage()));
            aiMetrics.recordImportRequest("workout", sample, "error", "direct");
            return WorkoutImportResponse.failure(e.getMessage());
        }
    }

    private String generateWorkoutId(MultipartFile image, ExtractedWorkoutData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(image.getBytes());
            String imageHash = HexFormat.of().formatHex(hash).substring(0, 8);

            LocalDate date = data.performedAt().atZone(ImportConstants.POLAND_ZONE).toLocalDate();
            return String.format("gymrun-screenshot-%s-%s", date, imageHash);

        } catch (Exception e) {
            log.warn("Failed to generate hash-based workout ID, using UUID", e);
            return "gymrun-screenshot-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
