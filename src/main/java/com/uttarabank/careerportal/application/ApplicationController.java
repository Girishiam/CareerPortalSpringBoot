package com.uttarabank.careerportal.application;

import com.uttarabank.careerportal.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ApplicationController {
  private final ApplicationService service;

  public ApplicationController(ApplicationService service) {
    this.service = service;
  }

  @PostMapping("/jobs/{jobId}/applications")
  public DraftResponse draft(@PathVariable long jobId) {
    return service.draft(jobId);
  }

  @GetMapping("/me/applications")
  public List<Map<String, Object>> applications() {
    return service.applications();
  }

  @GetMapping("/me/applications/{id}")
  public Map<String, Object> application(@PathVariable long id) {
    return service.application(id);
  }

  @PostMapping("/me/applications/{id}/submit")
  public SubmitResponse submit(@PathVariable long id) {
    return service.submit(id, CurrentUser.get().userId());
  }

  public record DraftResponse(
      long applicationId, String status, boolean canSubmit, List<String> missingSections) {}

  public record SubmitResponse(
      long applicationId,
      String trackingNumber,
      String status,
      String eligibilityStatus,
      Instant submittedAt) {}
}
