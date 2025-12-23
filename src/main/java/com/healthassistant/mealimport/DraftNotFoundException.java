package com.healthassistant.mealimport;

import java.io.Serial;
import java.util.UUID;

class DraftNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    DraftNotFoundException(UUID draftId) {
        super("Draft not found: " + draftId);
    }
}
