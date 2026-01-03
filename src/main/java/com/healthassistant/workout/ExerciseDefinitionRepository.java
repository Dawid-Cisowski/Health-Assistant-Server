package com.healthassistant.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface ExerciseDefinitionRepository extends JpaRepository<ExerciseDefinitionEntity, String> {

    @Query(value = "SELECT * FROM exercises WHERE :muscle = ANY(muscles)", nativeQuery = true)
    List<ExerciseDefinitionEntity> findByMuscle(@Param("muscle") String muscle);

    List<ExerciseDefinitionEntity> findByPrimaryMuscle(String primaryMuscle);

    @Query(value = "SELECT DISTINCT unnest(muscles) AS muscle FROM exercises ORDER BY muscle", nativeQuery = true)
    List<String> findAllMuscleGroups();
}
