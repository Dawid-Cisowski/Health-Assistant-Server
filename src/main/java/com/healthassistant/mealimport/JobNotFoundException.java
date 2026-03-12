package com.healthassistant.mealimport;

import java.util.UUID;

class JobNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    JobNotFoundException(UUID jobId) {
        super("Meal import job not found: " + jobId);
    }
}
