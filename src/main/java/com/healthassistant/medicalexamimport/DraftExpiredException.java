package com.healthassistant.medicalexamimport;

import java.util.UUID;

class DraftExpiredException extends RuntimeException {
    DraftExpiredException(UUID draftId) {
        super("Medical exam import draft has expired: " + draftId);
    }
}
