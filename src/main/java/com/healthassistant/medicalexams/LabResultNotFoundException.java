package com.healthassistant.medicalexams;

import java.util.UUID;

class LabResultNotFoundException extends RuntimeException {

    LabResultNotFoundException(UUID resultId) {
        super("Lab result not found: " + resultId);
    }
}
