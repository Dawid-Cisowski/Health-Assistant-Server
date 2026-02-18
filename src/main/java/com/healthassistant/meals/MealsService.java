package com.healthassistant.meals;

import tools.jackson.databind.ObjectMapper;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.EventCorrectedPayload;
import com.healthassistant.healthevents.api.dto.payload.EventDeletedPayload;
import com.healthassistant.healthevents.api.dto.payload.MealRecordedPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import com.healthassistant.meals.api.MealsFacade;
import com.healthassistant.meals.api.dto.EnergyRequirementsResponse;
import com.healthassistant.meals.api.dto.MealDailyDetailResponse;
import com.healthassistant.meals.api.dto.MealResponse;
import com.healthassistant.meals.api.dto.MealsRangeSummaryResponse;
import com.healthassistant.meals.api.dto.RecordMealRequest;
import com.healthassistant.meals.api.dto.UpdateMealRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
class MealsService implements MealsFacade {

    private static final int MAX_RANGE_DAYS = 365;
    private static final int MAX_MEALS_PER_DAY = 50;
    private static final int MAX_BACKDATE_DAYS = 30;
    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final String MEAL_V1 = "MealRecorded.v1";
    private static final String EVENT_DELETED_V1 = "EventDeleted.v1";
    private static final String EVENT_CORRECTED_V1 = "EventCorrected.v1";

    private final MealDailyProjectionJpaRepository dailyRepository;
    private final MealProjectionJpaRepository mealRepository;
    private final MealsProjector mealsProjector;
    private final HealthEventsFacade healthEventsFacade;
    private final ObjectMapper objectMapper;
    private final EnergyRequirementsService energyRequirementsService;

    MealsService(
            MealDailyProjectionJpaRepository dailyRepository,
            MealProjectionJpaRepository mealRepository,
            MealsProjector mealsProjector,
            HealthEventsFacade healthEventsFacade,
            ObjectMapper objectMapper,
            @Lazy EnergyRequirementsService energyRequirementsService
    ) {
        this.dailyRepository = dailyRepository;
        this.mealRepository = mealRepository;
        this.mealsProjector = mealsProjector;
        this.healthEventsFacade = healthEventsFacade;
        this.objectMapper = objectMapper;
        this.energyRequirementsService = energyRequirementsService;
    }

    @Override
    public MealDailyDetailResponse getDailyDetail(String deviceId, LocalDate date) {
        return getDailyDetailInternal(deviceId, date)
                .orElseGet(() -> createEmptyDetail(date));
    }

    private Optional<MealDailyDetailResponse> getDailyDetailInternal(String deviceId, LocalDate date) {
        return dailyRepository.findByDeviceIdAndDate(deviceId, date)
                .map(daily -> {
                    List<MealProjectionJpaEntity> meals = mealRepository.findByDeviceIdAndDateOrderByMealNumberAsc(
                            deviceId, date, PageRequest.of(0, MAX_MEALS_PER_DAY));

                    List<MealDailyDetailResponse.MealDetail> mealDetails = meals.stream()
                            .map(m -> new MealDailyDetailResponse.MealDetail(
                                    m.getEventId(), m.getMealNumber(), m.getOccurredAt(), m.getTitle(), m.getMealType(),
                                    m.getCaloriesKcal(), m.getProteinGrams(), m.getFatGrams(),
                                    m.getCarbohydratesGrams(), m.getHealthRating()))
                            .toList();

                    var mealTypeCounts = new MealDailyDetailResponse.MealTypeCounts(
                            daily.getBreakfastCount(), daily.getBrunchCount(), daily.getLunchCount(),
                            daily.getDinnerCount(), daily.getSnackCount(), daily.getDessertCount(), daily.getDrinkCount());

                    var healthRatingCounts = new MealDailyDetailResponse.HealthRatingCounts(
                            daily.getVeryHealthyCount(), daily.getHealthyCount(), daily.getNeutralCount(),
                            daily.getUnhealthyCount(), daily.getVeryUnhealthyCount());

                    return new MealDailyDetailResponse(
                            daily.getDate(), daily.getTotalMealCount(), daily.getTotalCaloriesKcal(),
                            daily.getTotalProteinGrams(), daily.getTotalFatGrams(), daily.getTotalCarbohydratesGrams(),
                            daily.getAverageCaloriesPerMeal(), mealTypeCounts, healthRatingCounts,
                            daily.getFirstMealTime(), daily.getLastMealTime(), mealDetails
                    );
                });
    }

