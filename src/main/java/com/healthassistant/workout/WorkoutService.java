package com.healthassistant.workout;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.payload.EventCorrectedPayload;
import com.healthassistant.healthevents.api.dto.payload.EventDeletedPayload;
import com.healthassistant.healthevents.api.dto.payload.WorkoutPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import com.healthassistant.workout.api.WorkoutFacade;
import com.healthassistant.workout.api.dto.ExerciseDefinition;
import com.healthassistant.workout.api.dto.PersonalRecordsResponse;
import com.healthassistant.workout.api.dto.UpdateWorkoutRequest;
import com.healthassistant.workout.api.dto.WorkoutDetailResponse;
import com.healthassistant.workout.api.dto.WorkoutMutationResponse;
import com.healthassistant.workout.api.dto.WorkoutReprojectionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
class WorkoutService implements WorkoutFacade {

    private static final int MAX_BACKDATE_DAYS = 30;
    private static final String WORKOUT_V1 = "WorkoutRecorded.v1";
    private static final String EVENT_DELETED_V1 = "EventDeleted.v1";
    private static final String EVENT_CORRECTED_V1 = "EventCorrected.v1";

    private final WorkoutProjectionJpaRepository workoutRepository;
    private final WorkoutExerciseProjectionJpaRepository exerciseRepository;
    private final WorkoutSetProjectionJpaRepository setRepository;
    private final WorkoutProjector workoutProjector;
    private final ExerciseCatalog exerciseCatalog;
    private final ExerciseDefinitionRepository exerciseDefinitionRepository;
    private final ExerciseStatisticsService exerciseStatisticsService;
    private final HealthEventsFacade healthEventsFacade;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<WorkoutDetailResponse> getWorkoutDetails(String deviceId, String workoutId) {
        Optional<WorkoutProjectionJpaEntity> workoutOpt = workoutRepository.findByDeviceIdAndWorkoutId(deviceId, workoutId);

        if (workoutOpt.isEmpty()) {
            return Optional.empty();
        }

        WorkoutProjectionJpaEntity workout = workoutOpt.get();
        List<WorkoutExerciseProjectionJpaEntity> exercises = workout.getExercises();
        List<WorkoutSetProjectionJpaEntity> sets = setRepository.findByWorkoutIdOrderByExerciseNameAscSetNumberAsc(workoutId);

        var setsByExercise = sets.stream()
                .collect(Collectors.groupingBy(WorkoutSetProjectionJpaEntity::getExerciseName));

        List<WorkoutDetailResponse.ExerciseDetail> exerciseDetails = exercises.stream()
                .sorted(Comparator.comparing(WorkoutExerciseProjectionJpaEntity::getOrderInWorkout))
                .map(exercise -> {
                    List<WorkoutSetProjectionJpaEntity> exerciseSets = setsByExercise.getOrDefault(
                            exercise.getExerciseName(),
                            List.of()
                    );

                    List<WorkoutDetailResponse.SetDetail> setDetails = exerciseSets.stream()
                            .sorted(Comparator.comparing(WorkoutSetProjectionJpaEntity::getSetNumber))
                            .map(set -> new WorkoutDetailResponse.SetDetail(
                                    set.getSetNumber(), set.getWeightKg(), set.getReps(),
                                    set.getIsWarmup(), set.getVolumeKg()))
                            .toList();

                    return new WorkoutDetailResponse.ExerciseDetail(
                            exercise.getExerciseId(), exercise.getExerciseName(),
                            exercise.getMuscleGroup(), exercise.getOrderInWorkout(),
                            exercise.getTotalSets(), exercise.getTotalVolumeKg(),
                            exercise.getMaxWeightKg(), setDetails);
                })
                .toList();

        return Optional.of(new WorkoutDetailResponse(
                workout.getEventId(), workout.getWorkoutId(), workout.getPerformedAt(), workout.getPerformedDate(),
                workout.getSource(), workout.getNote(), workout.getTotalExercises(),
                workout.getTotalSets(), workout.getTotalVolumeKg(), workout.getTotalWorkingVolumeKg(),
                exerciseDetails));
    }

    @Override
    public List<WorkoutDetailResponse> getWorkoutsByDateRange(String deviceId, LocalDate startDate, LocalDate endDate) {
        List<WorkoutProjectionJpaEntity> workouts = workoutRepository.findByDeviceIdAndPerformedDateBetweenOrderByPerformedAtDesc(
                deviceId,
                startDate,
                endDate
        );

        return workouts.stream()
                .map(workout -> {
                    List<WorkoutDetailResponse.ExerciseDetail> exerciseDetails = workout.getExercises().stream()
                            .sorted(Comparator.comparing(WorkoutExerciseProjectionJpaEntity::getOrderInWorkout))
                            .map(exercise -> new WorkoutDetailResponse.ExerciseDetail(
                                    exercise.getExerciseId(), exercise.getExerciseName(),
                                    exercise.getMuscleGroup(), exercise.getOrderInWorkout(),
                                    exercise.getTotalSets(), exercise.getTotalVolumeKg(),
                                    exercise.getMaxWeightKg(), List.of()))
                            .toList();

                    return new WorkoutDetailResponse(
                            workout.getEventId(), workout.getWorkoutId(), workout.getPerformedAt(), workout.getPerformedDate(),
                            workout.getSource(), workout.getNote(), workout.getTotalExercises(),
                            workout.getTotalSets(), workout.getTotalVolumeKg(), workout.getTotalWorkingVolumeKg(),
                            exerciseDetails);
                })
                .toList();
    }

