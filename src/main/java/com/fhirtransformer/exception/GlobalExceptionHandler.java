package com.fhirtransformer.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fhirtransformer.dto.ErrorResponse;
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
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid JSON format", e.getMessage(), request);
    }

    @ExceptionHandler(DataFormatException.class)
    public ResponseEntity<ErrorResponse> handleFhirParseError(DataFormatException e, HttpServletRequest request) {
        log.error("FHIR Parsing Error: {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid FHIR content", e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadInput(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("Invalid Input: {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid Input", e.getMessage(), request);
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
