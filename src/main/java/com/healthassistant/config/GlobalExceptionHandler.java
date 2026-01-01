package com.healthassistant.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.healthassistant.config.api.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestControllerAdvice
@Slf4j
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request
    ) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        if (details.stream().anyMatch(d -> d.contains("must contain between 1 and 100"))) {
            return ResponseEntity
                    .status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new ErrorResponse("BATCH_TOO_LARGE", "Too many events in batch", details));
        }

        ErrorResponse errorResponse = new ErrorResponse("VALIDATION_ERROR", "Request validation failed", details);

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
        if (cause instanceof InvalidFormatException ife) {
            String message = "Invalid data format: " + ife.getMessage();
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("MALFORMED_REQUEST", message,
                            List.of(ife.getMessage() != null ? ife.getMessage() : "Invalid request body")));
        }

        String message = determineErrorMessage(ex.getMessage());
        String detail = ex.getMessage();

        ErrorResponse errorResponse = new ErrorResponse("MALFORMED_REQUEST", message,
                List.of(detail != null ? detail : "Invalid request body"));

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

        ErrorResponse errorResponse = new ErrorResponse("UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/json",
                List.of("Received: " + ex.getContentType() + ", Expected: application/json"));

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request
    ) {
        log.warn("Invalid argument: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse("INVALID_ARGUMENT",
                ex.getMessage(), List.of(ex.getMessage()));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameterException(
            MissingServletRequestParameterException ex,
            WebRequest request
    ) {
        log.warn("Missing parameter: {}", ex.getParameterName());

        ErrorResponse errorResponse = new ErrorResponse("MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing",
                List.of(ex.getMessage()));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception ex,
            WebRequest request
    ) {
        log.error("Unexpected error", ex);

        ErrorResponse errorResponse = new ErrorResponse("INTERNAL_ERROR",
                "An unexpected error occurred", List.of(ex.getMessage()));

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
