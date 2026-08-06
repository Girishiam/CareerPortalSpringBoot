package com.uttarabank.careerportal.recruitment;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
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
    return service.applications(jobId, null, null, null, null, null, null, null, null, page, size);
  }

  @GetMapping("/applications")
  public Map<String, Object> applications(
      @RequestParam(required = false) Long jobId,
      @RequestParam(required = false)
          @Pattern(regexp = "^\\d*$", message = "Tracking number must be numeric.")
          String trackingNumber,
      @RequestParam(required = false) @Size(max = 30) String cvNumber,
      @RequestParam(required = false) @Size(max = 20) String mobile,
      @RequestParam(required = false) @Size(max = 254) String email,
      @RequestParam(required = false) @Size(max = 200) String candidateName,
      @RequestParam(required = false) @Pattern(regexp = "ELIGIBLE|INELIGIBLE|PENDING")
          String eligibility,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate submittedFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate submittedTo,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.applications(
        jobId,
        trackingNumber,
        cvNumber,
        mobile,
        email,
        candidateName,
        eligibility,
        submittedFrom,
        submittedTo,
        page,
        size);
  }

  @GetMapping("/applications/export")
  public ResponseEntity<byte[]> exportApplications(
      @RequestParam(required = false) Long jobId,
      @RequestParam(required = false)
          @Pattern(regexp = "^\\d*$", message = "Tracking number must be numeric.")
          String trackingNumber,
      @RequestParam(required = false) @Size(max = 30) String cvNumber,
      @RequestParam(required = false) @Size(max = 20) String mobile,
      @RequestParam(required = false) @Size(max = 254) String email,
      @RequestParam(required = false) @Size(max = 200) String candidateName,
      @RequestParam(required = false) @Pattern(regexp = "ELIGIBLE|INELIGIBLE|PENDING")
          String eligibility,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate submittedFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate submittedTo) {
    byte[] file =
        service.exportApplications(
            jobId,
            trackingNumber,
            cvNumber,
            mobile,
            email,
            candidateName,
            eligibility,
            submittedFrom,
            submittedTo);
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(
                    service.applicationExportFilename(
                        jobId,
                        trackingNumber,
                        cvNumber,
                        mobile,
                        email,
                        candidateName,
                        eligibility,
                        submittedFrom,
                        submittedTo))
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
