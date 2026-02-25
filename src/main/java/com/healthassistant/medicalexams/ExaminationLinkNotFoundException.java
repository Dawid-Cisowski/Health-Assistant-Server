package com.healthassistant.medicalexams;

import java.util.UUID;

class ExaminationLinkNotFoundException extends RuntimeException {

    ExaminationLinkNotFoundException(UUID examId, UUID linkedExamId) {
        super("No link found between examinations: " + examId + " and " + linkedExamId);
    }
}