    private MealDailyDetailResponse createEmptyDetail(LocalDate date) {
        return new MealDailyDetailResponse(
                date, 0, 0, 0, 0, 0, 0,
                new MealDailyDetailResponse.MealTypeCounts(0, 0, 0, 0, 0, 0, 0),
                new MealDailyDetailResponse.HealthRatingCounts(0, 0, 0, 0, 0),
                null, null, List.of()
        );
    }

    @Override
    public MealsRangeSummaryResponse getRangeSummary(String deviceId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        List<MealDailyProjectionJpaEntity> dailyData =
                dailyRepository.findByDeviceIdAndDateBetweenOrderByDateAsc(deviceId, startDate, endDate);

        Map<LocalDate, MealDailyProjectionJpaEntity> dataByDate = dailyData.stream()
                .collect(Collectors.toMap(MealDailyProjectionJpaEntity::getDate, Function.identity()));

        List<MealsRangeSummaryResponse.DailyStats> dailyStats = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> mapToDailyStats(date, dataByDate.get(date)))
                .toList();

        RangeTotals totals = dailyStats.stream()
                .reduce(
                        RangeTotals.ZERO,
                        (acc, d) -> acc.add(d.totalMealCount(), d.totalCaloriesKcal(), d.totalProteinGrams(),
                                d.totalFatGrams(), d.totalCarbohydratesGrams(), d.totalMealCount() > 0 ? 1 : 0),
                        RangeTotals::combine
                );

        int averageCaloriesPerDay = totals.daysWithData() > 0 ? totals.calories() / totals.daysWithData() : 0;
        int averageMealsPerDay = totals.daysWithData() > 0 ? totals.mealCount() / totals.daysWithData() : 0;

        MealsRangeSummaryResponse.DayExtreme dayWithMostCalories = dailyStats.stream()
                .filter(d -> d.totalCaloriesKcal() > 0)
                .max((d1, d2) -> Integer.compare(d1.totalCaloriesKcal(), d2.totalCaloriesKcal()))
                .map(d -> new MealsRangeSummaryResponse.DayExtreme(d.date(), d.totalCaloriesKcal()))
                .orElse(null);

        MealsRangeSummaryResponse.DayExtremeMeals dayWithMostMeals = dailyStats.stream()
                .filter(d -> d.totalMealCount() > 0)
                .max((d1, d2) -> Integer.compare(d1.totalMealCount(), d2.totalMealCount()))
                .map(d -> new MealsRangeSummaryResponse.DayExtremeMeals(d.date(), d.totalMealCount()))
                .orElse(null);

