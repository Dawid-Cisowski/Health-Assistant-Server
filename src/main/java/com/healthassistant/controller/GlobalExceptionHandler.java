package com.healthassistant.controller;

import com.healthassistant.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Global exception handler for REST controllers
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        List<String> details = new ArrayList<>();
        
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.add(error.getField() + ": " + error.getDefaultMessage());
        }
        
        // Check if batch size limit exceeded
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
        
        String message = "Malformed JSON or invalid data format";
        String detail = ex.getMessage();
        
        // Provide more specific error messages
        if (detail != null) {
            if (detail.contains("JSON parse error")) {
                message = "Invalid JSON format";
            } else if (detail.contains("Cannot deserialize")) {
                message = "Invalid data format in request body";
            } else if (detail.contains("not a valid representation")) {
                message = "Invalid timestamp format";
            }
        }
        
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
}

