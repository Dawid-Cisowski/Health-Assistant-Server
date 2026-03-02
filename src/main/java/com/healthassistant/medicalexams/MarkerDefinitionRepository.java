package com.healthassistant.medicalexams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface MarkerDefinitionRepository extends JpaRepository<MarkerDefinition, String> {

    Optional<MarkerDefinition> findByCode(String code);

    List<MarkerDefinition> findAllByOrderBySortOrderAsc();
}
