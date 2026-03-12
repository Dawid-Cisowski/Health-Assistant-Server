package com.healthassistant.mealimport;

import tools.jackson.databind.ObjectMapper;
import com.healthassistant.config.AiMetricsRecorder;
import com.healthassistant.config.ImageValidationUtils;
import com.healthassistant.config.SecurityUtils;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import com.healthassistant.mealcatalog.api.MealCatalogFacade;
import com.healthassistant.mealcatalog.api.dto.CatalogProductResponse;
import com.healthassistant.mealcatalog.api.dto.SaveProductRequest;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.mealimport.api.MealImportFacade;
import com.healthassistant.mealimport.api.dto.MealDraftResponse;
import com.healthassistant.mealimport.api.dto.MealDraftUpdateRequest;
import com.healthassistant.mealimport.api.dto.MealImportJobResponse;
import com.healthassistant.mealimport.api.dto.MealImportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
class MealImportService implements MealImportFacade {

    private static final long MAX_TOTAL_SIZE = 50L * 1024 * 1024;
    private static final int MAX_FILES_COUNT = 5;

    private final MealContentExtractor contentExtractor;
    private final MealEventMapper eventMapper;
    private final HealthEventsFacade healthEventsFacade;
    private final MealImportDraftRepository draftRepository;
    private final MealImportJobRepository jobRepository;
    private final MealImportJobProcessor jobProcessor;
    private final MealsFacade mealsFacade;
    private final MealCatalogFacade mealCatalogFacade;
    private final AiMetricsRecorder aiMetrics;
    private final ObjectMapper objectMapper;

