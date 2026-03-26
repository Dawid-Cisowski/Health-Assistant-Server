package com.healthassistant.assistant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
class UserMemoryService {

    private final UserMemoryRepository repository;

    @Transactional
    public void upsertMemory(String deviceId, String key, String value) {
        repository.findByDeviceIdAndMemoryKey(deviceId, key)
                .ifPresentOrElse(
                        existing -> {
                            existing.updateValue(value);
                            log.info("Updated memory key='{}' for device {}", key, maskDeviceId(deviceId));
                        },
                        () -> {
                            repository.save(new UserMemory(deviceId, key, value));
                            log.info("Saved new memory key='{}' for device {}", key, maskDeviceId(deviceId));
                        }
                );
    }

    @Transactional
    public boolean deleteMemory(String deviceId, String key) {
        return repository.findByDeviceIdAndMemoryKey(deviceId, key)
                .map(memory -> {
                    repository.delete(memory);
                    log.info("Deleted memory key='{}' for device {}", key, maskDeviceId(deviceId));
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<UserMemory> loadMemories(String deviceId) {
        return repository.findByDeviceId(deviceId);
    }

    private static String maskDeviceId(String deviceId) {
        return com.healthassistant.config.SecurityUtils.maskDeviceId(deviceId);
    }
}
