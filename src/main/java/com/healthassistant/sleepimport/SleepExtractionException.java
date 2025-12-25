package com.healthassistant.sleepimport;

class SleepExtractionException extends RuntimeException {

    SleepExtractionException(String message) {
        super(message);
    }

    SleepExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
