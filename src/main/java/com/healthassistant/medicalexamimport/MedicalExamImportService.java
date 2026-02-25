package com.healthassistant.medicalexamimport;

import com.healthassistant.medicalexamimport.api.MedicalExamImportFacade;
import com.healthassistant.medicalexamimport.api.dto.ExtractedResultData;
import com.healthassistant.medicalexamimport.api.dto.MedicalExamDraftResponse;
import com.healthassistant.medicalexamimport.api.dto.MedicalExamDraftUpdateRequest;
import com.healthassistant.medicalexams.api.FileStorageService;
import com.healthassistant.medicalexams.api.MedicalExamsFacade;
import com.healthassistant.medicalexams.api.dto.AddLabResultsRequest;
import com.healthassistant.medicalexams.api.dto.AttachmentStorageResult;
import com.healthassistant.medicalexams.api.dto.CreateExaminationRequest;
import com.healthassistant.medicalexams.api.dto.ExaminationDetailResponse;
import com.healthassistant.medicalexams.api.dto.LabResultEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
class MedicalExamImportService implements MedicalExamImportFacade {

    private final MedicalExamImportDraftRepository draftRepository;
    private final MedicalExamContentExtractor contentExtractor;
    private final MedicalExamsFacade medicalExamsFacade;
    private final FileStorageService fileStorageService;

