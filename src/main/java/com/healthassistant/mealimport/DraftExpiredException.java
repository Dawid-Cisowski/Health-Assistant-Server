package com.healthassistant.mealimport;

import java.io.Serial;
import java.util.UUID;

class DraftExpiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    DraftExpiredException(UUID draftId) {
        super("Draft has expired: " + draftId);
    }
}
