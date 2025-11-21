package com.healthassistant.sleep;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "sleep_sessions_projections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SleepSessionProjectionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "session_number", nullable = false)
    private Integer sessionNumber;

    @Column(name = "sleep_start", nullable = false)
    private Instant sleepStart;

    @Column(name = "sleep_end", nullable = false)
    private Instant sleepEnd;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    // Future: Sleep phases support
    @Column(name = "light_sleep_minutes")
    private Integer lightSleepMinutes;

    @Column(name = "deep_sleep_minutes")
    private Integer deepSleepMinutes;

    @Column(name = "rem_sleep_minutes")
    private Integer remSleepMinutes;

    @Column(name = "awake_minutes")
    private Integer awakeMinutes;

    @Column(name = "origin_package")
    private String originPackage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
