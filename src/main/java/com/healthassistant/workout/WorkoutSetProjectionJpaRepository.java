package com.healthassistant.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
interface WorkoutSetProjectionJpaRepository extends JpaRepository<WorkoutSetProjectionJpaEntity, Long> {

    interface PersonalRecordProjection {
        String getExerciseId();
        String getExerciseName();
        BigDecimal getWeightKg();
        String getWorkoutId();
        LocalDate getPerformedDate();
    }

    @Query("""
        SELECT s.exerciseId as exerciseId,
               s.exerciseName as exerciseName,
               s.weightKg as weightKg,
               s.workoutId as workoutId,
               w.performedDate as performedDate
        FROM WorkoutSetProjectionJpaEntity s
        JOIN WorkoutProjectionJpaEntity w ON s.workoutId = w.workoutId
        WHERE w.deviceId = :deviceId
          AND s.isWarmup = false
          AND s.weightKg > 0
        ORDER BY s.exerciseName, s.weightKg DESC, w.performedDate ASC
        """)
    List<PersonalRecordProjection> findAllSetsForPersonalRecords(@Param("deviceId") String deviceId);

    List<WorkoutSetProjectionJpaEntity> findByWorkoutIdOrderByExerciseNameAscSetNumberAsc(String workoutId);

    void deleteByWorkoutId(String workoutId);

    @Query("SELECT s FROM WorkoutSetProjectionJpaEntity s " +
           "WHERE s.workoutId IN :workoutIds AND s.exerciseName IN :exerciseNames " +
           "ORDER BY s.workoutId, s.setNumber")
    List<WorkoutSetProjectionJpaEntity> findByWorkoutIdsAndExerciseNames(
            @Param("workoutIds") List<String> workoutIds,
            @Param("exerciseNames") List<String> exerciseNames
    );

    @Query("SELECT s FROM WorkoutSetProjectionJpaEntity s " +
           "WHERE s.workoutId IN :workoutIds AND s.exerciseId = :exerciseId " +
           "ORDER BY s.workoutId, s.setNumber")
    List<WorkoutSetProjectionJpaEntity> findByWorkoutIdsAndExerciseId(
            @Param("workoutIds") List<String> workoutIds,
            @Param("exerciseId") String exerciseId
    );
}
