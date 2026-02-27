package com.healthassistant.medicalexams;

import com.healthassistant.medicalexams.api.FileStorageService;
import com.healthassistant.medicalexams.api.MedicalExamsFacade;
import com.healthassistant.medicalexams.api.dto.AddLabResultsRequest;
import com.healthassistant.medicalexams.api.dto.AttachmentDownloadUrlResponse;
import com.healthassistant.medicalexams.api.dto.AttachmentStorageResult;
import com.healthassistant.medicalexams.api.dto.CreateExaminationRequest;
import com.healthassistant.medicalexams.api.dto.ExamTypeDefinitionResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationAttachmentResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationDetailResponse;
import com.healthassistant.medicalexams.api.dto.ExaminationSummaryResponse;
import com.healthassistant.medicalexams.api.dto.LabResultEntry;
import com.healthassistant.medicalexams.api.dto.LabResultResponse;
import com.healthassistant.medicalexams.api.dto.LinkedExaminationResponse;
import com.healthassistant.medicalexams.api.dto.MarkerDataPoint;
import com.healthassistant.medicalexams.api.dto.MarkerTrendResponse;
import com.healthassistant.medicalexams.api.dto.UpdateExaminationRequest;
import com.healthassistant.medicalexams.api.dto.UpdateLabResultRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
class MedicalExamsService implements MedicalExamsFacade {

