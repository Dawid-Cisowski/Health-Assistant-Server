package com.healthassistant.weightimport;

class WeightExtractionException extends Exception {

    private static final long serialVersionUID = 1L;

    WeightExtractionException(String message) {
        super(message);
    }

    WeightExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
