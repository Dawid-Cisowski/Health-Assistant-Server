package com.healthassistant

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import javax.sql.DataSource
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Integration tests for conversation cleanup logic.
 * Tests the DELETE queries used by ConversationCleanupService
 * by inserting rows with controlled updated_at timestamps.
 */
@Title("Feature: Conversation Cleanup")
class ConversationCleanupSpec extends BaseIntegrationSpec {

    private static final String DEVICE_ID = "test-cleanup"

    @Autowired
    DataSource dataSource

    def setup() {
        // Clean up any leftover test data
        def conn = dataSource.getConnection()
        try {
            conn.prepareStatement("DELETE FROM conversation_messages WHERE conversation_id IN (SELECT id FROM conversations WHERE device_id = '${DEVICE_ID}')").executeUpdate()
            conn.prepareStatement("DELETE FROM conversations WHERE device_id = '${DEVICE_ID}'").executeUpdate()
            conn.commit()
        } finally {
            conn.close()
        }
    }

    def "Scenario 1: Conversation older than 90 days is deleted along with its messages"() {
        given: "a conversation updated 91 days ago with messages"
        def conversationId = UUID.randomUUID()
        def oldTimestamp = Timestamp.from(Instant.now().minus(91, ChronoUnit.DAYS))
        insertConversation(conversationId, DEVICE_ID, oldTimestamp)
        insertMessage(conversationId, "USER", "Hello", oldTimestamp)
        insertMessage(conversationId, "ASSISTANT", "Hi there", oldTimestamp)

        when: "cleanup DELETE queries are executed"
        def cutoff = Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS))
        def deletedMessages = executeCleanupMessages(cutoff)
        def deletedConversations = executeCleanupConversations(cutoff)

        then: "conversation and messages are deleted"
        deletedConversations == 1
        deletedMessages == 2

        and: "no rows remain"
        countConversations(conversationId) == 0
        countMessages(conversationId) == 0
    }

    def "Scenario 2: Conversation younger than 90 days is preserved"() {
        given: "a conversation updated 30 days ago"
        def conversationId = UUID.randomUUID()
        def recentTimestamp = Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS))
        insertConversation(conversationId, DEVICE_ID, recentTimestamp)
        insertMessage(conversationId, "USER", "Recent message", recentTimestamp)

        when: "cleanup DELETE queries are executed"
        def cutoff = Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS))
        def deletedMessages = executeCleanupMessages(cutoff)
        def deletedConversations = executeCleanupConversations(cutoff)

        then: "nothing is deleted"
        deletedConversations == 0
        deletedMessages == 0

        and: "rows still exist"
        countConversations(conversationId) == 1
        countMessages(conversationId) == 1
    }

    def "Scenario 3: Mixed old and new conversations - only old ones deleted"() {
        given: "one old and one new conversation"
        def oldId = UUID.randomUUID()
        def newId = UUID.randomUUID()
        def oldTimestamp = Timestamp.from(Instant.now().minus(100, ChronoUnit.DAYS))
        def newTimestamp = Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS))

        insertConversation(oldId, DEVICE_ID, oldTimestamp)
        insertMessage(oldId, "USER", "Old message", oldTimestamp)
        insertMessage(oldId, "ASSISTANT", "Old reply", oldTimestamp)

        insertConversation(newId, DEVICE_ID, newTimestamp)
        insertMessage(newId, "USER", "New message", newTimestamp)

        when: "cleanup DELETE queries are executed"
        def cutoff = Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS))
        def deletedMessages = executeCleanupMessages(cutoff)
        def deletedConversations = executeCleanupConversations(cutoff)

        then: "only old conversation and its messages are deleted"
        deletedConversations == 1
        deletedMessages == 2

        and: "new conversation remains intact"
        countConversations(newId) == 1
        countMessages(newId) == 1

        and: "old conversation is gone"
        countConversations(oldId) == 0
        countMessages(oldId) == 0
    }

    def "Scenario 4: Conversation exactly at 90-day boundary is preserved"() {
        given: "a conversation updated exactly 89 days and 23 hours ago (just under cutoff)"
        def conversationId = UUID.randomUUID()
        // 89 days 23 hours = just under 90 days
        def borderlineTimestamp = Timestamp.from(Instant.now().minus(89, ChronoUnit.DAYS).minus(23, ChronoUnit.HOURS))
        insertConversation(conversationId, DEVICE_ID, borderlineTimestamp)
        insertMessage(conversationId, "USER", "Borderline message", borderlineTimestamp)

        when: "cleanup DELETE queries are executed"
        def cutoff = Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS))
        def deletedMessages = executeCleanupMessages(cutoff)
        def deletedConversations = executeCleanupConversations(cutoff)

        then: "conversation is preserved (updated_at is NOT before cutoff)"
        deletedConversations == 0
        deletedMessages == 0
        countConversations(conversationId) == 1
    }

    // --- Helper methods ---

    private void insertConversation(UUID id, String deviceId, Timestamp updatedAt) {
        def conn = dataSource.getConnection()
        try {
            def stmt = conn.prepareStatement(
                    "INSERT INTO conversations (id, device_id, created_at, updated_at, version) VALUES (?, ?, ?, ?, 0)")
            stmt.setObject(1, id)
            stmt.setString(2, deviceId)
            stmt.setTimestamp(3, updatedAt)
            stmt.setTimestamp(4, updatedAt)
            stmt.executeUpdate()
            conn.commit()
        } finally {
            conn.close()
        }
    }

    private void insertMessage(UUID conversationId, String role, String content, Timestamp createdAt) {
        def conn = dataSource.getConnection()
        try {
            def stmt = conn.prepareStatement(
                    "INSERT INTO conversation_messages (conversation_id, role, content, created_at, version) VALUES (?, ?, ?, ?, 0)")
            stmt.setObject(1, conversationId)
            stmt.setString(2, role)
            stmt.setString(3, content)
            stmt.setTimestamp(4, createdAt)
            stmt.executeUpdate()
            conn.commit()
        } finally {
            conn.close()
        }
    }

    private int executeCleanupMessages(Timestamp cutoff) {
        def conn = dataSource.getConnection()
        try {
            def stmt = conn.prepareStatement(
                    "DELETE FROM conversation_messages WHERE conversation_id IN (SELECT id FROM conversations WHERE updated_at < ? AND device_id = ?)")
            stmt.setTimestamp(1, cutoff)
            stmt.setString(2, DEVICE_ID)
            def count = stmt.executeUpdate()
            conn.commit()
            return count
        } finally {
            conn.close()
        }
    }

    private int executeCleanupConversations(Timestamp cutoff) {
        def conn = dataSource.getConnection()
        try {
            def stmt = conn.prepareStatement(
                    "DELETE FROM conversations WHERE updated_at < ? AND device_id = ?")
            stmt.setTimestamp(1, cutoff)
            stmt.setString(2, DEVICE_ID)
            def count = stmt.executeUpdate()
            conn.commit()
            return count
        } finally {
            conn.close()
        }
    }

    private int countConversations(UUID conversationId) {
        def conn = dataSource.getConnection()
        try {
            def stmt = conn.prepareStatement("SELECT COUNT(*) FROM conversations WHERE id = ?")
            stmt.setObject(1, conversationId)
            def rs = stmt.executeQuery()
            rs.next()
            return rs.getInt(1)
        } finally {
            conn.close()
        }
    }

    private int countMessages(UUID conversationId) {
        def conn = dataSource.getConnection()
        try {
            def stmt = conn.prepareStatement("SELECT COUNT(*) FROM conversation_messages WHERE conversation_id = ?")
            stmt.setObject(1, conversationId)
            def rs = stmt.executeQuery()
            rs.next()
            return rs.getInt(1)
        } finally {
            conn.close()
        }
    }
}
