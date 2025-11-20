package com.healthassistant.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WorkoutExerciseProjectionJpaRepository extends JpaRepository<WorkoutExerciseProjectionJpaEntity, Long> {

    @Query("SELECT e FROM WorkoutExerciseProjectionJpaEntity e " +
           "JOIN e.workout w " +
           "WHERE e.exerciseName = :exerciseName " +
           "ORDER BY w.performedAt DESC")
    List<WorkoutExerciseProjectionJpaEntity> findByExerciseNameOrderByPerformedAtDesc(
            @Param("exerciseName") String exerciseName
    );

    @Query("SELECT e FROM WorkoutExerciseProjectionJpaEntity e " +
           "JOIN e.workout w " +
           "WHERE e.exerciseName = :exerciseName " +
           "AND w.performedDate BETWEEN :startDate AND :endDate " +
           "ORDER BY w.performedAt DESC")
    List<WorkoutExerciseProjectionJpaEntity> findByExerciseNameAndDateRange(
            @Param("exerciseName") String exerciseName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT DISTINCT e.exerciseName FROM WorkoutExerciseProjectionJpaEntity e " +
           "ORDER BY e.exerciseName")
    List<String> findAllDistinctExerciseNames();

    @Query("SELECT e FROM WorkoutExerciseProjectionJpaEntity e " +
           "JOIN e.workout w " +
           "WHERE e.muscleGroup = :muscleGroup " +
           "ORDER BY w.performedAt DESC")
    List<WorkoutExerciseProjectionJpaEntity> findByMuscleGroupOrderByPerformedAtDesc(
            @Param("muscleGroup") String muscleGroup
    );

    @Query("SELECT e.exerciseName, MAX(e.maxWeightKg) " +
           "FROM WorkoutExerciseProjectionJpaEntity e " +
           "GROUP BY e.exerciseName " +
           "ORDER BY e.exerciseName")
    List<Object[]> findPersonalRecordsByExercise();
}
