package com.healthassistant.medicalexams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ExamTypeDefinitionRepository extends JpaRepository<ExamTypeDefinition, String> {

    List<ExamTypeDefinition> findAllByOrderBySortOrderAsc();
}
