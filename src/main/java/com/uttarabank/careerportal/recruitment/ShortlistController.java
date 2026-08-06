package com.uttarabank.careerportal.recruitment;

import com.uttarabank.careerportal.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/shortlists")
public class ShortlistController {
  private final ShortlistService service;

  public ShortlistController(ShortlistService service) {
    this.service = service;
  }

  @GetMapping("/stages")
  public List<Map<String, Object>> stages(@RequestParam @Positive long jobId) {
    return service.stages(jobId);
  }

  @PostMapping("/stages")
  public Map<String, Object> createStage(@Valid @RequestBody StageRequest request) {
    return service.createStage(request, CurrentUser.get().userId());
  }

  @GetMapping("/stages/{stageId}/candidates")
  public Map<String, Object> candidates(
      @PathVariable long stageId,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    return service.candidates(stageId, q, page, size);
  }

  @PostMapping("/stages/{stageId}/candidates")
  public Map<String, Object> select(
      @PathVariable long stageId, @Valid @RequestBody SelectionRequest request) {
    return service.select(
        stageId,
        request.applicationIds(),
        request.remarks(),
        request.notifyApplicants(),
        "MANUAL",
        CurrentUser.get().userId());
  }

  @DeleteMapping("/stages/{stageId}/candidates/{applicationId}")
  public Map<String, Object> remove(@PathVariable long stageId, @PathVariable long applicationId) {
    return service.remove(stageId, applicationId);
  }

  @PatchMapping("/stages/{stageId}/candidates/{applicationId}/result")
  public Map<String, Object> result(
      @PathVariable long stageId,
      @PathVariable long applicationId,
      @Valid @RequestBody ResultRequest request) {
    return service.result(
        stageId,
        applicationId,
        request.resultStatus(),
        request.remarks(),
        request.notifyApplicant());
  }

  @GetMapping("/stages/{stageId}/export")
  public ResponseEntity<byte[]> export(@PathVariable long stageId) {
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(service.exportFilename(stageId))
                .build()
                .toString())
        .body(service.exportTemplate(stageId));
  }

  @PostMapping(value = "/stages/{stageId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> importFile(
      @PathVariable long stageId,
      @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue = "false") boolean notifyApplicants) {
    return service.importFile(stageId, file, notifyApplicants, CurrentUser.get().userId());
  }

  public record StageRequest(
      @NotNull Long jobId,
      @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,40}") String stageCode,
      @NotBlank @Size(max = 120) String stageName,
      @NotBlank @Pattern(regexp = "MCQ|WRITTEN|VIVA|ASSESSMENT|INTERVIEW|CUSTOM") String stageType,
      @Min(1) @Max(1000) int stageOrder,
      boolean requiresPreviousPass,
      @Size(max = 120) String candidateLabel) {}

  public record SelectionRequest(
      @NotEmpty @Size(max = 5000) List<@NotNull Long> applicationIds,
      @Size(max = 1000) String remarks,
      boolean notifyApplicants) {}

  public record ResultRequest(
      @NotBlank @Pattern(regexp = "PENDING|PASSED|FAILED|ABSENT") String resultStatus,
      @Size(max = 1000) String remarks,
      boolean notifyApplicant) {}
}