    MealImportService(
            MealContentExtractor contentExtractor,
            MealEventMapper eventMapper,
            HealthEventsFacade healthEventsFacade,
            MealImportDraftRepository draftRepository,
            MealImportJobRepository jobRepository,
            @Lazy MealImportJobProcessor jobProcessor,
            MealsFacade mealsFacade,
            MealCatalogFacade mealCatalogFacade,
            AiMetricsRecorder aiMetrics,
            ObjectMapper objectMapper
    ) {
        this.contentExtractor = contentExtractor;
        this.eventMapper = eventMapper;
        this.healthEventsFacade = healthEventsFacade;
        this.draftRepository = draftRepository;
        this.jobRepository = jobRepository;
        this.jobProcessor = jobProcessor;
        this.mealsFacade = mealsFacade;
        this.mealCatalogFacade = mealCatalogFacade;
        this.aiMetrics = aiMetrics;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public String submitImportJob(String description, List<MultipartFile> images, DeviceId deviceId) {
        validateInput(description, images);
        if (images != null && !images.isEmpty()) {
            images.forEach(ImageValidationUtils::validateImage);
        }

        List<MealImportJob.ImageEntry> imageData = serializeImages(images);

        MealImportJob job = MealImportJob.createImportJob(deviceId.value(), description, imageData);
        job = jobRepository.save(job);

        UUID jobId = job.getId();
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    jobProcessor.processJob(jobId);
                }
            }
        );

        return jobId.toString();
    }

    @Override
    @Transactional
    public String submitAnalyzeJob(String description, List<MultipartFile> images, DeviceId deviceId) {
        validateInput(description, images);
        if (images != null && !images.isEmpty()) {
            images.forEach(ImageValidationUtils::validateImage);
        }

        List<MealImportJob.ImageEntry> imageData = serializeImages(images);

        MealImportJob job = MealImportJob.createAnalyzeJob(deviceId.value(), description, imageData);
        job = jobRepository.save(job);

        UUID jobId = job.getId();
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    jobProcessor.processJob(jobId);
                }
            }
        );

        return jobId.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public MealImportJobResponse getJobStatus(UUID jobId, DeviceId deviceId) {
        MealImportJob job = jobRepository.findByIdAndDeviceId(jobId, deviceId.value())
            .orElseThrow(() -> new JobNotFoundException(jobId));

        return switch (job.getStatus()) {
            case PENDING -> MealImportJobResponse.pending(jobId.toString(), job.getJobType().name());
            case PROCESSING -> MealImportJobResponse.processing(jobId.toString(), job.getJobType().name());
            case DONE -> {
                Object result = parseResult(job.getResult(), job.getJobType());
                yield MealImportJobResponse.done(jobId.toString(), job.getJobType().name(), result);
            }
            case FAILED -> MealImportJobResponse.failed(jobId.toString(), job.getJobType().name(), job.getErrorMessage());
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    MealImportResponse executeImport(String description, List<MultipartFile> images, DeviceId deviceId) {
        var sample = aiMetrics.startTimer();
        if (images != null) {
            aiMetrics.recordImportImageCount("meal", images.size());
        }

        try {
            MealTimeContext timeContext = buildMealTimeContext(deviceId.value());
            var catalogProducts = fetchCatalogProducts(deviceId.value());
            ExtractedMealData extractedData = contentExtractor.extract(description, images, timeContext, catalogProducts);
            if (!extractedData.isValid()) {
                log.warn("Meal extraction invalid for device {}: {}",
                    SecurityUtils.maskDeviceId(deviceId.value()), extractedData.validationError());
                aiMetrics.recordImportRequest("meal", sample, "error", "direct");
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

            saveItemsToCatalog(deviceId.value(), extractedData);

            aiMetrics.recordImportConfidence("meal", extractedData.confidence());
            aiMetrics.recordImportTokens("meal", extractedData.promptTokens(), extractedData.completionTokens());
            aiMetrics.recordImportRequest("meal", sample, "success", "direct");
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
                extractedData.completionTokens(),
                mapItems(extractedData.items())
            );

        } catch (MealExtractionException e) {
            log.warn("Meal extraction failed for device {}: {}", SecurityUtils.maskDeviceId(deviceId.value()), e.getMessage());
            aiMetrics.recordImportRequest("meal", sample, "error", "direct");
            return MealImportResponse.failure(e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    MealDraftResponse executeAnalyze(String description, List<MultipartFile> images, DeviceId deviceId) {
        var sample = aiMetrics.startTimer();

        try {
            MealTimeContext timeContext = buildMealTimeContext(deviceId.value());
            var catalogProducts = fetchCatalogProducts(deviceId.value());
            ExtractedMealData extractedData = contentExtractor.extract(description, images, timeContext, catalogProducts);
            if (!extractedData.isValid()) {
                log.warn("Meal extraction invalid for device {}: {}",
                    SecurityUtils.maskDeviceId(deviceId.value()), extractedData.validationError());
                aiMetrics.recordImportRequest("meal", sample, "error", "draft");
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

            aiMetrics.recordImportConfidence("meal", extractedData.confidence());
            aiMetrics.recordImportRequest("meal", sample, "success", "draft");
            return mapToResponseWithItems(draft, extractedData.items());

        } catch (MealExtractionException e) {
            log.warn("Meal extraction failed for device {}: {}", SecurityUtils.maskDeviceId(deviceId.value()), e.getMessage());
            aiMetrics.recordImportRequest("meal", sample, "error", "draft");
            return MealDraftResponse.failure(e.getMessage());
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
                throw new IllegalArgumentException("Total images size exceeds 50MB limit");
            }
        }
    }

    private String generateMealId() {
        return "meal-" + UUID.randomUUID().toString().substring(0, 12);
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
                    aiMetrics.recordReanalysis("success");

                    log.info("Re-analysis successful for draft {}: {} kcal -> {} kcal",
                        draftId, currentExtraction.caloriesKcal(), reAnalyzed.caloriesKcal());
                } else {
                    log.warn("Re-analysis returned invalid result for draft {}: {}",
                        draftId, reAnalyzed.validationError());
                    aiMetrics.recordReanalysis("invalid");
                }
            } catch (MealExtractionException e) {
                log.warn("Re-analysis failed for draft {}, keeping original values. User should verify data is current. Error: {}",
                    draftId, e.getMessage());
                aiMetrics.recordReanalysis("error");
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
        aiMetrics.recordDraftAction("confirmed");
        draftRepository.save(draft);

        String eventId = eventResult.eventId() != null
            ? eventResult.eventId().value()
            : null;

        log.info("Confirmed meal draft {} as meal {} for device {}: {} ({} kcal)",
            draftId, mealId, SecurityUtils.maskDeviceId(deviceId.value()), draft.getTitle(), draft.getCaloriesKcal());

        saveSingleProductToCatalog(deviceId.value(), draft.getTitle(), draft.getMealType().name(),
                draft.getCaloriesKcal(), draft.getProteinGrams(), draft.getFatGrams(),
                draft.getCarbohydratesGrams(), draft.getHealthRating().name());

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

    private MealDraftResponse mapToResponseWithItems(MealImportDraft draft, List<ExtractedMealData.ExtractedItem> items) {
        List<MealDraftResponse.DraftItem> draftItems = List.of();
        if (items != null && !items.isEmpty()) {
            draftItems = items.stream()
                    .map(item -> new MealDraftResponse.DraftItem(
                            item.title(), item.source(), item.caloriesKcal(),
                            item.proteinGrams(), item.fatGrams(), item.carbohydratesGrams(),
                            item.healthRating()
                    ))
                    .toList();
        }
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
            draft.getExpiresAt(),
            draftItems
        );
    }

    private List<MealImportJob.ImageEntry> serializeImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
            .map(img -> {
                try {
                    String base64 = Base64.getEncoder().encodeToString(img.getBytes());
                    return new MealImportJob.ImageEntry(base64, img.getContentType(), img.getOriginalFilename());
                } catch (IOException e) {
                    throw new IllegalArgumentException("Failed to read image: " + e.getMessage(), e);
                }
            })
            .toList();
    }

    private Object parseResult(String resultJson, MealImportJobType jobType) {
        if (resultJson == null) return null;
        try {
            if (jobType == MealImportJobType.IMPORT) {
                return objectMapper.readValue(resultJson, MealImportResponse.class);
            } else {
                return objectMapper.readValue(resultJson, MealDraftResponse.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse job result JSON: {}", e.getMessage());
            return null;
        }
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

    private List<CatalogProductResponse> fetchCatalogProducts(String deviceId) {
        try {
            return mealCatalogFacade.getTopProducts(deviceId, 30);
        } catch (Exception e) {
            log.warn("Failed to fetch catalog products for device {}: {}",
                    SecurityUtils.maskDeviceId(deviceId), e.getMessage());
            return List.of();
        }
    }

    private void saveItemsToCatalog(String deviceId, ExtractedMealData data) {
        if (data.items() == null || data.items().isEmpty()) {
            saveSingleProductToCatalog(deviceId, data.title(), data.mealType(),
                    data.caloriesKcal(), data.proteinGrams(), data.fatGrams(),
                    data.carbohydratesGrams(), data.healthRating());
            return;
        }

        data.items().forEach(item -> {
            try {
                mealCatalogFacade.saveProduct(deviceId, new SaveProductRequest(
                        item.title(), data.mealType(), item.caloriesKcal(),
                        item.proteinGrams(), item.fatGrams(), item.carbohydratesGrams(),
                        item.healthRating()
                ));
            } catch (Exception e) {
                log.warn("Failed to save catalog item '{}' for device {}: {}",
                        SecurityUtils.sanitizeForLog(item.title()),
                        SecurityUtils.maskDeviceId(deviceId),
                        e.getMessage());
            }
        });
    }

    private void saveSingleProductToCatalog(String deviceId, String title, String mealType,
                                            Integer caloriesKcal, Integer proteinGrams,
                                            Integer fatGrams, Integer carbohydratesGrams,
                                            String healthRating) {
        try {
            mealCatalogFacade.saveProduct(deviceId, new SaveProductRequest(
                    title, mealType, caloriesKcal, proteinGrams, fatGrams,
                    carbohydratesGrams, healthRating
            ));
        } catch (Exception e) {
            log.warn("Failed to save product to catalog for device {}: {}",
                    SecurityUtils.maskDeviceId(deviceId), e.getMessage());
        }
    }

    private List<MealImportResponse.ImportedItem> mapItems(List<ExtractedMealData.ExtractedItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new MealImportResponse.ImportedItem(
                        item.title(), item.source(), item.caloriesKcal(),
                        item.proteinGrams(), item.fatGrams(), item.carbohydratesGrams(),
                        item.healthRating()
                ))
                .toList();
    }
}
