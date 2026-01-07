package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
class WorkoutProjector {

    private final WorkoutProjectionJpaRepository workoutRepository;
    private final WorkoutSetProjectionJpaRepository setRepository;
    private final WorkoutFactory workoutFactory;

    @Transactional
    public void projectWorkout(StoredEventData eventData) {
        workoutFactory.createFromEvent(eventData).ifPresentOrElse(
                this::saveProjection,
                () -> log.warn("Could not create Workout from event, skipping projection")
        );
    }

    private void saveProjection(Workout workout) {
        try {
            doSaveProjection(workout);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict for workout {}, retrying once", workout.workoutId());
            doSaveProjection(workout);
        }
    }

    private void doSaveProjection(Workout workout) {
        if (workoutRepository.existsByWorkoutId(workout.workoutId())) {
            log.debug("Workout projection already exists for workoutId: {}, skipping", workout.workoutId());
            return;
        }

        workoutRepository.save(WorkoutProjectionJpaEntity.from(workout));

        List<WorkoutSetProjectionJpaEntity> sets = WorkoutProjectionJpaEntity.setsFrom(workout);
        if (!sets.isEmpty()) {
            setRepository.saveAll(sets);
        }

        log.info("Created workout projection for workoutId: {}", workout.workoutId());
    }

    public void deleteByEventId(String eventId) {
        workoutRepository.findByEventId(eventId).ifPresent(entity -> {
            String workoutId = entity.getWorkoutId();
            setRepository.deleteByWorkoutId(workoutId);
            workoutRepository.deleteByEventId(eventId);
            log.info("Deleted workout projection for eventId: {} (workoutId: {})", eventId, workoutId);
        });
    }

    public void projectCorrectedWorkout(String deviceId, java.util.Map<String, Object> payload, java.time.Instant occurredAt) {
        workoutFactory.createFromCorrectionPayload(deviceId, payload, occurredAt)
                .ifPresent(this::saveProjection);
    }
}
