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

import java.util.List;

@Entity
@Table(name = "exam_type_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
class ExamTypeDefinition {

    @Id
    private String code;

    @Column(name = "name_pl", nullable = false)
    private String namePl;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "display_type", nullable = false, length = 30)
    private String displayType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specialties", columnDefinition = "jsonb", nullable = false)
    private List<String> specialties;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
