package com.uttarabank.careerportal.job;

import com.uttarabank.careerportal.common.ApiException;
import java.sql.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {
  private final JdbcTemplate jdbc;

  public JobService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> publicJobs() {
    return jdbc.queryForList(
        "SELECT job_id,job_code,job_title,vacancy_count,employment_type,application_start_at,application_end_at FROM dbo.job_posting WHERE status='PUBLISHED' ORDER BY application_end_at");
  }

  public Map<String, Object> publicJob(long id) {
    return one(id);
  }

  @Transactional
  public Map<String, Object> create(JobController.JobRequest r, long userId) {
    checkWindow(r);
    var keys = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT dbo.job_posting(job_code,job_title,department_id,job_description,responsibilities,vacancy_count,employment_type,application_start_at,application_end_at,age_reference_date,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
          p.setString(1, r.jobCode().strip());
          p.setString(2, r.jobTitle().strip());
          p.setLong(3, r.departmentId());
          p.setString(4, r.jobDescription().strip());
          p.setString(5, trim(r.responsibilities()));
          p.setInt(6, r.vacancyCount());
          p.setString(7, r.employmentType());
          p.setTimestamp(8, Timestamp.from(r.applicationStartAt().toInstant()));
          p.setTimestamp(9, Timestamp.from(r.applicationEndAt().toInstant()));
          p.setDate(10, java.sql.Date.valueOf(r.ageReferenceDate()));
          p.setLong(11, userId);
          return p;
        },
        keys);
    long jobId = Objects.requireNonNull(keys.getKey()).longValue();
    saveExtendedConfiguration(jobId, r);
    return one(jobId);
  }

  @Transactional
  public Map<String, Object> update(long id, JobController.JobRequest r) {
    checkWindow(r);
    ensureEditable(id);
    jdbc.update(
        "UPDATE dbo.job_posting SET job_code=?,job_title=?,department_id=?,job_description=?,responsibilities=?,vacancy_count=?,employment_type=?,application_start_at=?,application_end_at=?,age_reference_date=?,version=version+1 WHERE job_id=?",
        r.jobCode().strip(),
        r.jobTitle().strip(),
        r.departmentId(),
        r.jobDescription().strip(),
        trim(r.responsibilities()),
        r.vacancyCount(),
        r.employmentType(),
        r.applicationStartAt().toInstant(),
        r.applicationEndAt().toInstant(),
        r.ageReferenceDate(),
        id);
    saveExtendedConfiguration(id, r);
    return one(id);
  }

  @Transactional
  public Map<String, Object> transition(long id, String from, String to, Long actor) {
    int n =
        actor == null
            ? jdbc.update(
                "UPDATE dbo.job_posting SET status=?,version=version+1 WHERE job_id=? AND status=?",
                to,
                id,
                from)
            : jdbc.update(
                "UPDATE dbo.job_posting SET status=?,published_by=?,published_at=SYSUTCDATETIME(),version=version+1 WHERE job_id=? AND status=?",
                to,
                actor,
                id,
                from);
    if (n == 0)
      throw new ApiException(
          HttpStatus.CONFLICT, "JOB_STATE_CONFLICT", "Job is not in the required state.");
    return one(id);
  }

  private void ensureEditable(long id) {
    String s =
        jdbc.queryForObject("SELECT status FROM dbo.job_posting WHERE job_id=?", String.class, id);
    if (!Set.of("DRAFT", "APPROVED").contains(s))
      throw new ApiException(
          HttpStatus.CONFLICT, "PUBLISHED_JOB_IMMUTABLE", "Published job rules cannot be changed.");
  }

  private void checkWindow(JobController.JobRequest r) {
    if (!r.applicationEndAt().isAfter(r.applicationStartAt()))
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_APPLICATION_WINDOW",
          "Application end must be after start.");
  }

  private void saveExtendedConfiguration(long jobId, JobController.JobRequest r) {
    jdbc.update(
        """
        UPDATE dbo.job_posting SET
          designation=?,experience_type=?,job_location=?,salary_details=?,
          publication_channel=?,job_context=?,additional_requirements=?,
          compensation_benefits=?,apply_page_header=?,
          specific_education_required=?,existing_employee_eligible=?,
          external_applicant_eligible=?,maximum_designation=?,
          spouse_data_required=?,mobile_required=?,email_required=?,
          relative_declaration_required=?,allow_other_post_application=?,
          cover_letter_cv_required=?,circular_letter_name=?
        WHERE job_id=?
        """,
        trim(r.designation()),
        trim(r.experienceType()),
        trim(r.jobLocation()),
        trim(r.salaryDetails()),
        trim(r.publicationChannel()),
        trim(r.jobContext()),
        trim(r.additionalRequirements()),
        trim(r.compensationBenefits()),
        trim(r.applyPageHeader()),
        r.specificEducationRequired(),
        r.existingEmployeeEligible(),
        r.externalApplicantEligible(),
        trim(r.maximumDesignation()),
        r.spouseDataRequired(),
        r.mobileRequired(),
        r.emailRequired(),
        r.relativeDeclarationRequired(),
        r.allowOtherPostApplication(),
        r.coverLetterCvRequired(),
        trim(r.circularLetterName()),
        jobId);

    jdbc.update("DELETE FROM dbo.job_age_policy WHERE job_id=?", jobId);
    if (r.existingEmployeeEligible() && r.existingEmployeeMaxAge() != null)
      jdbc.update(
          "INSERT dbo.job_age_policy(job_id,applicant_category,maximum_age,age_reference_date,rules_version) VALUES(?,'BANK_STAFF',?,?,1)",
          jobId,
          r.existingEmployeeMaxAge(),
          r.ageReferenceDate());
    if (r.externalApplicantEligible() && r.externalApplicantMaxAge() != null)
      jdbc.update(
          "INSERT dbo.job_age_policy(job_id,applicant_category,maximum_age,age_reference_date,rules_version) VALUES(?,'GENERAL',?,?,1)",
          jobId,
          r.externalApplicantMaxAge(),
          r.ageReferenceDate());

    jdbc.update("DELETE FROM dbo.job_education_requirement WHERE job_id=?", jobId);
    if (r.specificEducationRequired() && r.educationRequirements() != null)
      for (var requirement : r.educationRequirements())
        jdbc.update(
            "INSERT dbo.job_education_requirement(job_id,qualification_id,minimum_result,rules_version,result_type) VALUES(?,?,?,1,?)",
            jobId,
            requirement.qualificationId(),
            requirement.minimumResult(),
            requirement.resultType());
  }

  private Map<String, Object> one(long id) {
    var rows = jdbc.queryForList("SELECT * FROM dbo.job_posting WHERE job_id=?", id);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found.");
    return rows.getFirst();
  }

  private String trim(String s) {
    return s == null ? null : s.strip();
  }
}
