package com.healthassistant.medicalexams;

import java.util.UUID;

class ExaminationNotFoundException extends RuntimeException {

    ExaminationNotFoundException(UUID examId) {
        super("Examination not found: " + examId);
    }
}
