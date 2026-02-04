package com.healthassistant.mealimport;

import com.healthassistant.config.ImageValidationUtils;
import com.healthassistant.config.ImportConstants;
import com.healthassistant.config.SecurityUtils;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.mealimport.api.MealImportFacade;
import com.healthassistant.mealimport.api.dto.MealDraftResponse;
import com.healthassistant.mealimport.api.dto.MealDraftUpdateRequest;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class MealImportService implements MealImportFacade {

    private static final long MAX_TOTAL_SIZE = 30L * 1024 * 1024;
    private static final int MAX_FILES_COUNT = 5;

    private final MealContentExtractor contentExtractor;
    private final MealEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;
    private final MealImportDraftRepository draftRepository;
    private final MealsFacade mealsFacade;

    @Override
    @Transactional
    public MealImportResponse importMeal(String description, List<MultipartFile> images, DeviceId deviceId) {
        validateInput(description, images);

        if (images != null && !images.isEmpty()) {
            images.forEach(ImageValidationUtils::validateImage);
        }

        try {
            MealTimeContext timeContext = buildMealTimeContext(deviceId.value());
            ExtractedMealData extractedData = contentExtractor.extract(description, images, timeContext);
            if (!extractedData.isValid()) {
                log.warn("Meal extraction invalid for device {}: {}",
                    SecurityUtils.maskDeviceId(deviceId.value()), extractedData.validationError());
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

            log.info("Successfully imported meal {} for device {}: {} ({} kcal), status={}, tokens: {}/{}",
                mealId, SecurityUtils.maskDeviceId(deviceId.value()), extractedData.title(),
                extractedData.caloriesKcal(), eventResult.status(),
                extractedData.promptTokens(), extractedData.completionTokens());

            return MealImportResponse.successWithTokens(
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
                extractedData.confidence(),
                extractedData.promptTokens(),
                extractedData.completionTokens()
            );

        } catch (MealExtractionException e) {
            log.warn("Meal extraction failed for device {}: {}", SecurityUtils.maskDeviceId(deviceId.value()), e.getMessage());
            return MealImportResponse.failure(e.getMessage());
        }
    }

    private void validateInput(String description, List<MultipartFile> images) {
        boolean hasDescription = description != null && !description.isBlank();
        boolean hasImages = images != null && !images.isEmpty();

        if (!hasDescription && !hasImages) {
            throw new IllegalArgumentException("Either description or images required");
        }

        if (images != null && images.size() > MAX_FILES_COUNT) {
            throw new IllegalArgumentException("Maximum " + MAX_FILES_COUNT + " images allowed");
        }

        if (images != null) {
            long totalSize = images.stream().mapToLong(MultipartFile::getSize).sum();
            if (totalSize > MAX_TOTAL_SIZE) {
                throw new IllegalArgumentException("Total images size exceeds 30MB limit");
            }
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
            images.forEach(ImageValidationUtils::validateImage);
        }

        try {
            MealTimeContext timeContext = buildMealTimeContext(deviceId.value());
            ExtractedMealData extractedData = contentExtractor.extract(description, images, timeContext);
            if (!extractedData.isValid()) {
                log.warn("Meal extraction invalid for device {}: {}",
                    SecurityUtils.maskDeviceId(deviceId.value()), extractedData.validationError());
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
                draft.getId(), SecurityUtils.maskDeviceId(deviceId.value()), draft.getTitle(),
                draft.getCaloriesKcal(), draft.getConfidence());

            return mapToResponse(draft);

        } catch (MealExtractionException e) {
            log.warn("Meal extraction failed for device {}: {}", SecurityUtils.maskDeviceId(deviceId.value()), e.getMessage());
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
                    draft.updateFromExtraction(reAnalyzed);
                    draft.recordUserContext(request.answers(), request.userFeedback());

                    log.info("Re-analysis successful for draft {}: {} kcal -> {} kcal",
                        draftId, currentExtraction.caloriesKcal(), reAnalyzed.caloriesKcal());
                } else {
                    log.warn("Re-analysis returned invalid result for draft {}: {}",
                        draftId, reAnalyzed.validationError());
                }
            } catch (MealExtractionException e) {
                log.warn("Re-analysis failed for draft {}, keeping original values. User should verify data is current. Error: {}",
                    draftId, e.getMessage());
                draft.recordUserContext(request.answers(), request.userFeedback());
            }
        }

        draft.applyUpdate(request);
        draft = draftRepository.save(draft);

        log.info("Updated meal draft {} for device {}", draftId, SecurityUtils.maskDeviceId(deviceId.value()));

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
            draftId, mealId, SecurityUtils.maskDeviceId(deviceId.value()), draft.getTitle(), draft.getCaloriesKcal());

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

    private MealTimeContext buildMealTimeContext(String deviceId) {
        LocalDate today = LocalDate.now(MealTimeContext.POLAND_ZONE);
        LocalTime now = LocalTime.now(MealTimeContext.POLAND_ZONE);

        MealDailyDetailResponse todayMeals = mealsFacade.getDailyDetail(deviceId, today);

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
