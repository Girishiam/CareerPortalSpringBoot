package com.uttarabank.careerportal.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
    int status,
    String code,
    String message,
    List<FieldError> fieldErrors,
    String correlationId,
    Instant timestamp) {
  public record FieldError(String field, String message) {}
}