    @Override
    public boolean hasWorkoutOnDate(String deviceId, LocalDate date) {
        return workoutRepository.existsByDeviceIdAndPerformedDate(deviceId, date);
    }

    @Override
    @Transactional
    public void deleteProjectionsForDate(String deviceId, LocalDate date) {
        log.debug("Deleting workout projections for device {} date {}", deviceId, date);
        workoutRepository.deleteByDeviceIdAndPerformedDate(deviceId, date);
    }

    @Override
    @Transactional
    public void projectEvents(List<StoredEventData> events) {
        log.debug("Projecting {} workout events directly", events.size());
        events.forEach(event -> {
            try {
                workoutProjector.projectWorkout(event);
            } catch (Exception e) {
                log.error("Failed to project workout event: {}", event.eventId().value(), e);
            }
        });
    }

    @Override
    public List<ExerciseDefinition> getAllExercises() {
        return exerciseCatalog.getAllExercises();
    }

    @Override
    public boolean exerciseExists(String exerciseId) {
        return exerciseDefinitionRepository.existsById(exerciseId);
    }

    @Override
    @Transactional
    public ExerciseDefinition createAutoExercise(String id, String name, String description,
                                                 String primaryMuscle, List<String> muscles) {
        ExerciseDefinitionEntity entity = ExerciseDefinitionEntity.createAutoCreated(
                id, name, description, primaryMuscle, muscles
        );
        ExerciseDefinitionEntity saved = exerciseDefinitionRepository.save(entity);
        log.info("Created auto-generated exercise: id={}, name={}", id, name);
        return new ExerciseDefinition(
                saved.getId(), saved.getName(), saved.getDescription(),
                saved.getPrimaryMuscle(), saved.getMuscles()
        );
    }

    @Override
    public PersonalRecordsResponse getAllPersonalRecords(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        if (deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId cannot be blank");
        }
        return exerciseStatisticsService.getAllPersonalRecords(deviceId);
    }

