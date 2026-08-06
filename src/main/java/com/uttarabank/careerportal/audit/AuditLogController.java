package com.uttarabank.careerportal.audit;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {
  private final AuditLogService service;

  public AuditLogController(AuditLogService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> search(
      @RequestParam(required = false) @Positive Long userId,
      @RequestParam(defaultValue = "ALL") @Pattern(regexp = "ALL|ADMIN|APPLICANT|ANONYMOUS")
          String actorType,
      @RequestParam(required = false) @Size(max = 30) String category,
      @RequestParam(required = false) @Size(max = 254) String email,
      @RequestParam(required = false) @Size(max = 100) String action,
      @RequestParam(required = false) @Size(max = 80) String entityType,
      @RequestParam(required = false) @Size(max = 100) String entityId,
      @RequestParam(required = false) @Size(max = 100) String ip,
      @RequestParam(required = false) @Pattern(regexp = "GET|HEAD|POST|PUT|PATCH|DELETE")
          String method,
      @RequestParam(required = false) @Min(100) @Max(599) Integer status,
      @RequestParam(required = false) Boolean success,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) @Size(max = 200) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    return service.search(
        userId,
        actorType,
        category,
        email,
        action,
        entityType,
        entityId,
        ip,
        method,
        status,
        success,
        from,
        to,
        q,
        page,
        size);
  }
}
