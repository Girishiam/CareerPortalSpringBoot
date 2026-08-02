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

  @GetMapping("/trainings")
  public List<Map<String, Object>> trainings() {
    return service.trainings();
  }

  @PostMapping("/trainings")
  public Map<String, Object> training(@Valid @RequestBody TrainingRequest r) {
    return service.createTraining(r);
  }

  @PutMapping("/trainings/{id}")
  public Map<String, Object> training(
      @PathVariable long id, @Valid @RequestBody TrainingRequest r) {
    return service.updateTraining(id, r);
  }

  @DeleteMapping("/trainings/{id}")
  public void deleteTraining(@PathVariable long id) {
    service.deleteTraining(id);
  }

  @GetMapping("/languages")
  public List<Map<String, Object>> languages() {
    return service.languages();
  }

  @PostMapping("/languages")
  public Map<String, Object> language(@Valid @RequestBody LanguageRequest r) {
    return service.createLanguage(r);
  }

  @PutMapping("/languages/{id}")
  public Map<String, Object> language(
      @PathVariable long id, @Valid @RequestBody LanguageRequest r) {
    return service.updateLanguage(id, r);
  }

  @DeleteMapping("/languages/{id}")
  public void deleteLanguage(@PathVariable long id) {
    service.deleteLanguage(id);
  }

  @GetMapping("/activities")
  public List<Map<String, Object>> activities() {
    return service.activities();
  }

  @PostMapping("/activities")
  public Map<String, Object> activity(@Valid @RequestBody ActivityRequest r) {
    return service.createActivity(r);
  }

  @PutMapping("/activities/{id}")
  public Map<String, Object> activity(
      @PathVariable long id, @Valid @RequestBody ActivityRequest r) {
    return service.updateActivity(id, r);
  }

  @DeleteMapping("/activities/{id}")
  public void deleteActivity(@PathVariable long id) {
    service.deleteActivity(id);
  }

  @GetMapping("/references")
  public List<Map<String, Object>> references() {
    return service.references();
  }

  @PostMapping("/references")
  public Map<String, Object> reference(@Valid @RequestBody ReferenceRequest r) {
    return service.createReference(r);
  }

  @PutMapping("/references/{id}")
  public Map<String, Object> reference(
      @PathVariable long id, @Valid @RequestBody ReferenceRequest r) {
    return service.updateReference(id, r);
  }

  @DeleteMapping("/references/{id}")
  public void deleteReference(@PathVariable long id) {
    service.deleteReference(id);
  }

  public record ProfileRequest(
      @NotBlank @Size(max = 150) String fullName,
      @Size(max = 150) String fatherName,
      @Size(max = 150) String motherName,
      @Past LocalDate dateOfBirth,
      String gender,
      String maritalStatus,
      @Size(max = 50) String nationality,
      @Pattern(regexp = "^[0-9 .-]*$", message = "NID contains unsupported characters.") String nidNumber,
      @Pattern(regexp = "^[A-Za-z0-9 -]*$", message = "Passport number contains unsupported characters.") String passportNumber,
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

  public record TrainingRequest(
      @Size(max = 200) String trainingTitle,
      @Size(max = 1000) String trainingSummary,
      @Positive Integer durationMonths) {}

  public record LanguageRequest(
      @NotBlank @Size(max = 100) String languageName,
      @Pattern(regexp = "^$|LOW|MEDIUM|HIGH") String speaking,
      @Pattern(regexp = "^$|LOW|MEDIUM|HIGH") String writing,
      @Pattern(regexp = "^$|LOW|MEDIUM|HIGH") String listening,
      @Pattern(regexp = "^$|LOW|MEDIUM|HIGH") String reading) {}

  public record ActivityRequest(
      @NotBlank @Size(max = 200) String activityName,
      @Size(max = 200) String organization,
      @Size(max = 150) String roleName,
      @Size(max = 1000) String activitySummary,
      @Size(max = 500) String achievement) {}

  public record ReferenceRequest(
      @NotBlank @Size(max = 150) String fullName,
      @NotBlank @Size(max = 200) String organization,
      @NotBlank @Size(max = 150) String designation,
      @Size(max = 100) String relationship,
      @Email @Size(max = 254) String email,
      @Pattern(regexp = "^$|^01[3-9]\\d{8}$") String mobile) {}
}
