package com.healthassistant.assistant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserMemoryRepository extends JpaRepository<UserMemory, UUID> {

    List<UserMemory> findByDeviceId(String deviceId);

    Optional<UserMemory> findByDeviceIdAndMemoryKey(String deviceId, String memoryKey);
}
