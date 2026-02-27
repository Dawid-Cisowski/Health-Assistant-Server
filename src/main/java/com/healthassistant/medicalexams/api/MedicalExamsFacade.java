package com.healthassistant.medicalexams.api;

import com.healthassistant.medicalexams.api.dto.AddLabResultsRequest;
import com.healthassistant.medicalexams.api.dto.AttachmentDownloadUrlResponse;
import com.healthassistant.medicalexams.api.dto.AttachmentStorageResult;
import com.healthassistant.medicalexams.api.dto.CreateExaminationRequest;
import com.healthassistant.medicalexams.api.dto.ExamTypeDefinitionResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationAttachmentResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationDetailResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationSummaryResponse;
import com.healthassistant.medicalexams.api.dto.LabResultResponse;
import com.healthassistant.medicalexams.api.dto.MarkerTrendResponse;
import com.healthassistant.medicalexams.api.dto.UpdateExaminationRequest;
import com.healthassistant.medicalexams.api.dto.UpdateLabResultRequest;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MedicalExamsFacade {

    List<ExaminationSummaryResponse> listExaminations(String deviceId, String specialty, String examType,
                                                      LocalDate from, LocalDate to, String q, Boolean abnormal);

    ExaminationDetailResponse getExamination(String deviceId, UUID examId);

    ExaminationDetailResponse createExamination(String deviceId, CreateExaminationRequest request);

    ExaminationDetailResponse updateExamination(String deviceId, UUID examId, UpdateExaminationRequest request);

    void deleteExamination(String deviceId, UUID examId);

    ExaminationDetailResponse addResults(String deviceId, UUID examId, AddLabResultsRequest request);

    LabResultResponse updateResult(String deviceId, UUID examId, UUID resultId, UpdateLabResultRequest request);

    void deleteResult(String deviceId, UUID examId, UUID resultId);

    MarkerTrendResponse getMarkerTrend(String deviceId, String markerCode, LocalDate from, LocalDate to);

    List<ExaminationSummaryResponse> getExaminationHistory(String deviceId, String examTypeCode, LocalDate from, LocalDate to);

    List<ExamTypeDefinitionResponse> getExamTypes();

    List<String> getSpecialties();

    ExaminationAttachmentResponse addAttachment(String deviceId, UUID examId, MultipartFile file,
                                                 String attachmentType, String description, boolean isPrimary);

    ExaminationAttachmentResponse addAttachmentFromStorage(String deviceId, UUID examId,
                                                            String filename, String contentType, long fileSize,
                                                            AttachmentStorageResult storageResult, boolean isPrimary);

    List<ExaminationAttachmentResponse> getAttachments(String deviceId, UUID examId);

    void deleteAttachment(String deviceId, UUID examId, UUID attachmentId);

    AttachmentDownloadUrlResponse getAttachmentDownloadUrl(String deviceId, UUID examId, UUID attachmentId);

    ExaminationDetailResponse linkExaminations(String deviceId, UUID examId, UUID linkedExaminationId);

    void unlinkExaminations(String deviceId, UUID examId, UUID linkedExaminationId);
}
