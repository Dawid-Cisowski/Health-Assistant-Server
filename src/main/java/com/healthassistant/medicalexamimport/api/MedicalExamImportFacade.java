package com.healthassistant.medicalexamimport.api;

import com.healthassistant.medicalexamimport.api.dto.MedicalExamDraftResponse;
import com.healthassistant.medicalexamimport.api.dto.MedicalExamDraftUpdateRequest;
import com.healthassistant.medicalexams.api.dto.ExaminationDetailResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface MedicalExamImportFacade {

    MedicalExamDraftResponse analyzeExam(String description, List<MultipartFile> files, String deviceId);

    MedicalExamDraftResponse getDraft(UUID draftId, String deviceId);

    MedicalExamDraftResponse updateDraft(UUID draftId, MedicalExamDraftUpdateRequest request, String deviceId);

    List<ExaminationDetailResponse> confirmDraft(UUID draftId, String deviceId, UUID relatedExaminationId);
}