    @Override
    @Transactional
    public void deleteWorkout(String deviceId, String eventId) {
        StoredEventData existingEvent = healthEventsFacade.findEventById(deviceId, eventId)
                .orElseThrow(() -> {
                    log.warn("Security: Device {} attempted to delete eventId {} which doesn't exist or belongs to another device",
                            sanitizeForLog(deviceId), sanitizeForLog(eventId));
                    return new WorkoutNotFoundException(eventId);
                });

        if (!WORKOUT_V1.equals(existingEvent.eventType().value())) {
            log.warn("Security: Device {} attempted to delete eventId {} which is not a workout event (type: {})",
                    sanitizeForLog(deviceId), sanitizeForLog(eventId), sanitizeForLog(existingEvent.eventType().value()));
            throw new WorkoutNotFoundException(eventId);
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

        log.info("Deleting workout {} for device {}", sanitizeForLog(eventId), sanitizeForLog(deviceId));
        StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

        if (result.results().isEmpty() || result.results().getFirst().status() == StoreHealthEventsResult.EventStatus.invalid) {
            String errorDetail = result.results().isEmpty() ? "No result" :
                    result.results().getFirst().error() != null ?
                            result.results().getFirst().error().message() : "Unknown error";
            log.error("Failed to delete workout eventId={}: {}", sanitizeForLog(eventId), errorDetail);
            throw new IllegalStateException("Failed to delete workout");
        }
    }

    @Override
    @Transactional
    public WorkoutMutationResponse updateWorkout(String deviceId, String eventId, UpdateWorkoutRequest request) {
        StoredEventData existingEvent = healthEventsFacade.findEventById(deviceId, eventId)
                .orElseThrow(() -> {
                    log.warn("Security: Device {} attempted to update eventId {} which doesn't exist or belongs to another device",
                            sanitizeForLog(deviceId), sanitizeForLog(eventId));
                    return new WorkoutNotFoundException(eventId);
                });

        if (!WORKOUT_V1.equals(existingEvent.eventType().value())) {
            log.warn("Security: Device {} attempted to update eventId {} which is not a workout event (type: {})",
                    sanitizeForLog(deviceId), sanitizeForLog(eventId), sanitizeForLog(existingEvent.eventType().value()));
            throw new WorkoutNotFoundException(eventId);
        }

        validateBackdating(request.performedAt());

        String workoutId = extractWorkoutId(existingEvent);
        WorkoutPayload correctedPayload = buildCorrectedWorkoutPayload(workoutId, request);

        Map<String, Object> payloadMap = objectMapper.convertValue(
                correctedPayload,
                new TypeReference<Map<String, Object>>() {}
        );

        EventCorrectedPayload payload = new EventCorrectedPayload(
                eventId,
                existingEvent.idempotencyKey().value(),
                WORKOUT_V1,
                payloadMap,
                request.performedAt(),
                "User requested update"
        );

        String idempotencyKey = deviceId + "|correct|" + eventId + "|" + request.performedAt().toEpochMilli();

        StoreHealthEventsCommand command = new StoreHealthEventsCommand(
                List.of(new StoreHealthEventsCommand.EventEnvelope(
                        new IdempotencyKey(idempotencyKey),
                        EVENT_CORRECTED_V1,
                        Instant.now(),
                        payload
                )),
                new DeviceId(deviceId)
        );

        log.info("Updating workout {} for device {}", sanitizeForLog(eventId), sanitizeForLog(deviceId));
        StoreHealthEventsResult result = healthEventsFacade.storeHealthEvents(command);

        if (result.results().isEmpty() || result.results().getFirst().status() == StoreHealthEventsResult.EventStatus.invalid) {
            String errorDetail = result.results().isEmpty() ? "No result" :
                    result.results().getFirst().error() != null ?
                            result.results().getFirst().error().message() : "Unknown error";
            log.error("Failed to update workout eventId={}: {}", sanitizeForLog(eventId), errorDetail);
            throw new IllegalStateException("Failed to update workout");
        }

        String newEventId = result.results().getFirst().eventId().value();
        int totalSets = request.exercises().stream()
                .mapToInt(e -> e.sets().size())
                .sum();

        return new WorkoutMutationResponse(
                newEventId,
                workoutId,
                request.performedAt(),
                request.exercises().size(),
                totalSets
        );
    }

    private void validateBackdating(Instant performedAt) {
        Instant now = Instant.now();

        if (performedAt.isAfter(now)) {
            throw new WorkoutBackdatingValidationException("Workout date cannot be in the future");
        }

        long daysBetween = ChronoUnit.DAYS.between(performedAt, now);
        if (daysBetween > MAX_BACKDATE_DAYS) {
            throw new WorkoutBackdatingValidationException(
                    "Workout date cannot be more than " + MAX_BACKDATE_DAYS + " days in the past"
            );
        }
    }

    private String extractWorkoutId(StoredEventData eventData) {
        if (eventData.payload() instanceof WorkoutPayload workoutPayload) {
            return workoutPayload.workoutId();
        }
        // Fallback: try to convert through ObjectMapper
        Map<String, Object> payloadMap = objectMapper.convertValue(
                eventData.payload(),
                new TypeReference<Map<String, Object>>() {}
        );
        if (payloadMap != null && payloadMap.containsKey("workoutId")) {
            return String.valueOf(payloadMap.get("workoutId"));
        }
        return "workout-" + UUID.randomUUID();
    }

    private WorkoutPayload buildCorrectedWorkoutPayload(String workoutId, UpdateWorkoutRequest request) {
        List<WorkoutPayload.Exercise> exercises = request.exercises().stream()
                .map(e -> new WorkoutPayload.Exercise(
                        e.name(),
                        e.exerciseId(),
                        e.muscleGroup(),
                        e.orderInWorkout(),
                        e.sets().stream()
                                .map(s -> new WorkoutPayload.ExerciseSet(
                                        s.setNumber(),
                                        s.weightKg(),
                                        s.reps(),
                                        s.isWarmup()
                                ))
                                .toList()
                ))
                .toList();

        return new WorkoutPayload(
                workoutId,
                request.performedAt(),
                request.source(),
                request.note(),
                exercises
        );
    }

    private String sanitizeForLog(String value) {
        if (value == null) return "null";
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) {
            return "***";
        }
        return deviceId.substring(0, 4) + "..." + deviceId.substring(deviceId.length() - 4);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WorkoutReprojectionResponse reprojectAllWorkouts(String deviceId) {
        log.info("Starting workout reprojection for device {}", maskDeviceId(deviceId));

        workoutRepository.deleteByDeviceId(deviceId);

        List<StoredEventData> events = healthEventsFacade
                .findActiveEventsByDeviceIdAndEventType(deviceId, WORKOUT_V1);

        int[] counter = {0, 0};
        events.forEach(event -> {
            try {
                workoutProjector.projectWorkout(event);
                counter[0]++;
            } catch (Exception e) {
                log.error("Failed to reproject event {}: {}", event.eventId().value(), e.getMessage());
                counter[1]++;
            }
        });

        log.info("Reprojection completed: {} success, {} failed, {} total",
                counter[0], counter[1], events.size());

        return new WorkoutReprojectionResponse(counter[0], counter[1], events.size());
    }

}
