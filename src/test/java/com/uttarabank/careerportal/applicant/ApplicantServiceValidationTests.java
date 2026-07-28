package com.uttarabank.careerportal.applicant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uttarabank.careerportal.common.ApiException;
import com.uttarabank.careerportal.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ApplicantServiceValidationTests {
  private JdbcTemplate jdbc;
  private ApplicantService service;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    service = new ApplicantService(jdbc);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rejectsCgpaGreaterThanScaleBeforeWriting() {
    var request =
        new ApplicantController.EducationRequest(
            1,
            null,
            null,
            "University",
            "CGPA",
            new BigDecimal("4.50"),
            new BigDecimal("4.00"),
            null,
            2024,
            true);

    assertCode("INVALID_ACADEMIC_RESULT", () -> service.createEducation(request));
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsCgpaWithoutScaleBeforeWriting() {
    var request =
        new ApplicantController.EducationRequest(
            1, null, null, "University", "CGPA", new BigDecimal("3.50"), null, null, 2024, true);

    assertCode("INVALID_ACADEMIC_RESULT", () -> service.createEducation(request));
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsDivisionWithoutGradeBeforeWriting() {
    var request =
        new ApplicantController.EducationRequest(
            1, null, null, "College", "DIVISION", null, null, "  ", 2020, false);

    assertCode("INVALID_ACADEMIC_RESULT", () -> service.createEducation(request));
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsFuturePassingYearBeforeWriting() {
    var request =
        new ApplicantController.EducationRequest(
            1,
            null,
            null,
            "University",
            "CGPA",
            new BigDecimal("3.50"),
            new BigDecimal("4.00"),
            null,
            Year.now().getValue() + 1,
            true);

    assertCode("INVALID_PASSING_YEAR", () -> service.createEducation(request));
    verifyNoInteractions(jdbc);
  }

  @Test
  void createAndUpdateApplyTheSameEducationValidation() {
    var request =
        new ApplicantController.EducationRequest(
            1,
            null,
            null,
            "University",
            "CGPA",
            new BigDecimal("5.00"),
            new BigDecimal("4.00"),
            null,
            2024,
            true);

    assertCode("INVALID_ACADEMIC_RESULT", () -> service.createEducation(request));
    assertCode("INVALID_ACADEMIC_RESULT", () -> service.updateEducation(99, request));
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsExperienceEndingBeforeItStarts() {
    var request =
        new ApplicantController.ExperienceRequest(
            "Bank", "Officer", LocalDate.of(2025, 1, 1), LocalDate.of(2024, 12, 31), false);

    assertCode("INVALID_EXPERIENCE_DATES", () -> service.createExperience(request));
    verifyNoInteractions(jdbc);
  }

  @Test
  void rejectsCurrentExperienceWithEndDate() {
    var request =
        new ApplicantController.ExperienceRequest(
            "Bank", "Officer", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), true);

    assertCode("INVALID_EXPERIENCE_DATES", () -> service.createExperience(request));
    verifyNoInteractions(jdbc);
  }

  @Test
  void repeatedEducationCreateReturnsExistingRecord() {
    authenticate(5);
    var request =
        new ApplicantController.EducationRequest(
            1,
            null,
            null,
            "University",
            "CGPA",
            new BigDecimal("3.50"),
            new BigDecimal("4.00"),
            null,
            2024,
            true);
    when(jdbc.queryForList(
            "SELECT applicant_id FROM dbo.applicant_profile WHERE user_id=?", Long.class, 5L))
        .thenReturn(List.of(10L));
    when(jdbc.queryForObject(
            "SELECT applicant_id FROM dbo.applicant_profile WITH(UPDLOCK,HOLDLOCK) WHERE applicant_id=?",
            Long.class,
            10L))
        .thenReturn(10L);
    when(jdbc.queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT education_id"),
            org.mockito.ArgumentMatchers.eq(Long.class),
            org.mockito.ArgumentMatchers.any(Object[].class)))
        .thenReturn(List.of(77L));
    when(jdbc.queryForMap(
            "SELECT * FROM dbo.applicant_education WHERE education_id=? AND applicant_id=?",
            77L,
            10L))
        .thenReturn(Map.of("education_id", 77L));

    assertEquals(77L, service.createEducation(request).get("education_id"));
  }

  private void authenticate(long userId) {
    var principal = new AuthenticatedUser(userId, Set.of("APPLICANT"));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
  }

  private void assertCode(String code, Runnable action) {
    ApiException exception = assertThrows(ApiException.class, action::run);
    assertEquals(code, exception.code());
  }
}
