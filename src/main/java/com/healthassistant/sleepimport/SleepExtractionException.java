package com.healthassistant.sleepimport;

class SleepExtractionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SleepExtractionException(String message) {
        super(message);
    }

    SleepExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
