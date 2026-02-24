package com.healthassistant.medicalexams;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "marker_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
class MarkerDefinition {

    @Id
    private String code;

    @Column(name = "name_pl", nullable = false)
    private String namePl;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "category", nullable = false)
    private String category;

    private String specialty;

    @Column(name = "standard_unit")
    private String standardUnit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unit_conversions", columnDefinition = "jsonb")
    private Map<String, Double> unitConversions;

    @Column(name = "ref_range_low_default", precision = 12, scale = 4)
    private BigDecimal refRangeLowDefault;

    @Column(name = "ref_range_high_default", precision = 12, scale = 4)
    private BigDecimal refRangeHighDefault;

    @Column(name = "sort_order")
    private int sortOrder;
}
