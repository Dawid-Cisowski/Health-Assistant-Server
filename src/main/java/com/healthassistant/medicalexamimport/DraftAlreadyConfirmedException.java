package com.healthassistant.medicalexamimport;

import java.util.UUID;

class DraftAlreadyConfirmedException extends RuntimeException {
    DraftAlreadyConfirmedException(UUID draftId) {
        super("Medical exam import draft already confirmed: " + draftId);
    }
}
