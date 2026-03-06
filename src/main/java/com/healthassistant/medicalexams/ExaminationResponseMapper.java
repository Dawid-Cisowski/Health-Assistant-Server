package com.healthassistant.medicalexams;

import com.healthassistant.medicalexams.api.dto.ExaminationAttachmentResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationDetailResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationSummaryResponse;
import com.healthassistant.medicalexams.api.dto.LabResultResponse;
import com.healthassistant.medicalexams.api.dto.LinkedExaminationResponse;

import java.util.Comparator;
import java.util.List;

class ExaminationResponseMapper {

    private ExaminationResponseMapper() {}

    static ExaminationSummaryResponse toSummaryResponse(Examination exam) {
        return new ExaminationSummaryResponse(
                exam.getId(),
                exam.getExamType().getCode(),
                exam.getTitle(),
                exam.getDate(),
                exam.getStatus().name(),
                exam.getDisplayType(),
                exam.getSpecialties(),
                exam.getLaboratory(),
                exam.getResultCount(),
                exam.getAbnormalCount(),
                exam.hasPrimaryAttachment(),
                exam.getPrimaryAttachmentUrl(),
                exam.getCreatedAt());
    }

    static ExaminationDetailResponse toDetailResponse(Examination exam, List<LinkedExaminationResponse> linkedExaminations) {
        var results = exam.getResults().stream()
                .sorted(Comparator.comparingInt(LabResult::getSortOrder))
                .map(ExaminationResponseMapper::toLabResultResponse)
                .toList();

        var attachments = exam.getAttachments().stream()
                .map(ExaminationResponseMapper::toAttachmentResponse)
                .toList();

        return new ExaminationDetailResponse(
                exam.getId(),
                exam.getExamType().getCode(),
                exam.getTitle(),
                exam.getDate(),
                exam.getStatus().name(),
                exam.getDisplayType(),
                exam.getSpecialties(),
                exam.getPerformedAt(),
                exam.getResultsReceivedAt(),
                exam.getLaboratory(),
                exam.getOrderingDoctor(),
                exam.getNotes(),
                exam.getSummary(),
                exam.getReportText(),
                exam.getConclusions(),
                exam.getRecommendations(),
                exam.getSource().name(),
                results,
                attachments,
                linkedExaminations,
                exam.getCreatedAt(),
                exam.getUpdatedAt());
    }

    static LinkedExaminationResponse toLinkedExaminationResponse(Examination exam) {
        return new LinkedExaminationResponse(
                exam.getId(),
                exam.getExamType().getCode(),
                exam.getTitle(),
                exam.getDate(),
                exam.getStatus().name(),
                exam.getDisplayType(),
                exam.getSpecialties());
    }

    static LabResultResponse toLabResultResponse(LabResult result) {
        return new LabResultResponse(
                result.getId(),
                result.getMarkerCode(),
                result.getMarkerName(),
                result.getCategory(),
                result.getValueNumeric(),
                result.getUnit(),
                result.getOriginalValueNumeric(),
                result.getOriginalUnit(),
                result.isConversionApplied(),
                result.getRefRangeLow(),
                result.getRefRangeHigh(),
                result.getRefRangeText(),
                result.getDefaultRefRangeLow(),
                result.getDefaultRefRangeHigh(),
                result.getDefaultRefRangeWarningHigh(),
                result.getValueText(),
                result.getFlag().name(),
                result.getSortOrder());
    }

    static ExaminationAttachmentResponse toAttachmentResponse(ExaminationAttachment attachment) {
        return new ExaminationAttachmentResponse(
                attachment.getId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getFileSizeBytes(),
                attachment.getStorageProvider(),
                attachment.getPublicUrl(),
                attachment.getAttachmentType().name(),
                attachment.isPrimary(),
                attachment.getDescription(),
                attachment.getCreatedAt());
    }
}
