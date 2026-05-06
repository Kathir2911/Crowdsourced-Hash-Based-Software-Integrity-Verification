package com.hashverify.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API
 * Implements requirements 3.5, 10.4, 11.1, 11.2
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handle validation errors
     * Implements requirement 11.1: Hash format validation
     * Implements requirement 10.4: Log errors with sufficient detail for debugging
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "Validation failed");
        response.put("errors", errors);
        response.put("timestamp", LocalDateTime.now().toString());
        
        // Requirement 10.4: Log with sufficient detail for debugging
        logger.warn("Validation error - Path: {}, Errors: {}, Request: {}", 
                   request.getDescription(false), 
                   errors,
                   getRequestDetails(request));
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle illegal argument exceptions
     * Implements requirement 3.5: Input sanitization
     * Implements requirement 10.4: Log errors with sufficient detail for debugging
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", ex.getMessage());
        response.put("timestamp", LocalDateTime.now().toString());
        
        // Requirement 10.4: Log with sufficient detail for debugging
        logger.warn("Illegal argument - Path: {}, Message: {}, Request: {}, StackTrace: {}", 
                   request.getDescription(false),
                   ex.getMessage(),
                   getRequestDetails(request),
                   getStackTraceString(ex));
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handle database-related exceptions
     * Implements requirement 10.4: Log errors with sufficient detail for debugging
     */
    @ExceptionHandler({
        org.springframework.dao.DataAccessException.class,
        jakarta.persistence.PersistenceException.class
    })
    public ResponseEntity<Map<String, Object>> handleDatabaseException(
            Exception ex, WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "Database operation failed");
        response.put("timestamp", LocalDateTime.now().toString());
        
        // Requirement 10.4: Log with sufficient detail for debugging
        logger.error("Database error - Path: {}, Type: {}, Message: {}, Request: {}, StackTrace: {}", 
                    request.getDescription(false),
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    getRequestDetails(request),
                    getStackTraceString(ex));
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    /**
     * Handle null pointer exceptions
     * Implements requirement 10.4: Log errors with sufficient detail for debugging
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointerException(
            NullPointerException ex, WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "Internal server error - null reference");
        response.put("timestamp", LocalDateTime.now().toString());
        
        // Requirement 10.4: Log with sufficient detail for debugging
        logger.error("NullPointerException - Path: {}, Request: {}, StackTrace: {}", 
                    request.getDescription(false),
                    getRequestDetails(request),
                    getStackTraceString(ex));
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Handle generic exceptions
     * Implements requirement 11.2: Comprehensive error response formatting
     * Implements requirement 10.4: Log errors with sufficient detail for debugging
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", "An unexpected error occurred");
        response.put("details", ex.getMessage());
        response.put("timestamp", LocalDateTime.now().toString());
        
        // Requirement 10.4: Log with sufficient detail for debugging
        logger.error("Unexpected error - Path: {}, Type: {}, Message: {}, Request: {}, StackTrace: {}", 
                    request.getDescription(false),
                    ex.getClass().getName(),
                    ex.getMessage(),
                    getRequestDetails(request),
                    getStackTraceString(ex));
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Extract request details for logging
     * Requirement 10.4: Include sufficient detail for debugging
     */
    private String getRequestDetails(WebRequest request) {
        StringBuilder details = new StringBuilder();
        details.append("Headers: {");
        
        request.getHeaderNames().forEachRemaining(headerName -> {
            // Skip sensitive headers
            if (!headerName.equalsIgnoreCase("Authorization") && 
                !headerName.equalsIgnoreCase("Cookie")) {
                String headerValue = request.getHeader(headerName);
                details.append(headerName).append("=").append(headerValue).append(", ");
            }
        });
        
        details.append("}");
        return details.toString();
    }
    
    /**
     * Get stack trace as string for logging
     * Requirement 10.4: Include stack trace for debugging
     */
    private String getStackTraceString(Exception ex) {
        StringBuilder stackTrace = new StringBuilder();
        stackTrace.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");
        
        // Include first 5 stack trace elements for debugging
        StackTraceElement[] elements = ex.getStackTrace();
        int limit = Math.min(5, elements.length);
        
        for (int i = 0; i < limit; i++) {
            stackTrace.append("  at ").append(elements[i].toString()).append("\n");
        }
        
        if (elements.length > limit) {
            stackTrace.append("  ... ").append(elements.length - limit).append(" more");
        }
        
        return stackTrace.toString();
    }
}

