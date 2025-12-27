package com.healthassistant.mealimport;

import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.mealimport.api.MealImportFacade;
import com.healthassistant.mealimport.api.dto.MealDraftResponse;
import com.healthassistant.mealimport.api.dto.MealDraftUpdateRequest;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private final MealImportDraftRepository draftRepository;
    private final MealsFacade mealsFacade;

    @Override
    public MealImportResponse importMeal(String description, List<MultipartFile> images, DeviceId deviceId) {
        validateInput(description, images);

        if (images != null && !images.isEmpty()) {
            for (MultipartFile image : images) {
                validateImage(image);
            }
        }

        try {
            MealTimeContext timeContext = buildMealTimeContext();
            ExtractedMealData extractedData = contentExtractor.extract(description, images, timeContext);
            if (!extractedData.isValid()) {
                log.warn("Meal extraction invalid for device {}: {}",
                    deviceId.value(), extractedData.validationError());
                return MealImportResponse.failure(
                    "Could not extract valid meal data: " + extractedData.validationError()
                );
            }

            Instant occurredAt = extractedData.occurredAt() != null
                ? extractedData.occurredAt()
                : Instant.now();

            String mealId = generateMealId();

            StoreHealthEventsCommand.EventEnvelope envelope = eventMapper.mapToEventEnvelope(
                extractedData, mealId, deviceId, occurredAt
            );

            StoreHealthEventsCommand command = new StoreHealthEventsCommand(
                List.of(envelope), deviceId
            );
            StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

            var eventResult = result.results().get(0);
            if (eventResult.status() == StoreHealthEventsResult.EventStatus.invalid) {
                String errorMessage = eventResult.error() != null
                    ? eventResult.error().message()
                    : "Validation failed";
                log.warn("Meal event validation failed: {}", errorMessage);
                return MealImportResponse.failure("Validation error: " + errorMessage);
            }

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
        } catch (IOException e) {
            log.warn("Failed to detect image type from magic bytes", e);
            return null;
        }
    }

    private String generateMealId() {
        return "meal-" + UUID.randomUUID().toString().substring(0, 12);
    }

    @Override
    @Transactional
    public MealDraftResponse analyzeMeal(String description, List<MultipartFile> images, DeviceId deviceId) {
        validateInput(description, images);

        if (images != null && !images.isEmpty()) {
            for (MultipartFile image : images) {
                validateImage(image);
            }
        }

        try {
            MealTimeContext timeContext = buildMealTimeContext();
            ExtractedMealData extractedData = contentExtractor.extract(description, images, timeContext);
            if (!extractedData.isValid()) {
                log.warn("Meal extraction invalid for device {}: {}",
                    deviceId.value(), extractedData.validationError());
                return MealDraftResponse.failure(
                    "Could not extract valid meal data: " + extractedData.validationError()
                );
            }

            Instant suggestedOccurredAt = extractedData.occurredAt() != null
                ? extractedData.occurredAt()
                : Instant.now();

            MealImportDraft draft = MealImportDraft.builder()
                .id(UUID.randomUUID())
                .deviceId(deviceId.value())
                .title(extractedData.title())
                .mealType(MealType.valueOf(extractedData.mealType()))
                .caloriesKcal(extractedData.caloriesKcal())
                .proteinGrams(extractedData.proteinGrams())
                .fatGrams(extractedData.fatGrams())
                .carbohydratesGrams(extractedData.carbohydratesGrams())
                .healthRating(HealthRating.valueOf(extractedData.healthRating()))
                .confidence(BigDecimal.valueOf(extractedData.confidence()))
                .suggestedOccurredAt(suggestedOccurredAt)
                .questions(extractedData.questions())
                .originalDescription(description)
                .description(extractedData.description())
                .build();

            draft = draftRepository.save(draft);

            log.info("Created meal draft {} for device {}: {} ({} kcal), confidence={}",
                draft.getId(), deviceId.value(), draft.getTitle(),
                draft.getCaloriesKcal(), draft.getConfidence());

            return mapToResponse(draft);

        } catch (MealExtractionException e) {
            log.warn("Meal extraction failed for device {}: {}", deviceId.value(), e.getMessage());
            return MealDraftResponse.failure(e.getMessage());
        }
    }

    @Override
    @Transactional
    public MealDraftResponse updateDraft(UUID draftId, MealDraftUpdateRequest request, DeviceId deviceId) {
        MealImportDraft draft = draftRepository.findByIdAndDeviceId(draftId, deviceId.value())
            .orElseThrow(() -> new DraftNotFoundException(draftId));

        if (!draft.isPending()) {
            throw new DraftAlreadyConfirmedException(draftId);
        }

        if (draft.isExpired()) {
            throw new DraftExpiredException(draftId);
        }

        boolean hasAnswers = request.answers() != null && !request.answers().isEmpty();
        boolean hasFeedback = request.userFeedback() != null && !request.userFeedback().isBlank();

        if (hasAnswers || hasFeedback) {
            log.info("Re-analyzing meal draft {} with user context", draftId);

            ExtractedMealData currentExtraction = buildCurrentExtraction(draft);

            try {
                ExtractedMealData reAnalyzed = contentExtractor.reAnalyzeWithContext(
                    draft.getOriginalDescription(),
                    currentExtraction,
                    request.answers(),
                    request.userFeedback()
                );

                if (reAnalyzed.isValid()) {
                    updateDraftFromExtraction(draft, reAnalyzed);
                    draft.setUserFeedback(request.userFeedback());

                    log.info("Re-analysis successful for draft {}: {} kcal -> {} kcal",
                        draftId, currentExtraction.caloriesKcal(), reAnalyzed.caloriesKcal());
                } else {
                    log.warn("Re-analysis returned invalid result for draft {}: {}",
                        draftId, reAnalyzed.validationError());
                }
            } catch (MealExtractionException e) {
                log.warn("Re-analysis failed for draft {}, keeping original values: {}",
                    draftId, e.getMessage());
                if (hasAnswers) {
                    draft.setAnswers(request.answers());
                }
                if (hasFeedback) {
                    draft.setUserFeedback(request.userFeedback());
                }
            }
        }

        draft.applyUpdate(request);
        draft = draftRepository.save(draft);

        log.info("Updated meal draft {} for device {}", draftId, deviceId.value());

        return mapToResponse(draft);
    }

    private ExtractedMealData buildCurrentExtraction(MealImportDraft draft) {
        return ExtractedMealData.validWithQuestions(
            draft.getSuggestedOccurredAt(),
            draft.getTitle(),
            draft.getDescription(),
            draft.getMealType().name(),
            draft.getCaloriesKcal(),
            draft.getProteinGrams(),
            draft.getFatGrams(),
            draft.getCarbohydratesGrams(),
            draft.getHealthRating().name(),
            draft.getConfidence().doubleValue(),
            draft.getQuestions()
        );
    }

    private void updateDraftFromExtraction(MealImportDraft draft, ExtractedMealData extraction) {
        draft.setTitle(extraction.title());
        draft.setDescription(extraction.description());
        draft.setMealType(MealType.valueOf(extraction.mealType()));
        draft.setCaloriesKcal(extraction.caloriesKcal());
        draft.setProteinGrams(extraction.proteinGrams());
        draft.setFatGrams(extraction.fatGrams());
        draft.setCarbohydratesGrams(extraction.carbohydratesGrams());
        draft.setHealthRating(HealthRating.valueOf(extraction.healthRating()));
        draft.setConfidence(BigDecimal.valueOf(extraction.confidence()));

        if (extraction.occurredAt() != null) {
            draft.setSuggestedOccurredAt(extraction.occurredAt());
        }

        draft.setQuestions(extraction.questions());
    }

    @Override
    @Transactional
    public MealImportResponse confirmDraft(UUID draftId, DeviceId deviceId) {
        MealImportDraft draft = draftRepository.findByIdAndDeviceId(draftId, deviceId.value())
            .orElseThrow(() -> new DraftNotFoundException(draftId));

        if (!draft.isPending()) {
            throw new DraftAlreadyConfirmedException(draftId);
        }

        if (draft.isExpired()) {
            throw new DraftExpiredException(draftId);
        }

        Instant occurredAt = draft.getEffectiveOccurredAt();
        String mealId = generateMealId();

        ExtractedMealData extractedData = ExtractedMealData.valid(
            occurredAt,
            draft.getTitle(),
            draft.getDescription(),
            draft.getMealType().name(),
            draft.getCaloriesKcal(),
            draft.getProteinGrams(),
            draft.getFatGrams(),
            draft.getCarbohydratesGrams(),
            draft.getHealthRating().name(),
            draft.getConfidence().doubleValue()
        );

        StoreHealthEventsCommand.EventEnvelope envelope = eventMapper.mapToEventEnvelope(
            extractedData, mealId, deviceId, occurredAt
        );

        StoreHealthEventsCommand command = new StoreHealthEventsCommand(
            List.of(envelope), deviceId
        );
        StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

        var eventResult = result.results().get(0);
        if (eventResult.status() == StoreHealthEventsResult.EventStatus.invalid) {
            String errorMessage = eventResult.error() != null
                ? eventResult.error().message()
                : "Validation failed";
            log.warn("Meal event validation failed for draft {}: {}", draftId, errorMessage);
            return MealImportResponse.failure("Validation error: " + errorMessage);
        }

        draft.markConfirmed();
        draftRepository.save(draft);

        String eventId = eventResult.eventId() != null
            ? eventResult.eventId().value()
            : null;

        log.info("Confirmed meal draft {} as meal {} for device {}: {} ({} kcal)",
            draftId, mealId, deviceId.value(), draft.getTitle(), draft.getCaloriesKcal());

        return MealImportResponse.success(
            mealId,
            eventId,
            occurredAt,
            draft.getTitle(),
            draft.getMealType().name(),
            draft.getCaloriesKcal(),
            draft.getProteinGrams(),
            draft.getFatGrams(),
            draft.getCarbohydratesGrams(),
            draft.getHealthRating().name(),
            draft.getConfidence().doubleValue()
        );
    }

    private MealDraftResponse mapToResponse(MealImportDraft draft) {
        return MealDraftResponse.success(
            draft.getId().toString(),
            draft.getSuggestedOccurredAt(),
            draft.getDescription(),
            new MealDraftResponse.MealData(
                draft.getTitle(),
                draft.getMealType().name(),
                draft.getCaloriesKcal(),
                draft.getProteinGrams(),
                draft.getFatGrams(),
                draft.getCarbohydratesGrams(),
                draft.getHealthRating().name()
            ),
            draft.getConfidence().doubleValue(),
            draft.getQuestions(),
            draft.getExpiresAt()
        );
    }

    private MealTimeContext buildMealTimeContext() {
        LocalDate today = LocalDate.now(MealTimeContext.POLAND_ZONE);
        LocalTime now = LocalTime.now(MealTimeContext.POLAND_ZONE);

        MealDailyDetailResponse todayMeals = mealsFacade.getDailyDetail(today);

        List<MealTimeContext.TodaysMeal> meals = todayMeals.meals().stream()
            .map(m -> new MealTimeContext.TodaysMeal(
                m.mealType(),
                m.occurredAt(),
                m.title()
            ))
            .toList();

        return new MealTimeContext(now, today, meals);
    }
}
