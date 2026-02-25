package com.healthassistant.medicalexams;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lab_results")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class LabResult {

    @Id
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "marker_code", nullable = false, length = 100)
    private String markerCode;

    @Column(name = "marker_name", nullable = false, length = 255)
    private String markerName;

    @Column(length = 100)
    private String category;

    @Column(name = "value_numeric", precision = 12, scale = 4)
    private BigDecimal valueNumeric;

    @Column(length = 50)
    private String unit;

    @Column(name = "original_value_numeric", precision = 12, scale = 4)
    private BigDecimal originalValueNumeric;

    @Column(name = "original_unit", length = 50)
    private String originalUnit;

    @Column(name = "conversion_applied", nullable = false)
    private boolean conversionApplied;

    @Column(name = "ref_range_low", precision = 12, scale = 4)
    private BigDecimal refRangeLow;

    @Column(name = "ref_range_high", precision = 12, scale = 4)
    private BigDecimal refRangeHigh;

    @Column(name = "ref_range_text", length = 255)
    private String refRangeText;

    @Column(name = "default_ref_range_low", precision = 12, scale = 4)
    private BigDecimal defaultRefRangeLow;

    @Column(name = "default_ref_range_high", precision = 12, scale = 4)
    private BigDecimal defaultRefRangeHigh;

    @Column(name = "value_text", length = 500)
    private String valueText;

    @Column(nullable = false, length = 20)
    private String flag;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "performed_at")
    private Instant performedAt;

    @Column
    private LocalDate date;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    static LabResult create(Examination examination, String deviceId, String markerCode,
                            String markerName, String category, BigDecimal valueNumeric, String unit,
                            BigDecimal refRangeLow, BigDecimal refRangeHigh, String refRangeText,
                            BigDecimal defaultRefRangeLow, BigDecimal defaultRefRangeHigh,
                            String valueText, int sortOrder) {
        var result = new LabResult();
        result.id = UUID.randomUUID();
        result.examination = examination;
        result.deviceId = deviceId;
        result.markerCode = markerCode;
        result.markerName = markerName;
        result.category = category;
        result.valueNumeric = valueNumeric;
        result.originalValueNumeric = valueNumeric;
        result.unit = unit;
        result.originalUnit = unit;
        result.conversionApplied = false;
        result.refRangeLow = refRangeLow;
        result.refRangeHigh = refRangeHigh;
        result.refRangeText = refRangeText;
        result.defaultRefRangeLow = defaultRefRangeLow;
        result.defaultRefRangeHigh = defaultRefRangeHigh;
        result.valueText = valueText;
        result.sortOrder = sortOrder;
        result.performedAt = examination.getPerformedAt();
        result.date = examination.getDate();
        result.flag = calculateFlag(valueNumeric, refRangeLow, refRangeHigh, defaultRefRangeLow, defaultRefRangeHigh);
        var now = Instant.now();
        result.createdAt = now;
        result.updatedAt = now;
        return result;
    }

    void applyUnitConversion(BigDecimal conversionFactor, String standardUnit) {
        if (conversionFactor != null && this.valueNumeric != null) {
            this.originalValueNumeric = this.valueNumeric;
            this.originalUnit = this.unit;
            this.valueNumeric = this.valueNumeric.multiply(conversionFactor).setScale(4, RoundingMode.HALF_UP);
            this.unit = standardUnit;
            this.conversionApplied = true;
            this.flag = calculateFlag(this.valueNumeric, this.refRangeLow, this.refRangeHigh,
                    this.defaultRefRangeLow, this.defaultRefRangeHigh);
            this.updatedAt = Instant.now();
        }
    }

    void populateDefaultRanges(BigDecimal defaultLow, BigDecimal defaultHigh) {
        this.defaultRefRangeLow = defaultLow;
        this.defaultRefRangeHigh = defaultHigh;
        this.flag = calculateFlag(this.valueNumeric, this.refRangeLow, this.refRangeHigh, defaultLow, defaultHigh);
        this.updatedAt = Instant.now();
    }

    void updateDetails(BigDecimal valueNumeric, String unit, BigDecimal refRangeLow,
                       BigDecimal refRangeHigh, String refRangeText, String valueText) {
        if (valueNumeric != null) {
            this.valueNumeric = valueNumeric;
            this.originalValueNumeric = valueNumeric;
        }
        if (unit != null) {
            this.unit = unit;
            this.originalUnit = unit;
        }
        if (refRangeLow != null) this.refRangeLow = refRangeLow;
        if (refRangeHigh != null) this.refRangeHigh = refRangeHigh;
        if (refRangeText != null) this.refRangeText = refRangeText;
        if (valueText != null) this.valueText = valueText;
        this.conversionApplied = false;
        this.flag = calculateFlag(this.valueNumeric, this.refRangeLow, this.refRangeHigh,
                this.defaultRefRangeLow, this.defaultRefRangeHigh);
        this.updatedAt = Instant.now();
    }

    private static String calculateFlag(BigDecimal value, BigDecimal refLow, BigDecimal refHigh,
                                         BigDecimal defaultLow, BigDecimal defaultHigh) {
        if (value == null) return "UNKNOWN";

        BigDecimal low = refLow != null ? refLow : defaultLow;
        BigDecimal high = refHigh != null ? refHigh : defaultHigh;

        if (low == null && high == null) return "UNKNOWN";
        if (high != null && value.compareTo(high) > 0) return "HIGH";
        if (low != null && value.compareTo(low) < 0) return "LOW";
        return "NORMAL";
    }
}
