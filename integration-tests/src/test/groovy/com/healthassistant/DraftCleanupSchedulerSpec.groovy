package com.healthassistant

import com.healthassistant.medicalexamimport.DraftCleanupScheduler
import com.healthassistant.medicalexamimport.MedicalExamImportDraft
import com.healthassistant.medicalexamimport.MedicalExamImportDraftRepository
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Title

import java.time.Instant
import java.time.temporal.ChronoUnit

@Title("Draft Cleanup Scheduler Tests")
class DraftCleanupSchedulerSpec extends BaseIntegrationSpec {

    @Autowired
    DraftCleanupScheduler scheduler

    @Autowired
    MedicalExamImportDraftRepository draftRepository

    def "should clean up expired drafts successfully"() {
        given: "an expired draft with stored files"
        def draftId = UUID.randomUUID()
        jdbcTemplate.update("""
            INSERT INTO medical_exam_import_drafts 
            (id, version, device_id, extracted_data, stored_files, status, created_at, updated_at, expires_at)
            VALUES (?, 0, 'test-device', '{}'::jsonb, '[{"storageKey": "test-key", "publicUrl": "http://test", "provider": "LOCAL", "filename": "test.pdf", "contentType": "application/pdf", "fileSize": 123}]'::jsonb, 'PENDING', NOW(), NOW(), ?)
        """, draftId, java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)))

        when: "scheduler runs"
        scheduler.cleanupExpiredDrafts()

        then: "no exception is thrown and drafts are cleaned up"
        noExceptionThrown()
        
        and: "draft is deleted"
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM medical_exam_import_drafts WHERE id = ?", Integer.class, draftId) == 0
    }
}
