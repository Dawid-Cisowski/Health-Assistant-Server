package com.healthassistant.medicalexamimport;

class MedicalExamExtractionException extends RuntimeException {
    MedicalExamExtractionException(String message) {
        super(message);
    }

    MedicalExamExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
