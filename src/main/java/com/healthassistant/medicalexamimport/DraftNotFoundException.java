package com.healthassistant.medicalexamimport;

import java.util.UUID;

class DraftNotFoundException extends RuntimeException {
    DraftNotFoundException(UUID draftId) {
        super("Medical exam import draft not found: " + draftId);
    }
}
