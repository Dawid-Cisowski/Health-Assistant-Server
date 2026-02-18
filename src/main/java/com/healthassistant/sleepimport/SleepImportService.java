package com.healthassistant.sleepimport;

import com.healthassistant.config.AiMetricsRecorder;
import com.healthassistant.config.ImageValidationUtils;
import com.healthassistant.config.ImportConstants;
import com.healthassistant.config.SecurityUtils;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.ExistingSleepInfo;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.payload.EventDeletedPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import com.healthassistant.sleepimport.api.SleepImportFacade;
import com.healthassistant.sleepimport.api.dto.SleepImportResponse;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class SleepImportService implements SleepImportFacade {

    private static final int HASH_PREFIX_LENGTH = 8;

    private final SleepImageExtractor imageExtractor;
    private final SleepEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;
    private final AiMetricsRecorder aiMetrics;

    @Override
    public SleepImportResponse importFromImage(MultipartFile image, DeviceId deviceId, Integer year) {
        ImageValidationUtils.validateImage(image);
        var sample = aiMetrics.startTimer();

        int effectiveYear = year != null ? year : LocalDate.now(ImportConstants.POLAND_ZONE).getYear();

        try {
            ExtractedSleepData extractedData = imageExtractor.extract(image, effectiveYear);

            if (!extractedData.isValid()) {
                log.warn("Sleep extraction invalid for device {}: {}",
                        SecurityUtils.maskDeviceId(deviceId.value()), extractedData.validationError());
                aiMetrics.recordImportRequest("sleep", sample, "error", "direct");
                return SleepImportResponse.failure(
                        "Could not extract valid sleep data: " + extractedData.validationError()
                );
            }

            Optional<ExistingSleepInfo> existingInfo = healthEventsFacade.findOverlappingSleepInfo(
                    deviceId, extractedData.sleepStart(), extractedData.sleepEnd()
            );

            boolean overwrote = existingInfo.isPresent();
            List<StoreHealthEventsCommand.EventEnvelope> envelopes = new ArrayList<>();

            if (overwrote) {
                String targetEventId = existingInfo.get().eventId().value();
                log.info("Found overlapping sleep session, will delete and recreate: eventId={}, existingRange=[{} - {}]",
                        targetEventId, existingInfo.get().sleepStart(), existingInfo.get().sleepEnd());

                EventDeletedPayload deletePayload = new EventDeletedPayload(
                        targetEventId,
                        existingInfo.get().idempotencyKey().value(),
                        "Replaced by oHealth sleep import"
                );

                IdempotencyKey deleteIdempotencyKey = IdempotencyKey.of(
                        String.format("%s|delete|%s", deviceId.value(), targetEventId)
                );

                envelopes.add(new StoreHealthEventsCommand.EventEnvelope(
                        deleteIdempotencyKey,
                        "EventDeleted.v1",
                        Instant.now(),
                        deletePayload
                ));
            }

            IdempotencyKey newIdempotencyKey = generateNewIdempotencyKey(deviceId, extractedData);
            String sleepId = generateSleepId(image, extractedData);
            log.info("Creating new sleep event with key: {}", newIdempotencyKey.value());

            StoreHealthEventsCommand.EventEnvelope sleepEnvelope = eventMapper.mapToEventEnvelope(
                    extractedData, sleepId, newIdempotencyKey
            );
            envelopes.add(sleepEnvelope);

            StoreHealthEventsCommand command = new StoreHealthEventsCommand(envelopes, deviceId);
            StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

            if (result.results().isEmpty()) {
                log.error("No event results returned from health events facade");
                return SleepImportResponse.failure("Internal error: no results returned");
            }

            var sleepEventResult = result.results().getLast();
            if (sleepEventResult.status() == StoreHealthEventsResult.EventStatus.INVALID) {
                String errorMessage = Optional.ofNullable(sleepEventResult.error())
                        .map(StoreHealthEventsResult.EventError::message)
                        .orElse("Validation failed");
                log.warn("Sleep event validation failed: {}", errorMessage);
                aiMetrics.recordImportRequest("sleep", sample, "error", "direct");
                return SleepImportResponse.failure("Validation error: " + errorMessage);
            }

            String eventId = sleepEventResult.eventId() != null
                    ? sleepEventResult.eventId().value()
                    : null;

            log.info("Successfully imported sleep {} for device {}: {}min, score={}, status={}, overwrote={}, tokens={}/{}",
                    sleepId, SecurityUtils.maskDeviceId(deviceId.value()), extractedData.totalSleepMinutes(),
                    extractedData.sleepScore(), sleepEventResult.status(), overwrote,
                    extractedData.promptTokens(), extractedData.completionTokens());

            aiMetrics.recordImportConfidence("sleep", extractedData.confidence());
            aiMetrics.recordImportTokens("sleep", extractedData.promptTokens(), extractedData.completionTokens());
            aiMetrics.recordImportRequest("sleep", sample, "success", "direct");

            return SleepImportResponse.successWithTokens(
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
                    overwrote,
                    extractedData.promptTokens(),
                    extractedData.completionTokens()
            );

        } catch (SleepExtractionException e) {
            log.warn("Sleep extraction failed for device {}: {}", SecurityUtils.maskDeviceId(deviceId.value()), e.getMessage());
            aiMetrics.recordImportRequest("sleep", sample, "error", "direct");
            return SleepImportResponse.failure(e.getMessage());
        }
    }

    private IdempotencyKey generateNewIdempotencyKey(DeviceId deviceId, ExtractedSleepData data) {
        String keyValue = String.format("%s|sleep-import|%s|%d",
                deviceId.value(),
                data.sleepStart().toString(),
                Instant.now().toEpochMilli()
        );
        return IdempotencyKey.of(keyValue);
    }

    private String generateSleepId(MultipartFile image, ExtractedSleepData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(image.getBytes());
            String imageHash = HexFormat.of().formatHex(hash).substring(0, HASH_PREFIX_LENGTH);

            return String.format("ohealth-import-%s-%s", data.sleepDate(), imageHash);

        } catch (Exception e) {
            log.warn("Failed to generate hash-based sleep ID, using UUID", e);
            return "ohealth-import-" + UUID.randomUUID().toString().substring(0, HASH_PREFIX_LENGTH);
        }
    }
}
