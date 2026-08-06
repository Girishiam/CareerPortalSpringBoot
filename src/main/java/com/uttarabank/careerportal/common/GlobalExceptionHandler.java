package com.uttarabank.careerportal.common;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
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
    log.warn(
        "Data integrity conflict. correlationId={} details={}",
        request.getAttribute(CorrelationIdFilter.ATTRIBUTE),
        details);

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

    if (details.contains("uq_applicant_normalized_nid")
        || details.contains("uq_applicant_normalized_passport")) {
      return response(
          HttpStatus.CONFLICT,
          "IDENTITY_ALREADY_REGISTERED",
          "This NID or passport number is already registered.",
          List.of(),
          request);
    }

    if (details.contains("uq_job_applicant")) {
      return response(
          HttpStatus.CONFLICT,
          "APPLICATION_ALREADY_EXISTS",
          "You already have an application for this position.",
          List.of(),
          request);
    }

    if (details.contains("ck_job_window")) {
      return response(
          HttpStatus.BAD_REQUEST,
          "INVALID_APPLICATION_WINDOW",
          "Application end must be after the application start.",
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

  @ExceptionHandler(HttpMessageNotWritableException.class)
  void responseWriteFailure(HttpMessageNotWritableException ex, HttpServletRequest request) {
    if (isClientAbort(ex)) {
      log.debug(
          "Client disconnected before the API response completed. correlationId={}",
          request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
      return;
    }
    log.error(
        "API response could not be serialized. correlationId={}",
        request.getAttribute(CorrelationIdFilter.ATTRIBUTE),
        ex);
  }

  @ExceptionHandler(CannotGetJdbcConnectionException.class)
  ResponseEntity<ApiError> databaseUnavailable(
      CannotGetJdbcConnectionException ex, HttpServletRequest request) {
    String correlation = Objects.toString(request.getAttribute(CorrelationIdFilter.ATTRIBUTE), "");
    log.error("Database unavailable. correlationId={}", correlation, ex);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "3")
        .body(
            error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DATABASE_UNAVAILABLE",
                "The database is temporarily unavailable. Please try again shortly.",
                List.of(),
                request));
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

  private boolean isClientAbort(Throwable value) {
    for (Throwable current = value; current != null; current = current.getCause()) {
      String type = current.getClass().getName();
      String message = Objects.toString(current.getMessage(), "").toLowerCase(Locale.ROOT);
      if (type.endsWith("ClientAbortException")
          || type.endsWith("AsyncRequestNotUsableException")
          || message.contains("connection was aborted")
          || message.contains("broken pipe")
          || message.contains("connection reset")) return true;
    }
    return false;
  }

  private ResponseEntity<ApiError> response(
      HttpStatus status,
      String code,
      String message,
      List<ApiError.FieldError> fields,
      HttpServletRequest request) {
    return ResponseEntity.status(status).body(error(status, code, message, fields, request));
  }

  private ApiError error(
      HttpStatus status,
      String code,
      String message,
      List<ApiError.FieldError> fields,
      HttpServletRequest request) {
    String correlation =
        Optional.ofNullable(request.getAttribute(CorrelationIdFilter.ATTRIBUTE))
            .map(Object::toString)
            .orElseGet(() -> UUID.randomUUID().toString());
    return new ApiError(status.value(), code, message, fields, correlation, Instant.now());
  }
}
