package com.al.fhirhl7transformer.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.al.fhirhl7transformer.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ca.uhn.fhir.parser.DataFormatException;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorResponse> handleJsonError(JsonProcessingException e, HttpServletRequest request) {
        log.error("JSON Processing Error: {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "JSON Processing Error", e.getMessage(), request);
    }

    @ExceptionHandler(DataFormatException.class)
    public ResponseEntity<ErrorResponse> handleFhirParseError(DataFormatException e, HttpServletRequest request) {
        log.error("FHIR Parsing Error: {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "FHIR Parsing Error", e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadInput(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("Invalid Input: {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Input Error", e.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            org.springframework.web.bind.MethodArgumentNotValidException e, HttpServletRequest request) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation Error: {}", details);
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Error", details, request);
    }

    @ExceptionHandler(ca.uhn.hl7v2.HL7Exception.class)
    public ResponseEntity<ErrorResponse> handleHl7Error(ca.uhn.hl7v2.HL7Exception e, HttpServletRequest request) {
        log.error("HL7 Processing Error: {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "HL7 Processing Error", e.getMessage(), request);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFound(TenantNotFoundException e, HttpServletRequest request) {
        log.warn("Tenant Not Found: {}", e.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", e.getMessage(), request);
    }

    @ExceptionHandler(TenantAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleTenantExists(TenantAlreadyExistsException e,
            HttpServletRequest request) {
        log.warn("Tenant Already Exists: {}", e.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Tenant Already Exists", e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralError(Exception e, HttpServletRequest request) {
        log.error("Internal Server Error: ", e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred",
                request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message,
            HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                error,
                message,
                request.getRequestURI());
        return new ResponseEntity<>(response, status);
    }
}
