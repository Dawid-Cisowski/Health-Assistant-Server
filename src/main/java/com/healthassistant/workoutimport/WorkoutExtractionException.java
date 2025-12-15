package com.healthassistant.workoutimport;

class WorkoutExtractionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WorkoutExtractionException(String message) {
        super(message);
    }

    public WorkoutExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
