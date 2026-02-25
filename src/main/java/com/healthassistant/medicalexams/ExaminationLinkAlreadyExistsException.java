package com.healthassistant.medicalexams;

import java.util.UUID;

class ExaminationLinkAlreadyExistsException extends RuntimeException {

    ExaminationLinkAlreadyExistsException(UUID examId, UUID linkedExamId) {
        super("Link already exists between examinations: " + examId + " and " + linkedExamId);
    }
}