    @Override
    public MedicalExamDraftResponse analyzeExam(String description, List<MultipartFile> files,
                                                 String deviceId) {
        log.info("Analyzing medical exam for device {}", maskDeviceId(deviceId));

        if ((files == null || files.isEmpty()) && (description == null || description.isBlank())) {
            throw new IllegalArgumentException("At least one file or a description is required");
        }

        var extraction = contentExtractor.extract(description, files);

        if (!extraction.valid()) {
            log.warn("AI extraction failed for device {}: {}", maskDeviceId(deviceId), extraction.errorMessage());
            throw new MedicalExamExtractionException(
                    extraction.errorMessage() != null ? extraction.errorMessage() : "AI could not extract medical exam data");
        }

        List<String> filenames = Optional.ofNullable(files)
                .map(f -> f.stream().map(MultipartFile::getOriginalFilename).toList())
                .orElse(List.of());

        var draft = MedicalExamImportDraft.create(deviceId, extraction, filenames);
        draftRepository.save(draft);

        var storedFiles = uploadFilesToStorage(draft.getId(), extraction.examTypeCode(), files);
        if (!storedFiles.isEmpty()) {
            draft.attachStoredFiles(storedFiles);
            draftRepository.save(draft);
        }

        log.info("Created medical exam import draft {} for device {}", draft.getId(), maskDeviceId(deviceId));
        return toDraftResponse(draft);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalExamDraftResponse getDraft(UUID draftId, String deviceId) {
        var draft = findDraftForDevice(draftId, deviceId);
        return toDraftResponse(draft);
    }

    @Override
    public MedicalExamDraftResponse updateDraft(UUID draftId, MedicalExamDraftUpdateRequest request,
                                                 String deviceId) {
        var draft = findDraftForDevice(draftId, deviceId);
        validateDraftEditable(draft);
        draft.applyUpdate(request);
        draftRepository.save(draft);
        return toDraftResponse(draft);
    }

    @Override
    public ExaminationDetailResponse confirmDraft(UUID draftId, String deviceId, UUID relatedExaminationId) {
        var draft = findDraftForDevice(draftId, deviceId);
        validateDraftEditable(draft);

        var data = draft.getExtractedData();

        // Build CreateExaminationRequest from draft data
        Instant performedAt = parseInstant(data.performedAt());
        LocalDate examDate = resolveExamDate(data.date(), performedAt);
        var createRequest = new CreateExaminationRequest(
                data.examTypeCode(),
                data.title() != null ? data.title() : "Badanie medyczne",
                examDate,
                performedAt,
                null,
                data.laboratory(),
                data.orderingDoctor(),
                null,
                data.reportText(),
                data.conclusions(),
                null,
                "AI_IMPORT"
        );

        var examination = medicalExamsFacade.createExamination(deviceId, createRequest);

        // Add lab results if any
        if (data.results() != null && !data.results().isEmpty()) {
            var labEntries = data.results().stream()
                    .map(this::toLabResultEntry)
                    .toList();
            var addRequest = new AddLabResultsRequest(labEntries);
            examination = medicalExamsFacade.addResults(deviceId, examination.id(), addRequest);
        }

        if (relatedExaminationId != null) {
            examination = medicalExamsFacade.linkExaminations(deviceId, examination.id(), relatedExaminationId);
        }

        // Register source documents as attachments so users can view them from UI
        var storedFiles = draft.getStoredFiles();
        if (storedFiles != null && !storedFiles.isEmpty()) {
            var examId = examination.id();
            var validFiles = storedFiles.stream()
                    .filter(sf -> sf.storageKey() != null)
                    .toList();
            IntStream.range(0, validFiles.size()).forEach(i -> {
                var sf = validFiles.get(i);
                var storageResult = new AttachmentStorageResult(
                        sf.storageKey(), sf.publicUrl(), null, sf.provider());
                medicalExamsFacade.addAttachmentFromStorage(
                        deviceId, examId,
                        sf.filename(), sf.contentType(), sf.fileSize(),
                        storageResult,
                        i == 0);
            });
        }

        draft.markConfirmed();
        draftRepository.save(draft);

        log.info("Confirmed medical exam import draft {} → examination {} for device {}",
                draftId, examination.id(), maskDeviceId(deviceId));

        return examination;
    }

    private List<MedicalExamImportDraft.StoredFile> uploadFilesToStorage(
            UUID draftId, String examTypeCode, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return List.of();
        var stored = new ArrayList<MedicalExamImportDraft.StoredFile>();
        files.forEach(file -> {
            try {
                var result = fileStorageService.store(
                        draftId, examTypeCode,
                        file.getOriginalFilename(), file.getContentType(), file.getBytes());
                stored.add(new MedicalExamImportDraft.StoredFile(
                        result.storageKey(), result.publicUrl(), result.provider(),
                        file.getOriginalFilename(), file.getContentType(), file.getSize()));
            } catch (IOException e) {
                log.warn("Failed to upload file {} to storage: {}", file.getOriginalFilename(), e.getMessage());
            }
        });
        return stored;
    }

    private MedicalExamImportDraft findDraftForDevice(UUID draftId, String deviceId) {
        return draftRepository.findByIdAndDeviceId(draftId, deviceId)
                .orElseThrow(() -> new DraftNotFoundException(draftId));
    }

    private void validateDraftEditable(MedicalExamImportDraft draft) {
        if (!draft.isPending()) {
            throw new DraftAlreadyConfirmedException(draft.getId());
        }
        if (draft.isExpired()) {
            throw new DraftExpiredException(draft.getId());
        }
    }

    private LabResultEntry toLabResultEntry(ExtractedResultData r) {
        return new LabResultEntry(
                r.markerCode(),
                r.markerName() != null ? r.markerName() : r.markerCode(),
                r.category(),
                r.valueNumeric(),
                r.unit(),
                r.refRangeLow(),
                r.refRangeHigh(),
                r.refRangeText(),
                r.valueText(),
                r.sortOrder()
        );
    }

    private MedicalExamDraftResponse toDraftResponse(MedicalExamImportDraft draft) {
        var data = draft.getExtractedData();
        return new MedicalExamDraftResponse(
                draft.getId(),
                data.examTypeCode(),
                data.title(),
                parseLocalDate(data.date()),
                parseInstant(data.performedAt()),
                data.laboratory(),
                data.orderingDoctor(),
                data.reportText(),
                data.conclusions(),
                data.results() != null ? data.results() : List.of(),
                draft.getAiConfidence(),
                draft.getStatus().name(),
                draft.getExpiresAt(),
                null
        );
    }

    private LocalDate resolveExamDate(String dateStr, Instant performedAt) {
        LocalDate parsed = parseLocalDate(dateStr);
        if (parsed != null) return parsed;
        if (performedAt != null) return performedAt.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate();
        log.warn("No exam date available in draft — falling back to today");
        return LocalDate.now(ZoneId.of("Europe/Warsaw"));
    }

    private LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 8) return "****";
        return deviceId.substring(0, 4) + "****" + deviceId.substring(deviceId.length() - 4);
    }
}
