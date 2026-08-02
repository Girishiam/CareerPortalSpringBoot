package com.uttarabank.careerportal.recruitment;

import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminDashboardController {
  private final AdminDashboardService service;

  public AdminDashboardController(AdminDashboardService service) {
    this.service = service;
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard() {
    return service.dashboard();
  }

  @GetMapping("/jobs")
  public List<Map<String, Object>> jobs() {
    return service.jobs();
  }

  @GetMapping("/jobs/{jobId}/applications")
  public Map<String, Object> applications(
      @PathVariable long jobId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.applications(jobId, null, page, size);
  }

  @GetMapping("/applications")
  public Map<String, Object> applications(
      @RequestParam(required = false) Long jobId,
      @RequestParam(required = false)
          @Pattern(regexp = "^\\d*$", message = "Tracking number must be numeric.")
          String trackingNumber,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.applications(jobId, trackingNumber, page, size);
  }

  @GetMapping("/applications/export")
  public ResponseEntity<byte[]> exportApplications(
      @RequestParam(required = false) Long jobId,
      @RequestParam(required = false)
          @Pattern(regexp = "^\\d*$", message = "Tracking number must be numeric.")
          String trackingNumber) {
    byte[] file = service.exportApplications(jobId, trackingNumber);
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("submitted-applications.xlsx")
                .build()
                .toString())
        .body(file);
  }

  @GetMapping("/applications/{applicationId}")
  public Map<String, Object> application(@PathVariable long applicationId) {
    return service.application(applicationId);
  }

  @GetMapping("/users")
  public Map<String, Object> users(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.users(page, size);
  }
}
