package com.healthassistant.infrastructure.web.rest;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.healthassistant.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        if (details.stream().anyMatch(d -> d.contains("must contain between 1 and 100"))) {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("BATCH_TOO_LARGE")
                    .message("Too many events in batch")
                    .details(details)
                    .build();

            return ResponseEntity
                    .status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(errorResponse);
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .details(details)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadableException(
            HttpMessageNotReadableException ex,
            WebRequest request
    ) {
        log.warn("Malformed request body: {}", ex.getMessage());

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) cause;
            String message = "Invalid data format: " + ife.getMessage();
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("MALFORMED_REQUEST")
                    .message(message)
                    .details(List.of(ife.getMessage() != null ? ife.getMessage() : "Invalid request body"))
                    .build();
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }

        String message = determineErrorMessage(ex.getMessage());
        String detail = ex.getMessage();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("MALFORMED_REQUEST")
                .message(message)
                .details(List.of(detail != null ? detail : "Invalid request body"))
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex,
            WebRequest request
    ) {
        log.warn("Unsupported media type: {}", ex.getContentType());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("UNSUPPORTED_MEDIA_TYPE")
                .message("Content-Type must be application/json")
                .details(List.of("Received: " + ex.getContentType() + ", Expected: application/json"))
                .build();

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception ex,
            WebRequest request
    ) {
        log.error("Unexpected error", ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .details(List.of(ex.getMessage()))
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    private String determineErrorMessage(String detail) {
        if (detail == null) {
            return "Malformed JSON or invalid data format";
        }
        if (detail.contains("JSON parse error")) {
            return "Invalid JSON format";
        }
        if (detail.contains("Cannot deserialize")) {
            return "Invalid data format in request body";
        }
        if (detail.contains("not a valid representation")) {
            return "Invalid timestamp format";
        }
        return "Malformed JSON or invalid data format";
    }
}