        return new MealsRangeSummaryResponse(
                startDate, endDate, totals.mealCount(), totals.daysWithData(),
                totals.calories(), totals.protein(), totals.fat(), totals.carbs(),
                averageCaloriesPerDay, averageMealsPerDay,
                dayWithMostCalories, dayWithMostMeals, dailyStats
        );
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (daysBetween <= 0) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
        if (daysBetween > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range exceeds maximum of " + MAX_RANGE_DAYS + " days");
        }
    }

    private MealsRangeSummaryResponse.DailyStats mapToDailyStats(LocalDate date, MealDailyProjectionJpaEntity dayData) {
        return new MealsRangeSummaryResponse.DailyStats(
                date,
                dayData != null ? dayData.getTotalMealCount() : 0,
                dayData != null ? dayData.getTotalCaloriesKcal() : 0,
                dayData != null ? dayData.getTotalProteinGrams() : 0,
                dayData != null ? dayData.getTotalFatGrams() : 0,
                dayData != null ? dayData.getTotalCarbohydratesGrams() : 0
        );
    }

    @Override
    @Transactional
    public MealResponse recordMeal(String deviceId, RecordMealRequest request) {
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : Instant.now();
        validateBackdating(occurredAt);

        MealRecordedPayload payload = new MealRecordedPayload(
                request.title(),
                request.mealType(),
                request.caloriesKcal(),
                request.proteinGrams(),
                request.fatGrams(),
                request.carbohydratesGrams(),
                request.healthRating()
        );

        String idempotencyKey = generateMealIdempotencyKey(deviceId, occurredAt);

        StoreHealthEventsCommand command = new StoreHealthEventsCommand(
                List.of(new StoreHealthEventsCommand.EventEnvelope(
                        new IdempotencyKey(idempotencyKey),
                        MEAL_V1,
                        occurredAt,
                        payload
                )),
                new DeviceId(deviceId)
        );

        log.info("Recording meal for device {} at {}", sanitizeForLog(deviceId), occurredAt);
        StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

        if (result.results().isEmpty() || result.results().getFirst().status() == StoreHealthEventsResult.EventStatus.INVALID) {
            String error = result.results().isEmpty() ? "No result" :
                    result.results().getFirst().error() != null ?
                            result.results().getFirst().error().message() : "Unknown error";
            throw new IllegalStateException("Failed to record meal: " + error);
        }

        String eventId = result.results().getFirst().eventId().value();
        LocalDate date = toPolandDate(occurredAt);

        return new MealResponse(
                eventId,
                date,
                occurredAt,
                request.title(),
                request.mealType().name(),
                request.caloriesKcal(),
                request.proteinGrams(),
                request.fatGrams(),
                request.carbohydratesGrams(),
                request.healthRating().name()
        );
    }

    @Override
    @Transactional
    public void deleteMeal(String deviceId, String eventId) {
        StoredEventData existingEvent = healthEventsFacade.findEventById(deviceId, eventId)
                .orElseGet(() -> {
                    log.warn("Security: Device {} attempted to delete eventId {} which doesn't exist or belongs to another device",
                            sanitizeForLog(deviceId), sanitizeForLog(eventId));
                    throw new MealNotFoundException(eventId);
                });

        if (!MEAL_V1.equals(existingEvent.eventType().value())) {
            log.warn("Security: Device {} attempted to delete eventId {} which is not a meal event (type: {})",
                    sanitizeForLog(deviceId), sanitizeForLog(eventId), existingEvent.eventType().value());
            throw new MealNotFoundException(eventId);
        }

        EventDeletedPayload payload = new EventDeletedPayload(
                eventId,
                existingEvent.idempotencyKey().value(),
                "User requested deletion"
        );

        String idempotencyKey = deviceId + "|delete|" + eventId;

        StoreHealthEventsCommand command = new StoreHealthEventsCommand(
                List.of(new StoreHealthEventsCommand.EventEnvelope(
                        new IdempotencyKey(idempotencyKey),
                        EVENT_DELETED_V1,
                        Instant.now(),
                        payload
                )),
                new DeviceId(deviceId)
        );

        log.info("Deleting meal {} for device {}", sanitizeForLog(eventId), sanitizeForLog(deviceId));
        StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

        if (result.results().isEmpty() || result.results().getFirst().status() == StoreHealthEventsResult.EventStatus.INVALID) {
            String error = result.results().isEmpty() ? "No result" :
                    result.results().getFirst().error() != null ?
                            result.results().getFirst().error().message() : "Unknown error";
            throw new IllegalStateException("Failed to delete meal: " + error);
        }
    }

    @Override
    @Transactional
    public MealResponse updateMeal(String deviceId, String eventId, UpdateMealRequest request) {
        StoredEventData existingEvent = healthEventsFacade.findEventById(deviceId, eventId)
                .orElseGet(() -> {
                    log.warn("Security: Device {} attempted to update eventId {} which doesn't exist or belongs to another device",
                            sanitizeForLog(deviceId), sanitizeForLog(eventId));
                    throw new MealNotFoundException(eventId);
                });

        if (!MEAL_V1.equals(existingEvent.eventType().value())) {
            log.warn("Security: Device {} attempted to update eventId {} which is not a meal event (type: {})",
                    sanitizeForLog(deviceId), sanitizeForLog(eventId), existingEvent.eventType().value());
            throw new MealNotFoundException(eventId);
        }

        Instant newOccurredAt = request.occurredAt() != null ? request.occurredAt() : existingEvent.occurredAt();
        validateBackdating(newOccurredAt);

        MealRecordedPayload correctedPayload = new MealRecordedPayload(
                request.title(),
                request.mealType(),
                request.caloriesKcal(),
                request.proteinGrams(),
                request.fatGrams(),
                request.carbohydratesGrams(),
                request.healthRating()
        );

        Map<String, Object> payloadMap = objectMapper.convertValue(
                correctedPayload,
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}
        );

        EventCorrectedPayload payload = new EventCorrectedPayload(
                eventId,
                existingEvent.idempotencyKey().value(),
                MEAL_V1,
                payloadMap,
                newOccurredAt,
                "User requested update"
        );

        // Use content-based idempotency key so same correction request is idempotent
        String idempotencyKey = deviceId + "|correct|" + eventId + "|" + newOccurredAt.toEpochMilli();

        StoreHealthEventsCommand command = new StoreHealthEventsCommand(
                List.of(new StoreHealthEventsCommand.EventEnvelope(
                        new IdempotencyKey(idempotencyKey),
                        EVENT_CORRECTED_V1,
                        Instant.now(),
                        payload
                )),
                new DeviceId(deviceId)
        );

        log.info("Updating meal {} for device {}", sanitizeForLog(eventId), sanitizeForLog(deviceId));
        StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

        if (result.results().isEmpty() || result.results().getFirst().status() == StoreHealthEventsResult.EventStatus.INVALID) {
            String error = result.results().isEmpty() ? "No result" :
                    result.results().getFirst().error() != null ?
                            result.results().getFirst().error().message() : "Unknown error";
            throw new IllegalStateException("Failed to update meal: " + error);
        }

        String newEventId = result.results().getFirst().eventId().value();
        LocalDate date = toPolandDate(newOccurredAt);

        return new MealResponse(
                newEventId,
                date,
                newOccurredAt,
                request.title(),
                request.mealType().name(),
                request.caloriesKcal(),
                request.proteinGrams(),
                request.fatGrams(),
                request.carbohydratesGrams(),
                request.healthRating().name()
        );
    }

    private void validateBackdating(Instant occurredAt) {
        Instant now = Instant.now();

        if (occurredAt.isAfter(now)) {
            throw new BackdatingValidationException("Meal date cannot be in the future");
        }

        long daysBetween = ChronoUnit.DAYS.between(occurredAt, now);
        if (daysBetween > MAX_BACKDATE_DAYS) {
            throw new BackdatingValidationException(
                    "Meal date cannot be more than " + MAX_BACKDATE_DAYS + " days in the past"
            );
        }
    }

    private String generateMealIdempotencyKey(String deviceId, Instant occurredAt) {
        // Use millisecond precision to allow idempotency within same millisecond
        return deviceId + "|" + MEAL_V1 + "|" + occurredAt.toEpochMilli();
    }

    private LocalDate toPolandDate(Instant instant) {
        return instant != null ? instant.atZone(POLAND_ZONE).toLocalDate() : null;
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        boolean hasData = mealRepository.existsByDeviceIdAndDate(deviceId, date);
        if (hasData) {
            log.debug("Deleting meal projections for device {} date {}", sanitizeForLog(deviceId), date);
            mealRepository.deleteByDeviceIdAndDate(deviceId, date);
            dailyRepository.deleteByDeviceIdAndDate(deviceId, date);
        } else {
            log.debug("No meal projections to delete for device {} date {}", sanitizeForLog(deviceId), date);
        }
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} meal events directly", events.size());
        events.forEach(event -> {
            try {
                mealsProjector.projectMeal(event);
            } catch (Exception e) {
                log.error("Failed to project meal event: {}", sanitizeForLog(event.eventId().value()), e);
            }
        });
    }

    @Override
    public Optional<EnergyRequirementsResponse> getEnergyRequirements(String deviceId, LocalDate date) {
        return energyRequirementsService.calculateEnergyRequirements(new DeviceId(deviceId), date);
    }

    private String sanitizeForLog(String value) {
        if (value == null) return "null";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private record RangeTotals(int mealCount, int calories, int protein, int fat, int carbs, int daysWithData) {
        static final RangeTotals ZERO = new RangeTotals(0, 0, 0, 0, 0, 0);

        RangeTotals add(int meals, int cal, int prot, int f, int c, int days) {
            return new RangeTotals(
                    mealCount + meals,
                    calories + cal,
                    protein + prot,
                    fat + f,
                    carbs + c,
                    daysWithData + days
            );
        }

        static RangeTotals combine(RangeTotals a, RangeTotals b) {
            return new RangeTotals(
                    a.mealCount + b.mealCount,
                    a.calories + b.calories,
                    a.protein + b.protein,
                    a.fat + b.fat,
                    a.carbs + b.carbs,
                    a.daysWithData + b.daysWithData
            );
        }
    }
}
