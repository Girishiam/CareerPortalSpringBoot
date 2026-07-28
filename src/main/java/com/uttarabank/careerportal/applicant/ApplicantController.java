package com.uttarabank.careerportal.applicant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class ApplicantController {
  private final ApplicantService service;

  public ApplicantController(ApplicantService service) {
    this.service = service;
  }

  @GetMapping("/profile")
  public Map<String, Object> profile() {
    return service.profile();
  }

  @PutMapping("/profile")
  public Map<String, Object> profile(@Valid @RequestBody ProfileRequest r) {
    return service.updateProfile(r);
  }

  @PutMapping("/addresses/{type}")
  public Map<String, Object> address(
      @PathVariable String type, @Valid @RequestBody AddressRequest r) {
    return service.putAddress(type, r);
  }

  @GetMapping("/addresses")
  public List<Map<String, Object>> addresses() {
    return service.addresses();
  }

  @GetMapping("/educations")
  public List<Map<String, Object>> educations() {
    return service.educations();
  }

  @PostMapping("/educations")
  public Map<String, Object> education(@Valid @RequestBody EducationRequest r) {
    return service.createEducation(r);
  }

  @PutMapping("/educations/{id}")
  public Map<String, Object> education(
      @PathVariable long id, @Valid @RequestBody EducationRequest r) {
    return service.updateEducation(id, r);
  }

  @DeleteMapping("/educations/{id}")
  public void deleteEducation(@PathVariable long id) {
    service.deleteEducation(id);
  }

  @GetMapping("/experiences")
  public List<Map<String, Object>> experiences() {
    return service.experiences();
  }

  @PostMapping("/experiences")
  public Map<String, Object> experience(@Valid @RequestBody ExperienceRequest r) {
    return service.createExperience(r);
  }

  @PutMapping("/experiences/{id}")
  public Map<String, Object> experience(
      @PathVariable long id, @Valid @RequestBody ExperienceRequest r) {
    return service.updateExperience(id, r);
  }

  @DeleteMapping("/experiences/{id}")
  public void deleteExperience(@PathVariable long id) {
    service.deleteExperience(id);
  }

  public record ProfileRequest(
      @NotBlank @Size(max = 150) String fullName,
      @Size(max = 150) String fatherName,
      @Size(max = 150) String motherName,
      @Past LocalDate dateOfBirth,
      String gender,
      String maritalStatus,
      @Size(max = 50) String nationality,
      @Size(max = 30) String nidNumber,
      @Email String email,
      @Pattern(regexp = "^01[3-9]\\d{8}$") String mobile) {}

  public record AddressRequest(
      @NotBlank @Size(max = 300) String addressLine,
      @Positive long divisionId,
      @Positive long districtId,
      @Positive long upazilaId,
      @Pattern(regexp = "^\\d{4,10}$") String postcode) {}

  public record EducationRequest(
      @Positive long qualificationId,
      Long subjectId,
      Long institutionId,
      @Size(max = 200) String institutionName,
      @NotBlank String resultType,
      BigDecimal resultValue,
      BigDecimal resultScale,
      @Size(max = 30) String resultGrade,
      @Min(1950) int passingYear,
      boolean isHighestDegree) {}

  public record ExperienceRequest(
      @NotBlank @Size(max = 200) String employerName,
      @NotBlank @Size(max = 150) String designation,
      @NotNull LocalDate startDate,
      LocalDate endDate,
      boolean isCurrent) {}
}
