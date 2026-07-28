package com.uttarabank.careerportal.job;

import com.uttarabank.careerportal.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class JobController {
  private final JobService service;

  public JobController(JobService service) {
    this.service = service;
  }

  @GetMapping("/jobs")
  public List<Map<String, Object>> publicJobs() {
    return service.publicJobs();
  }

  @GetMapping("/jobs/{id}")
  public Map<String, Object> publicJob(@PathVariable long id) {
    return service.publicJob(id);
  }

  @PostMapping("/admin/jobs")
  public Map<String, Object> create(@Valid @RequestBody JobRequest r) {
    return service.create(r, CurrentUser.get().userId());
  }

  @PutMapping("/admin/jobs/{id}")
  public Map<String, Object> update(@PathVariable long id, @Valid @RequestBody JobRequest r) {
    return service.update(id, r);
  }

  @PostMapping("/admin/jobs/{id}/approve")
  public Map<String, Object> approve(@PathVariable long id) {
    return service.transition(id, "DRAFT", "APPROVED", null);
  }

  @PostMapping("/admin/jobs/{id}/publish")
  public Map<String, Object> publish(@PathVariable long id) {
    return service.transition(id, "APPROVED", "PUBLISHED", CurrentUser.get().userId());
  }

  @PostMapping("/admin/jobs/{id}/close")
  public Map<String, Object> close(@PathVariable long id) {
    return service.transition(id, "PUBLISHED", "CLOSED", null);
  }

  public record JobRequest(
      @NotBlank @Size(max = 40) String jobCode,
      @NotBlank @Size(max = 200) String jobTitle,
      @Positive long departmentId,
      @NotBlank String jobDescription,
      String responsibilities,
      @Positive int vacancyCount,
      @NotBlank
          @Pattern(regexp = "PERMANENT|PROBATIONARY|CONTRACTUAL|INTERNSHIP")
          String employmentType,
      @NotNull OffsetDateTime applicationStartAt,
      @NotNull OffsetDateTime applicationEndAt,
      @NotNull LocalDate ageReferenceDate,
      @Size(max = 150) String designation,
      @Size(max = 40) String experienceType,
      @Size(max = 200) String jobLocation,
      @Size(max = 200) String salaryDetails,
      @Size(max = 40) String publicationChannel,
      String jobContext,
      String additionalRequirements,
      String compensationBenefits,
      @Size(max = 300) String applyPageHeader,
      boolean specificEducationRequired,
      boolean existingEmployeeEligible,
      boolean externalApplicantEligible,
      @Min(1) Integer existingEmployeeMaxAge,
      @Min(1) Integer externalApplicantMaxAge,
      @Size(max = 150) String maximumDesignation,
      boolean spouseDataRequired,
      boolean mobileRequired,
      boolean emailRequired,
      boolean relativeDeclarationRequired,
      boolean allowOtherPostApplication,
      boolean coverLetterCvRequired,
      @Size(max = 260) String circularLetterName,
      List<@Valid EducationRequirement> educationRequirements) {}

  public record EducationRequirement(
      @Positive long qualificationId,
      @DecimalMin("0.0") Double minimumResult,
      @Pattern(regexp = "GPA|DIVISION") String resultType) {}
}
