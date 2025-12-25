package com.healthassistant.dailysummary;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "daily_summaries", indexes = {
        @Index(name = "idx_daily_summaries_date", columnList = "date", unique = true),
        @Index(name = "idx_daily_summaries_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DailySummaryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_summary_generated_at")
    private Instant aiSummaryGeneratedAt;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
