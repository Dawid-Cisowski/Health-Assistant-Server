package com.healthassistant.assistant;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_memories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "memory_key", nullable = false)
    private String memoryKey;

    @Column(name = "memory_value", nullable = false)
    private String memoryValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    UserMemory(String deviceId, String memoryKey, String memoryValue) {
        this.deviceId = deviceId;
        this.memoryKey = memoryKey;
        this.memoryValue = memoryValue;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    void updateValue(String newValue) {
        this.memoryValue = newValue;
        this.updatedAt = OffsetDateTime.now();
    }
}
