package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        if (workoutRepository.existsByWorkoutId(workout.workoutId())) {
            log.debug("Workout projection already exists for workoutId: {}, skipping", workout.workoutId());
            return;
        }

        try {
            workoutRepository.save(WorkoutProjectionJpaEntity.from(workout));

            List<WorkoutSetProjectionJpaEntity> sets = WorkoutProjectionJpaEntity.setsFrom(workout);
            if (!sets.isEmpty()) {
                setRepository.saveAll(sets);
            }

            log.info("Created workout projection for workoutId: {}", workout.workoutId());
        } catch (Exception e) {
            log.error("Failed to create workout projection for workoutId: {}", workout.workoutId(), e);
            throw new WorkoutProjectionException("Failed to project workout: " + workout.workoutId(), e);
        }
    }

    static class WorkoutProjectionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        WorkoutProjectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
