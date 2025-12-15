package com.healthassistant.mealimport;

import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.mealimport.api.MealImportFacade;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
import com.healthassistant.mealimport.dto.ExtractedMealData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class MealImportService implements MealImportFacade {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final Set<String> GENERIC_IMAGE_TYPES = Set.of(
        "image/*", "application/octet-stream"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final MealContentExtractor contentExtractor;
    private final MealEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;

    @Override
    public MealImportResponse importMeal(String description, List<MultipartFile> images, DeviceId deviceId) {
        // Validate input - need at least description or images
        validateInput(description, images);

        // Validate images if present
        if (images != null && !images.isEmpty()) {
            for (MultipartFile image : images) {
                validateImage(image);
            }
        }

        try {
            // Extract meal data using AI
            ExtractedMealData extractedData = contentExtractor.extract(description, images);

            // Validate extraction result
            if (!extractedData.isValid()) {
                log.warn("Meal extraction invalid for device {}: {}",
                    deviceId.value(), extractedData.validationError());
                return MealImportResponse.failure(
                    "Could not extract valid meal data: " + extractedData.validationError()
                );
            }

            // Determine timestamp - use extracted or fallback to now
            Instant occurredAt = extractedData.occurredAt() != null
                ? extractedData.occurredAt()
                : Instant.now();

            // Generate unique meal ID (no idempotency - each upload is a new meal)
            String mealId = generateMealId();

            // Map to event envelope
            StoreHealthEventsCommand.EventEnvelope envelope = eventMapper.mapToEventEnvelope(
                extractedData, mealId, deviceId, occurredAt
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
                log.warn("Meal event validation failed: {}", errorMessage);
                return MealImportResponse.failure("Validation error: " + errorMessage);
            }

            // Get event ID
            String eventId = eventResult.eventId() != null
                ? eventResult.eventId().value()
                : null;

            log.info("Successfully imported meal {} for device {}: {} ({} kcal), status={}",
                mealId, deviceId.value(), extractedData.title(),
                extractedData.caloriesKcal(), eventResult.status());

            return MealImportResponse.success(
                mealId,
                eventId,
                occurredAt,
                extractedData.title(),
                extractedData.mealType(),
                extractedData.caloriesKcal(),
                extractedData.proteinGrams(),
                extractedData.fatGrams(),
                extractedData.carbohydratesGrams(),
                extractedData.healthRating(),
                extractedData.confidence()
            );

        } catch (MealExtractionException e) {
            log.warn("Meal extraction failed for device {}: {}", deviceId.value(), e.getMessage());
            return MealImportResponse.failure(e.getMessage());
        }
    }

    private void validateInput(String description, List<MultipartFile> images) {
        boolean hasDescription = description != null && !description.isBlank();
        boolean hasImages = images != null && !images.isEmpty();

        if (!hasDescription && !hasImages) {
            throw new IllegalArgumentException("Either description or images required");
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
            return; // Valid content type
        }

        // For generic types like image/*, detect from magic bytes
        if (contentType != null && GENERIC_IMAGE_TYPES.contains(contentType)) {
            String detectedType = detectImageType(image);
            if (detectedType != null && ALLOWED_CONTENT_TYPES.contains(detectedType)) {
                log.debug("Detected image type {} from magic bytes (client sent {})", detectedType, contentType);
                return; // Valid detected type
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

            // JPEG: FF D8 FF
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return "image/jpeg";
            }

            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return "image/png";
            }

            // WebP: RIFF....WEBP
            if (read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
                return "image/webp";
            }

            return null;
        } catch (IOException e) {
            log.warn("Failed to detect image type from magic bytes", e);
            return null;
        }
    }

    private String generateMealId() {
        return "meal-" + UUID.randomUUID().toString().substring(0, 12);
    }
}
