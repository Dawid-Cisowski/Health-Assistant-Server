package com.healthassistant.medicalexams;

import com.healthassistant.medicalexams.api.ExamSource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "examinations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Examination {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_type_code", nullable = false)
    private ExamTypeDefinition examType;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "performed_at")
    private Instant performedAt;

    @Column(name = "results_received_at")
    private Instant resultsReceivedAt;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 255)
    private String laboratory;

    @Column(name = "ordering_doctor", length = 255)
    private String orderingDoctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExaminationStatus status;

    @Column(name = "display_type", nullable = false, length = 30)
    private String displayType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specialties", columnDefinition = "jsonb", nullable = false)
    private List<String> specialties;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "report_text", columnDefinition = "TEXT")
    private String reportText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "findings", columnDefinition = "jsonb")
    private Map<String, Object> findings;

    @Column(columnDefinition = "TEXT")
    private String conclusions;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ExamSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "examination", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<LabResult> results = new HashSet<>();

    @OneToMany(mappedBy = "examination", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ExaminationAttachment> attachments = new HashSet<>();

    static Examination create(String deviceId, ExamTypeDefinition examType, String title, LocalDate date,
                              Instant performedAt, Instant resultsReceivedAt, String laboratory,
                              String orderingDoctor, String notes, String reportText,
                              String conclusions, String recommendations, ExamSource source) {
        var exam = new Examination();
        exam.id = UUID.randomUUID();
        exam.deviceId = deviceId;
        exam.examType = examType;
        exam.title = title;
        exam.date = date;
        exam.performedAt = performedAt;
        exam.resultsReceivedAt = resultsReceivedAt;
        exam.laboratory = laboratory;
        exam.orderingDoctor = orderingDoctor;
        exam.notes = notes;
        exam.reportText = reportText;
        exam.conclusions = conclusions;
        exam.recommendations = recommendations;
        exam.source = source != null ? source : ExamSource.MANUAL;
        exam.status = ExaminationStatus.COMPLETED;
        exam.displayType = examType.getDisplayType();
        exam.specialties = examType.getSpecialties() != null ? new ArrayList<>(examType.getSpecialties()) : new ArrayList<>();
        var now = Instant.now();
        exam.createdAt = now;
        exam.updatedAt = now;
        return exam;
    }

    void updateDetails(String title, LocalDate date, Instant performedAt, Instant resultsReceivedAt,
                       String laboratory, String orderingDoctor, String notes, String summary,
                       String reportText, String conclusions, String recommendations) {
        if (title != null) this.title = title;
        if (date != null) this.date = date;
        if (performedAt != null) this.performedAt = performedAt;
        if (resultsReceivedAt != null) this.resultsReceivedAt = resultsReceivedAt;
        if (laboratory != null) this.laboratory = laboratory;
        if (orderingDoctor != null) this.orderingDoctor = orderingDoctor;
        if (notes != null) this.notes = notes;
        if (summary != null) this.summary = summary;
        if (reportText != null) this.reportText = reportText;
        if (conclusions != null) this.conclusions = conclusions;
        if (recommendations != null) this.recommendations = recommendations;
        this.updatedAt = Instant.now();
    }

    void recalculateStatus() {
        boolean hasAbnormal = results.stream().anyMatch(r -> r.getFlag().isAbnormal());
        this.status = hasAbnormal ? ExaminationStatus.ABNORMAL : ExaminationStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    void addResult(LabResult result) {
        results.add(result);
    }

    void removeResult(LabResult result) {
        results.remove(result);
    }

    void addAttachment(ExaminationAttachment attachment) {
        attachments.add(attachment);
    }

    void removeAttachment(ExaminationAttachment attachment) {
        attachments.remove(attachment);
    }

    Optional<LabResult> findResult(UUID resultId) {
        return results.stream().filter(r -> r.getId().equals(resultId)).findFirst();
    }

    Optional<ExaminationAttachment> findAttachment(UUID attachmentId) {
        return attachments.stream().filter(a -> a.getId().equals(attachmentId)).findFirst();
    }

    int getResultCount() {
        return results.size();
    }

    int getAbnormalCount() {
        return (int) results.stream()
                .filter(r -> r.getFlag() != ResultFlag.NORMAL && r.getFlag() != ResultFlag.UNKNOWN)
                .count();
    }

    boolean hasPrimaryAttachment() {
        return attachments.stream().anyMatch(ExaminationAttachment::isPrimary);
    }

    String getPrimaryAttachmentUrl() {
        return attachments.stream()
                .filter(ExaminationAttachment::isPrimary)
                .findFirst()
                .map(ExaminationAttachment::getPublicUrl)
                .orElse(null);
    }
}
