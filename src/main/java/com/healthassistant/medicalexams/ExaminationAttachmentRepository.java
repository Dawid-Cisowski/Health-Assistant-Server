package com.healthassistant.medicalexams;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ExaminationAttachmentRepository extends JpaRepository<ExaminationAttachment, UUID> {

    List<ExaminationAttachment> findByExaminationId(UUID examinationId);

    Optional<ExaminationAttachment> findByIdAndExaminationId(UUID id, UUID examinationId);
}
