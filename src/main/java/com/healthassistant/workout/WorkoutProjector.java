package com.healthassistant.workout;

import com.healthassistant.healthevents.api.dto.StoredEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
class WorkoutProjector {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 50;

    private final WorkoutProjectionJpaRepository workoutRepository;
    private final WorkoutSetProjectionJpaRepository setRepository;
    private final WorkoutFactory workoutFactory;

    public void projectWorkout(StoredEventData eventData) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                doProjectWorkout(eventData);
                return;
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException e) {
                if (attempt == MAX_RETRIES) {
                    log.error("Deadlock persists after {} retries for workout event {}, giving up",
                            MAX_RETRIES, eventData.eventId().value());
                    throw e;
                }
                long delay = BASE_DELAY_MS * attempt + ThreadLocalRandom.current().nextLong(50);
                log.warn("Deadlock detected for workout event {} (attempt {}/{}), retrying in {}ms",
                        eventData.eventId().value(), attempt, MAX_RETRIES, delay);
                sleep(delay);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doProjectWorkout(StoredEventData eventData) {
        workoutFactory.createFromEvent(eventData).ifPresentOrElse(
                this::saveProjection,
                () -> log.warn("Could not create Workout from event, skipping projection")
        );
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
