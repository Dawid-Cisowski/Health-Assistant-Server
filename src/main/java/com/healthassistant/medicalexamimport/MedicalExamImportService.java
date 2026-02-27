package com.healthassistant.medicalexamimport;

import com.healthassistant.config.SecurityUtils;
import com.healthassistant.medicalexamimport.api.MedicalExamImportFacade;
import com.healthassistant.medicalexamimport.api.dto.DraftSectionResponse;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
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
    private final CdaExamExtractor cdaExamExtractor;
    private final MedicalExamsFacade medicalExamsFacade;
    private final FileStorageService fileStorageService;
    private final MedicalExamSectionInterpreter sectionInterpreter;

    @Override
    public MedicalExamDraftResponse analyzeExam(String description, List<MultipartFile> files,
                                                 String deviceId) {
        log.info("Analyzing medical exam for device {}", SecurityUtils.maskDeviceId(deviceId));

        if ((files == null || files.isEmpty()) && (description == null || description.isBlank())) {
            throw new IllegalArgumentException("At least one file or a description is required");
        }

        var extraction = isCdaFile(files)
                ? extractCda(files)
                : contentExtractor.extract(description, files);

        if (!extraction.valid()) {
            log.warn("Extraction failed for device {}: {}",
                    SecurityUtils.maskDeviceId(deviceId),
                    SecurityUtils.sanitizeForLog(extraction.errorMessage()));
            throw new MedicalExamExtractionException("Could not extract medical exam data");
        }

        List<String> filenames = Optional.ofNullable(files)
                .map(f -> f.stream().map(MultipartFile::getOriginalFilename).toList())
                .orElse(List.of());

        var interpretations = sectionInterpreter.interpretSections(extraction.sections());
        var draft = MedicalExamImportDraft.create(deviceId, extraction, filenames, interpretations);
        draftRepository.save(draft);

        var firstSectionCode = extraction.sections().stream()
                .map(ExtractedExamData.ExtractedSectionData::examTypeCode)
                .filter(code -> code != null)
                .findFirst()
                .orElse("OTHER");
        var storedFiles = uploadFilesToStorage(draft.getId(), firstSectionCode, files);
        if (!storedFiles.isEmpty()) {
            draft.attachStoredFiles(storedFiles);
            draftRepository.save(draft);
        }

        log.info("Created medical exam import draft {} ({} sections) for device {}",
                draft.getId(), extraction.sections().size(), SecurityUtils.maskDeviceId(deviceId));
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
    public List<ExaminationDetailResponse> confirmDraft(UUID draftId, String deviceId, UUID relatedExaminationId) {
        var draft = findDraftForDevice(draftId, deviceId);
        validateDraftEditable(draft);

        var data = draft.getExtractedData();
        Instant performedAt = MedicalExamDateParser.parseInstant(data.performedAt());
        LocalDate examDate = resolveExamDate(data.date(), performedAt);

        var sections = Optional.ofNullable(data.sections()).orElse(List.of());
        var examinations = sections.stream()
                .map(section -> createExamFromSection(deviceId, section, data, examDate, performedAt))
                .toList();

        linkExaminationsToEachOther(deviceId, examinations);

        if (relatedExaminationId != null) {
            examinations.forEach(exam ->
                    medicalExamsFacade.linkExaminations(deviceId, exam.id(), relatedExaminationId));
        }

        attachStoredFilesToAll(deviceId, draft, examinations);

        draft.markConfirmed();
        draftRepository.save(draft);

        log.info("Confirmed medical exam import draft {} → {} examination(s) for device {}",
                draftId, examinations.size(), SecurityUtils.maskDeviceId(deviceId));

        // Re-fetch examinations to include all created links in the response
        return examinations.stream()
                .map(exam -> medicalExamsFacade.getExamination(deviceId, exam.id()))
                .toList();
    }

    private ExaminationDetailResponse createExamFromSection(
            String deviceId,
            MedicalExamImportDraft.ExtractedData.SectionRecord section,
            MedicalExamImportDraft.ExtractedData sharedData,
            LocalDate examDate, Instant performedAt) {

        var importSource = Optional.ofNullable(sharedData.importSource()).orElse("AI_IMPORT");
        var createRequest = new CreateExaminationRequest(
                section.examTypeCode(),
                section.title() != null ? section.title() : "Badanie medyczne",
                examDate,
                performedAt,
                null,
                sharedData.laboratory(),
                sharedData.orderingDoctor(),
                null,
                section.reportText(),
                section.conclusions(),
                null,
                importSource
        );

        var examination = medicalExamsFacade.createExamination(deviceId, createRequest);

        var results = Optional.ofNullable(section.results()).orElse(List.of());
        if (!results.isEmpty()) {
            var labEntries = results.stream()
                    .map(this::toLabResultEntry)
                    .toList();
            examination = medicalExamsFacade.addResults(deviceId, examination.id(), new AddLabResultsRequest(labEntries));
        }

        return examination;
    }

    private void linkExaminationsToEachOther(String deviceId, List<ExaminationDetailResponse> examinations) {
        if (examinations.size() < 2) return;
        // O(n²) pair linking — acceptable for typical section counts (3-5 per document)
        var examIds = examinations.stream().map(ExaminationDetailResponse::id).toList();
        IntStream.range(0, examIds.size())
                .forEach(i -> IntStream.range(i + 1, examIds.size())
                        .forEach(j -> medicalExamsFacade.linkExaminations(deviceId, examIds.get(i), examIds.get(j))));
    }

    private void attachStoredFilesToAll(String deviceId, MedicalExamImportDraft draft,
                                        List<ExaminationDetailResponse> examinations) {
        var storedFiles = draft.getStoredFiles();
        if (storedFiles == null || storedFiles.isEmpty()) return;

        var validFiles = storedFiles.stream()
                .filter(sf -> sf.storageKey() != null)
                .toList();
        if (validFiles.isEmpty()) return;

        var primary = selectPrimaryFile(validFiles);
        examinations.forEach(exam ->
                validFiles.forEach(sf ->
                        medicalExamsFacade.addAttachmentFromStorage(
                                deviceId, exam.id(),
                                sf.filename(), sf.contentType(), sf.fileSize(),
                                new AttachmentStorageResult(sf.storageKey(), sf.publicUrl(), null, sf.provider()),
                                sf.equals(primary))));
    }

    private MedicalExamImportDraft.StoredFile selectPrimaryFile(List<MedicalExamImportDraft.StoredFile> files) {
        return files.stream()
                .filter(f -> "application/pdf".equals(f.contentType()))
                .findFirst()
                .or(() -> files.stream()
                        .filter(f -> f.contentType() != null && f.contentType().startsWith("image/"))
                        .findFirst())
                .orElse(files.getFirst());
    }

    private List<MedicalExamImportDraft.StoredFile> uploadFilesToStorage(
            UUID draftId, String examTypeCode, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return List.of();
        return files.stream()
                .map(file -> uploadSingleFile(draftId, examTypeCode, file))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<MedicalExamImportDraft.StoredFile> uploadSingleFile(
            UUID draftId, String examTypeCode, MultipartFile file) {
        try {
            var result = fileStorageService.store(
                    draftId, examTypeCode,
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
            return Optional.of(new MedicalExamImportDraft.StoredFile(
                    result.storageKey(), result.publicUrl(), result.provider(),
                    file.getOriginalFilename(), file.getContentType(), file.getSize()));
        } catch (IOException e) {
            log.warn("Failed to upload file {} to storage: {}",
                    SecurityUtils.sanitizeForLog(file.getOriginalFilename()), e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isCdaMultipartFile(MultipartFile f) {
        String ct = f.getContentType();
        String name = f.getOriginalFilename();
        if (ct == null || "application/octet-stream".equals(ct)) {
            String lower = name != null ? name.toLowerCase(Locale.ROOT) : "";
            return lower.endsWith(".cda") || lower.endsWith(".xml");
        }
        return "text/xml".equals(ct) || "application/xml".equals(ct)
                || (name != null && name.toLowerCase(Locale.ROOT).endsWith(".cda"));
    }

    private boolean isCdaFile(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return false;
        return files.stream().anyMatch(MedicalExamImportService::isCdaMultipartFile);
    }

    private ExtractedExamData extractCda(List<MultipartFile> files) {
        var cdaFiles = files.stream().filter(MedicalExamImportService::isCdaMultipartFile).toList();
        if (cdaFiles.size() > 1) {
            log.warn("Multiple CDA files uploaded — only the first will be parsed ({} ignored)", cdaFiles.size() - 1);
        }
        return cdaFiles.stream()
                .findFirst()
                .map(f -> {
                    try {
                        return cdaExamExtractor.extract(f.getBytes());
                    } catch (IOException e) {
                        log.error("Failed to read CDA file bytes: {}", SecurityUtils.sanitizeForLog(e.getMessage()));
                        return ExtractedExamData.invalid("Failed to read CDA file", BigDecimal.ZERO);
                    }
                })
                .orElseThrow(() -> new MedicalExamExtractionException("No CDA file found"));
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
        var sections = Optional.ofNullable(data.sections()).orElse(List.of()).stream()
                .map(s -> new DraftSectionResponse(
                        s.examTypeCode(), s.title(), s.reportText(), s.conclusions(),
                        Optional.ofNullable(s.results()).orElse(List.of())))
                .toList();

        return MedicalExamDraftResponse.success(
                draft.getId(),
                MedicalExamDateParser.parseLocalDate(data.date()),
                MedicalExamDateParser.parseInstant(data.performedAt()),
                data.laboratory(),
                data.orderingDoctor(),
                sections,
                draft.getAiConfidence(),
                draft.getStatus().name(),
                draft.getExpiresAt()
        );
    }

    private LocalDate resolveExamDate(String dateStr, Instant performedAt) {
        LocalDate parsed = MedicalExamDateParser.parseLocalDate(dateStr);
        if (parsed != null) return parsed;
        if (performedAt != null) return performedAt.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate();
        log.warn("No exam date available in draft — falling back to today");
        return LocalDate.now(ZoneId.of("Europe/Warsaw"));
    }
}
