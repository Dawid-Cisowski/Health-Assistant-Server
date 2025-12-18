package com.healthassistant.dailysummary;

import java.io.Serial;

public class AiSummaryGenerationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AiSummaryGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
