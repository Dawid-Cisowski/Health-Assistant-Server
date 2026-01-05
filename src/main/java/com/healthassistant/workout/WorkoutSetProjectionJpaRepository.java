package com.healthassistant.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
interface WorkoutSetProjectionJpaRepository extends JpaRepository<WorkoutSetProjectionJpaEntity, Long> {

    List<WorkoutSetProjectionJpaEntity> findByWorkoutIdOrderByExerciseNameAscSetNumberAsc(String workoutId);

    void deleteByWorkoutId(String workoutId);

    @Query("SELECT s FROM WorkoutSetProjectionJpaEntity s " +
           "WHERE s.workoutId IN :workoutIds AND s.exerciseName IN :exerciseNames " +
           "ORDER BY s.workoutId, s.setNumber")
    List<WorkoutSetProjectionJpaEntity> findByWorkoutIdsAndExerciseNames(
            @Param("workoutIds") List<String> workoutIds,
            @Param("exerciseNames") List<String> exerciseNames
    );
}
