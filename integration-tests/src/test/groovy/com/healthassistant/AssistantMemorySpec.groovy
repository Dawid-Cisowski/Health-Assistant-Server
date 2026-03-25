package com.healthassistant

import org.springframework.ai.chat.model.ToolContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Title

/**
 * Integration tests for AI Chat Memory System.
 * Verifies that the assistant can persist user facts across sessions.
 *
 * Uses Groovy duck typing to access package-private beans.
 */
@Title("Feature: AI Chat Memory System - persistent user facts across sessions")
class AssistantMemorySpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-assistant-memory"

    @Autowired
    ApplicationContext applicationContext

    private Object healthTools
    private Object userMemoryService

    def setup() {
        healthTools = applicationContext.getBean("healthTools")
        userMemoryService = applicationContext.getBean("userMemoryService")
        jdbcTemplate.update("DELETE FROM user_memories WHERE device_id = ?", DEVICE_ID)
    }

    private ToolContext createToolContext() {
        return new ToolContext(Map.of("deviceId", DEVICE_ID))
    }

    private boolean isMutationSuccess(Object result) {
        return result.class.simpleName == "MutationSuccess"
    }

    private boolean isToolError(Object result) {
        return result.class.simpleName == "ToolError"
    }

    // ===================== saveUserMemory =====================

    def "saveUserMemory should persist a fact and return MutationSuccess"() {
        when: "saving a memory through the tool"
        def result = healthTools.saveUserMemory("dietary_preference", "vegetarian", createToolContext())

        then: "result is MutationSuccess"
        isMutationSuccess(result)

        and: "memory is persisted in DB"
        def count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_memories WHERE device_id = ? AND memory_key = ? AND memory_value = ?",
                Integer, DEVICE_ID, "dietary_preference", "vegetarian"
        )
        count == 1
    }

    def "saveUserMemory should upsert when same key is saved again with new value"() {
        given: "existing memory"
        healthTools.saveUserMemory("training_time", "morning", createToolContext())

        when: "saving with same key but different value"
        def result = healthTools.saveUserMemory("training_time", "evening", createToolContext())

        then: "result is MutationSuccess"
        isMutationSuccess(result)

        and: "only one record exists"
        def count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_memories WHERE device_id = ? AND memory_key = ?",
                Integer, DEVICE_ID, "training_time"
        )
        count == 1

        and: "value is updated to the new one"
        def value = jdbcTemplate.queryForObject(
                "SELECT memory_value FROM user_memories WHERE device_id = ? AND memory_key = ?",
                String, DEVICE_ID, "training_time"
        )
        value == "evening"
    }

    def "saveUserMemory should return ToolError for blank key"() {
        when: "saving memory with blank key"
        def result = healthTools.saveUserMemory("", "some value", createToolContext())

        then: "result is ToolError"
        isToolError(result)
    }

    def "saveUserMemory should return ToolError for blank value"() {
        when: "saving memory with blank value"
        def result = healthTools.saveUserMemory("some_key", "", createToolContext())

        then: "result is ToolError"
        isToolError(result)
    }

    def "saveUserMemory should return ToolError for null key"() {
        when: "saving memory with null key"
        def result = healthTools.saveUserMemory(null, "some value", createToolContext())

        then: "result is ToolError"
        isToolError(result)
    }

    // ===================== getUserMemories =====================

    def "getUserMemories should return all saved memories for device"() {
        given: "multiple memories saved"
        healthTools.saveUserMemory("dietary_preference", "vegetarian", createToolContext())
        healthTools.saveUserMemory("training_time", "evening", createToolContext())
        healthTools.saveUserMemory("goal", "build muscle mass", createToolContext())

        when: "getting all memories"
        def result = healthTools.getUserMemories(createToolContext())

        then: "result is MutationSuccess"
        isMutationSuccess(result)

        and: "all keys and values are present in the result data"
        def data = result.data().toString()
        data.contains("dietary_preference")
        data.contains("vegetarian")
        data.contains("training_time")
        data.contains("evening")
        data.contains("goal")
        data.contains("build muscle mass")
    }

    def "getUserMemories should return empty result when no memories saved"() {
        when: "getting memories for device with no saved memories"
        def result = healthTools.getUserMemories(createToolContext())

        then: "result is MutationSuccess"
        isMutationSuccess(result)

        and: "data indicates no memories"
        result.data() != null
    }

    // ===================== deleteUserMemory =====================

    def "deleteUserMemory should remove a specific fact"() {
        given: "two saved memories"
        healthTools.saveUserMemory("injury", "right knee", createToolContext())
        healthTools.saveUserMemory("dietary_preference", "vegetarian", createToolContext())

        when: "deleting one memory"
        def result = healthTools.deleteUserMemory("injury", createToolContext())

        then: "result is MutationSuccess"
        isMutationSuccess(result)

        and: "deleted memory is gone from DB"
        def deletedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_memories WHERE device_id = ? AND memory_key = ?",
                Integer, DEVICE_ID, "injury"
        )
        deletedCount == 0

        and: "other memory remains intact"
        def otherCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_memories WHERE device_id = ? AND memory_key = ?",
                Integer, DEVICE_ID, "dietary_preference"
        )
        otherCount == 1
    }

    def "deleteUserMemory should return ToolError when key does not exist"() {
        when: "deleting non-existent key"
        def result = healthTools.deleteUserMemory("nonexistent_key", createToolContext())

        then: "result is ToolError"
        isToolError(result)
    }

    // ===================== UserMemoryService =====================

    def "memories should be isolated per device"() {
        given: "memory saved for test device"
        healthTools.saveUserMemory("personal_fact", "likes running", createToolContext())

        when: "loading memories for a different device"
        def otherDeviceMemories = userMemoryService.loadMemories("completely-different-device-xyz")

        then: "no memories found for other device"
        otherDeviceMemories.isEmpty()

        and: "original device still has its memory"
        def memories = userMemoryService.loadMemories(DEVICE_ID)
        memories.any { it.getMemoryKey() == "personal_fact" }
    }

    def "memories loaded via service should reflect correct key and value"() {
        given: "memories saved via service directly"
        userMemoryService.upsertMemory(DEVICE_ID, "dietary_preference", "vegetarian")
        userMemoryService.upsertMemory(DEVICE_ID, "training_time", "evening workouts")

        when: "loading memories"
        def memories = userMemoryService.loadMemories(DEVICE_ID)

        then: "both memories are returned with correct data"
        memories.size() == 2
        memories.any { it.getMemoryKey() == "dietary_preference" && it.getMemoryValue() == "vegetarian" }
        memories.any { it.getMemoryKey() == "training_time" && it.getMemoryValue() == "evening workouts" }
    }

    def "memories should persist across conversations (new conversation sees old memories)"() {
        given: "memory saved via tool in first context"
        healthTools.saveUserMemory("goal_weight", "80 kg", createToolContext())

        when: "loading memories for a fresh context (simulating new conversation start)"
        def memories = userMemoryService.loadMemories(DEVICE_ID)

        then: "memory from previous session is present"
        memories.any { it.getMemoryKey() == "goal_weight" && it.getMemoryValue() == "80 kg" }
    }
}