    private final ExaminationRepository examinationRepository;
    private final LabResultRepository labResultRepository;
    private final ExaminationAttachmentRepository attachmentRepository;
    private final ExamTypeDefinitionRepository examTypeRepository;
    private final MarkerDefinitionRepository markerDefinitionRepository;
    private final FileStorageService fileStorageService;
    private final ExaminationLinkRepository examinationLinkRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ExaminationSummaryResponse> listExaminations(String deviceId, String specialty,
                                                              String examType, LocalDate from, LocalDate to,
                                                              String q, Boolean abnormal) {
        var searchActive = q != null && !q.isBlank();
        List<Examination> exams;
        if (searchActive) {
            var likePattern = buildLikePattern(q);
            var effectiveFrom = from != null ? from : LocalDate.of(2000, 1, 1);
            var effectiveTo = to != null ? to : LocalDate.now();
            exams = examinationRepository.findAllByDeviceIdWithSearch(deviceId, effectiveFrom, effectiveTo, likePattern);
        } else {
            exams = (from != null || to != null)
                    ? examinationRepository.findAllByDeviceIdAndDateBetweenWithDetails(
                            deviceId,
                            from != null ? from : LocalDate.of(2000, 1, 1),
                            to != null ? to : LocalDate.now())
                    : examinationRepository.findAllByDeviceIdWithDetails(deviceId);
        }

        return exams.stream()
                .filter(e -> specialty == null || (e.getSpecialties() != null && e.getSpecialties().contains(specialty)))
                .filter(e -> examType == null || examType.equals(e.getExamType().getCode()))
                .filter(e -> !Boolean.TRUE.equals(abnormal) || "ABNORMAL".equals(e.getStatus()))
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExaminationDetailResponse getExamination(String deviceId, UUID examId) {
        var exam = findExamForDevice(deviceId, examId);
        return toDetailResponse(exam);
    }

    @Override
    public ExaminationDetailResponse createExamination(String deviceId, CreateExaminationRequest request) {
        var examType = examTypeRepository.findById(request.examTypeCode())
                .orElseThrow(() -> new IllegalArgumentException("Unknown exam type: " + request.examTypeCode()));

        var exam = Examination.create(deviceId, examType, request.title(), request.date(),
                request.performedAt(), request.resultsReceivedAt(), request.laboratory(),
                request.orderingDoctor(), request.notes(), request.reportText(),
                request.conclusions(), request.recommendations(), request.source());

        examinationRepository.save(exam);
        log.info("Created examination {} for device {}", exam.getId(), maskDeviceId(deviceId));
        return toDetailResponse(exam);
    }

    @Override
    public ExaminationDetailResponse updateExamination(String deviceId, UUID examId, UpdateExaminationRequest request) {
        var exam = findExamForDevice(deviceId, examId);
        exam.updateDetails(request.title(), request.date(), request.performedAt(), request.resultsReceivedAt(),
                request.laboratory(), request.orderingDoctor(), request.notes(), request.summary(),
                request.reportText(), request.conclusions(), request.recommendations());
        examinationRepository.save(exam);
        log.info("Updated examination {} for device {}", examId, maskDeviceId(deviceId));
        return toDetailResponse(exam);
    }

    @Override
    public void deleteExamination(String deviceId, UUID examId) {
        var exam = findExamForDevice(deviceId, examId);
        exam.getAttachments().stream()
                .map(ExaminationAttachment::getStorageKey)
                .forEach(fileStorageService::delete);
        examinationRepository.delete(exam);
        log.info("Deleted examination {} for device {}", examId, maskDeviceId(deviceId));
    }

    @Override
    public ExaminationDetailResponse addResults(String deviceId, UUID examId, AddLabResultsRequest request) {
        var exam = findExamForDevice(deviceId, examId);

        request.results().stream()
                .map(entry -> createLabResult(exam, deviceId, entry))
                .forEach(result -> {
                    enrichWithMarkerDefinition(result);
                    exam.addResult(result);
                });

        exam.recalculateStatus();
        examinationRepository.save(exam);
        log.info("Added {} results to examination {} for device {}",
                request.results().size(), examId, maskDeviceId(deviceId));
        return toDetailResponse(exam);
    }

    @Override
    public LabResultResponse updateResult(String deviceId, UUID examId, UUID resultId, UpdateLabResultRequest request) {
        var exam = findExamForDevice(deviceId, examId);
        var result = labResultRepository.findByIdAndExaminationId(resultId, examId)
                .orElseThrow(() -> new LabResultNotFoundException(resultId));

        result.updateDetails(request.valueNumeric(), request.unit(), request.refRangeLow(),
                request.refRangeHigh(), request.refRangeText(), request.valueText());

        labResultRepository.save(result);
        exam.recalculateStatus();
        examinationRepository.save(exam);
        log.info("Updated result {} in examination {} for device {}", resultId, examId, maskDeviceId(deviceId));
        return toLabResultResponse(result);
    }

    @Override
    public void deleteResult(String deviceId, UUID examId, UUID resultId) {
        var exam = findExamForDevice(deviceId, examId);
        var result = labResultRepository.findByIdAndExaminationId(resultId, examId)
                .orElseThrow(() -> new LabResultNotFoundException(resultId));

        exam.removeResult(result);
        exam.recalculateStatus();
        examinationRepository.save(exam);
        log.info("Deleted result {} from examination {} for device {}", resultId, examId, maskDeviceId(deviceId));
    }

    @Override
    @Transactional(readOnly = true)
    public MarkerTrendResponse getMarkerTrend(String deviceId, String markerCode, LocalDate from, LocalDate to) {
        var markerDef = markerDefinitionRepository.findByCode(markerCode).orElse(null);

        var results = labResultRepository.findTrendData(deviceId, markerCode, from, to);

        var dataPoints = results.stream()
                .map(r -> new MarkerDataPoint(
                        r.getDate(),
                        r.getValueNumeric(),
                        r.getFlag(),
                        r.getExamination().getId(),
                        r.getExamination().getTitle(),
                        r.getRefRangeLow(),
                        r.getRefRangeHigh()))
                .toList();

        return new MarkerTrendResponse(
                markerCode,
                markerDef != null ? markerDef.getNamePl() : markerCode,
                markerDef != null ? markerDef.getStandardUnit() : null,
                markerDef != null ? markerDef.getRefRangeLowDefault() : null,
                markerDef != null ? markerDef.getRefRangeHighDefault() : null,
                dataPoints);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExaminationSummaryResponse> getExaminationHistory(String deviceId, String examTypeCode,
                                                                   LocalDate from, LocalDate to) {
        var exams = (from != null && to != null)
                ? examinationRepository.findAllByDeviceIdAndExamTypeCodeAndDateBetweenWithDetails(
                        deviceId, examTypeCode, from, to)
                : examinationRepository.findAllByDeviceIdAndExamTypeCodeWithDetails(deviceId, examTypeCode);

        return exams.stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamTypeDefinitionResponse> getExamTypes() {
        return examTypeRepository.findAllByOrderBySortOrderAsc().stream()
                .map(et -> new ExamTypeDefinitionResponse(
                        et.getCode(), et.getNamePl(), et.getNameEn(),
                        et.getDisplayType(), et.getSpecialties(),
                        et.getCategory(), et.getSortOrder()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getSpecialties() {
        return examTypeRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(et -> et.getSpecialties() != null)
                .flatMap(et -> et.getSpecialties().stream())
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public ExaminationAttachmentResponse addAttachment(String deviceId, UUID examId, MultipartFile file,
                                                        String attachmentType, String description, boolean isPrimary) {
        FileValidationUtils.validate(file);
        var exam = findExamForDevice(deviceId, examId);

        try {
            var storageResult = fileStorageService.store(
                    examId, exam.getExamType().getCode(),
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());

            var attachment = ExaminationAttachment.create(exam, deviceId,
                    file.getOriginalFilename(), file.getContentType(), file.getSize(),
                    storageResult, attachmentType, isPrimary, description);

            exam.addAttachment(attachment);
            examinationRepository.save(exam);
            log.info("Added attachment to examination {} for device {}", examId, maskDeviceId(deviceId));
            return toAttachmentResponse(attachment);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
    }

    @Override
    public ExaminationAttachmentResponse addAttachmentFromStorage(String deviceId, UUID examId,
                                                                   String filename, String contentType,
                                                                   long fileSize, AttachmentStorageResult storageResult,
                                                                   boolean isPrimary) {
        var exam = findExamForDevice(deviceId, examId);
        var attachment = ExaminationAttachment.create(exam, deviceId,
                filename, contentType, fileSize, storageResult, "SOURCE_DOCUMENT", isPrimary, null);
        exam.addAttachment(attachment);
        examinationRepository.save(exam);
        log.info("Registered source document attachment for examination {} for device {}", examId, maskDeviceId(deviceId));
        return toAttachmentResponse(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentDownloadUrlResponse getAttachmentDownloadUrl(String deviceId, UUID examId, UUID attachmentId) {
        findExamForDevice(deviceId, examId);
        var attachment = attachmentRepository.findByIdAndExaminationId(attachmentId, examId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        var url = fileStorageService.generateDownloadUrl(attachment.getStorageKey());
        log.info("Generated download URL for attachment {} in examination {} for device {}",
                attachmentId, examId, maskDeviceId(deviceId));
        return new AttachmentDownloadUrlResponse(url, attachment.getStorageProvider(),
                attachment.getStorageProvider().equals("GCS") ? 3600 : 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExaminationAttachmentResponse> getAttachments(String deviceId, UUID examId) {
        findExamForDevice(deviceId, examId);
        return attachmentRepository.findByExaminationId(examId).stream()
                .map(this::toAttachmentResponse)
                .toList();
    }

    @Override
    public void deleteAttachment(String deviceId, UUID examId, UUID attachmentId) {
        var exam = findExamForDevice(deviceId, examId);
        var attachment = attachmentRepository.findByIdAndExaminationId(attachmentId, examId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));

        fileStorageService.delete(attachment.getStorageKey());
        exam.removeAttachment(attachment);
        examinationRepository.save(exam);
        log.info("Deleted attachment {} from examination {} for device {}",
                attachmentId, examId, maskDeviceId(deviceId));
    }

    @Override
    public ExaminationDetailResponse linkExaminations(String deviceId, UUID examId, UUID linkedExaminationId) {
        if (examId.equals(linkedExaminationId)) {
            throw new IllegalArgumentException("Cannot link an examination to itself");
        }
        var exam = findExamForDevice(deviceId, examId);
        var linkedExam = findExamForDevice(deviceId, linkedExaminationId);

        var ids = orderedIds(examId, linkedExaminationId);
        examinationLinkRepository.findLinkByOrderedIds(ids[0], ids[1]).ifPresent(existing -> {
            throw new ExaminationLinkAlreadyExistsException(examId, linkedExaminationId);
        });

        examinationLinkRepository.save(ExaminationLink.create(exam, linkedExam));
        log.info("Linked examination {} with {} for device {}", examId, linkedExaminationId, maskDeviceId(deviceId));
        return toDetailResponse(exam);
    }

    @Override
    public void unlinkExaminations(String deviceId, UUID examId, UUID linkedExaminationId) {
        findExamForDevice(deviceId, examId);
        findExamForDevice(deviceId, linkedExaminationId);

        var ids = orderedIds(examId, linkedExaminationId);
        var link = examinationLinkRepository.findLinkByOrderedIds(ids[0], ids[1])
                .orElseThrow(() -> new ExaminationLinkNotFoundException(examId, linkedExaminationId));

        examinationLinkRepository.delete(link);
        log.info("Unlinked examination {} from {} for device {}", examId, linkedExaminationId, maskDeviceId(deviceId));
    }

    private UUID[] orderedIds(UUID idA, UUID idB) {
        if (idA.toString().compareTo(idB.toString()) < 0) {
            return new UUID[]{idA, idB};
        }
        return new UUID[]{idB, idA};
    }

    private Examination findExamForDevice(String deviceId, UUID examId) {
        return examinationRepository.findByDeviceIdAndIdWithDetails(deviceId, examId)
                .orElseThrow(() -> new ExaminationNotFoundException(examId));
    }

    private LabResult createLabResult(Examination exam, String deviceId, LabResultEntry entry) {
        return LabResult.create(exam, deviceId, entry.markerCode(), entry.markerName(),
                entry.category(), entry.valueNumeric(), entry.unit(),
                entry.refRangeLow(), entry.refRangeHigh(), entry.refRangeText(),
                null, null, entry.valueText(), entry.sortOrder());
    }

    private void enrichWithMarkerDefinition(LabResult result) {
        markerDefinitionRepository.findByCode(result.getMarkerCode()).ifPresent(markerDef -> {
            result.populateDefaultRanges(markerDef.getRefRangeLowDefault(), markerDef.getRefRangeHighDefault());

            if (markerDef.getStandardUnit() != null && result.getUnit() != null
                    && !markerDef.getStandardUnit().equalsIgnoreCase(result.getUnit())
                    && markerDef.getUnitConversions() != null) {
                var conversionFactor = findConversionFactor(markerDef.getUnitConversions(), result.getUnit());
                if (conversionFactor != null) {
                    result.applyUnitConversion(BigDecimal.valueOf(conversionFactor), markerDef.getStandardUnit());
                }
            }
        });
    }

    private Double findConversionFactor(Map<String, Double> conversions, String sourceUnit) {
        return conversions.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(sourceUnit))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private ExaminationSummaryResponse toSummaryResponse(Examination exam) {
        return new ExaminationSummaryResponse(
                exam.getId(),
                exam.getExamType().getCode(),
                exam.getTitle(),
                exam.getDate(),
                exam.getStatus(),
                exam.getDisplayType(),
                exam.getSpecialties(),
                exam.getLaboratory(),
                exam.getResultCount(),
                exam.getAbnormalCount(),
                exam.hasPrimaryAttachment(),
                exam.getPrimaryAttachmentUrl(),
                exam.getCreatedAt());
    }

    private ExaminationDetailResponse toDetailResponse(Examination exam) {
        var results = exam.getResults().stream()
                .sorted(Comparator.comparingInt(LabResult::getSortOrder))
                .map(this::toLabResultResponse)
                .toList();

        var attachments = exam.getAttachments().stream()
                .map(this::toAttachmentResponse)
                .toList();

        var linkedExaminations = examinationLinkRepository.findAllLinksForExamination(exam.getId()).stream()
                .map(link -> link.getExaminationA().getId().equals(exam.getId())
                        ? link.getExaminationB() : link.getExaminationA())
                .map(this::toLinkedExaminationResponse)
                .toList();

        return new ExaminationDetailResponse(
                exam.getId(),
                exam.getExamType().getCode(),
                exam.getTitle(),
                exam.getDate(),
                exam.getStatus(),
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
                exam.getSource(),
                results,
                attachments,
                linkedExaminations,
                exam.getCreatedAt(),
                exam.getUpdatedAt());
    }

    private LinkedExaminationResponse toLinkedExaminationResponse(Examination exam) {
        return new LinkedExaminationResponse(
                exam.getId(),
                exam.getExamType().getCode(),
                exam.getTitle(),
                exam.getDate(),
                exam.getStatus(),
                exam.getDisplayType(),
                exam.getSpecialties());
    }

    private LabResultResponse toLabResultResponse(LabResult result) {
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
                result.getValueText(),
                result.getFlag(),
                result.getSortOrder());
    }

    private ExaminationAttachmentResponse toAttachmentResponse(ExaminationAttachment attachment) {
        return new ExaminationAttachmentResponse(
                attachment.getId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getFileSizeBytes(),
                attachment.getStorageProvider(),
                attachment.getPublicUrl(),
                attachment.getAttachmentType(),
                attachment.isPrimary(),
                attachment.getDescription(),
                attachment.getCreatedAt());
    }

    private String buildLikePattern(String q) {
        var sanitized = q.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + sanitized + "%";
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) return "****";
        return deviceId.substring(0, 4) + "****" + deviceId.substring(deviceId.length() - 4);
    }
}
