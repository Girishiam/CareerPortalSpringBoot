package com.uttarabank.careerportal.common;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> api(ApiException ex, HttpServletRequest request) {
    return response(ex.status(), ex.code(), ex.getMessage(), List.of(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    var fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                e ->
                    new ApiError.FieldError(
                        e.getField(),
                        Optional.ofNullable(e.getDefaultMessage()).orElse("Invalid value")))
            .toList();
    return response(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "One or more fields are invalid.",
        fields,
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> conflict(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    String details = Optional.ofNullable(ex.getMostSpecificCause().getMessage()).orElse("");

    if (details.contains("uq_user_email") || details.contains("uq_user_mobile")) {
      return response(
          HttpStatus.CONFLICT,
          "ACCOUNT_ALREADY_EXISTS",
          "Email or mobile is already registered.",
          List.of(),
          request);
    }

    if (details.contains("uq_cv_number")) {
      return response(
          HttpStatus.CONFLICT,
          "CV_NUMBER_CONFLICT",
          "A permanent CV number could not be allocated. Please try again.",
          List.of(),
          request);
    }

    return response(
        HttpStatus.CONFLICT,
        "RESOURCE_CONFLICT",
        "The request conflicts with an existing record.",
        List.of(),
        request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
    log.error(
        "Unhandled API error. correlationId={}",
        request.getAttribute(CorrelationIdFilter.ATTRIBUTE),
        ex);
    SQLException sql = findSql(ex);
    if (sql != null) {
      return switch (sql.getErrorCode()) {
        case 50021 ->
            response(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", sql.getMessage(), List.of(), request);
        case 50022 ->
            response(
                HttpStatus.CONFLICT,
                "APPLICATION_STATE_CONFLICT",
                sql.getMessage(),
                List.of(),
                request);
        case 50024 ->
            response(
                HttpStatus.CONFLICT,
                "APPLICATION_DEADLINE_PASSED",
                sql.getMessage(),
                List.of(),
                request);
        case 50025 ->
            response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "APPLICANT_INELIGIBLE",
                sql.getMessage(),
                List.of(),
                request);
        default ->
            response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATABASE_ERROR",
                "The request could not be completed.",
                List.of(),
                request);
      };
    }
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred.",
        List.of(),
        request);
  }

  private SQLException findSql(Throwable value) {
    for (Throwable current = value; current != null; current = current.getCause())
      if (current instanceof SQLException sql) return sql;
    return null;
  }

  private ResponseEntity<ApiError> response(
      HttpStatus status,
      String code,
      String message,
      List<ApiError.FieldError> fields,
      HttpServletRequest request) {
    String correlation =
        Optional.ofNullable(request.getAttribute(CorrelationIdFilter.ATTRIBUTE))
            .map(Object::toString)
            .orElseGet(() -> UUID.randomUUID().toString());
    return ResponseEntity.status(status)
        .body(new ApiError(status.value(), code, message, fields, correlation, Instant.now()));
  }
}
