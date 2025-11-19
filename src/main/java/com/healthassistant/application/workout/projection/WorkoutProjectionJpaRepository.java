package com.healthassistant.application.workout.projection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutProjectionJpaRepository extends JpaRepository<WorkoutProjectionJpaEntity, Long> {

    Optional<WorkoutProjectionJpaEntity> findByWorkoutId(String workoutId);

    boolean existsByWorkoutId(String workoutId);

    List<WorkoutProjectionJpaEntity> findByPerformedDateBetweenOrderByPerformedAtDesc(
            LocalDate startDate,
            LocalDate endDate
    );

    List<WorkoutProjectionJpaEntity> findByPerformedDateOrderByPerformedAtDesc(LocalDate date);

    @Query("SELECT w FROM WorkoutProjectionJpaEntity w " +
           "LEFT JOIN FETCH w.exercises e " +
           "WHERE w.workoutId = :workoutId " +
           "ORDER BY e.orderInWorkout")
    Optional<WorkoutProjectionJpaEntity> findByWorkoutIdWithExercises(@Param("workoutId") String workoutId);

    @Query("SELECT COUNT(w) FROM WorkoutProjectionJpaEntity w " +
           "WHERE w.performedDate BETWEEN :startDate AND :endDate")
    long countByPerformedDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
