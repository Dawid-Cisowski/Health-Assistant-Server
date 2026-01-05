package com.healthassistant.config.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,
    String message,
    List<String> details,
    String requestId
) {
    public ErrorResponse(String code, String message, List<String> details) {
        this(code, message, details, null);
    }
}
