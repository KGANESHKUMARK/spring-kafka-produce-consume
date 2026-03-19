package com.example.kafkaavro.exception;

import com.example.kafkaavro.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SchemaFetchException.class)
    public ResponseEntity<ErrorResponse> handleSchemaFetch(SchemaFetchException ex) {
        log.error("Schema fetch failed for subject: {}", ex.getSubject(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            error(500, "Schema Registry Error", ex.getMessage()));
    }

    @ExceptionHandler(DataConversionException.class)
    public ResponseEntity<ErrorResponse> handleDataConversion(DataConversionException ex) {
        log.warn("Data conversion failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            error(400, "Data Conversion Error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            error(400, "Validation Error", message));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(
            error(ex.getStatusCode().value(), "Bad Request", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            error(500, "Internal Server Error", "An unexpected error occurred"));
    }

    private ErrorResponse error(int status, String error, String message) {
        ErrorResponse r = new ErrorResponse();
        r.setStatus(status);
        r.setError(error);
        r.setMessage(message);
        r.setTimestamp(Instant.now());
        return r;
    }
}
