package com.healthassistant.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
interface ExerciseNameMappingRepository extends JpaRepository<ExerciseNameMappingEntity, String> {

    List<ExerciseNameMappingEntity> findAllByCatalogId(String catalogId);
}
