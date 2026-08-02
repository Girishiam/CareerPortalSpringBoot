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
        "SELECT j.job_id,j.job_code,j.job_title,j.designation,j.job_location,j.vacancy_count,j.employment_type,j.experience_type,j.salary_details,j.application_start_at,j.application_end_at,j.circular_letter_name,CAST(CASE WHEN p.job_id IS NULL THEN 0 ELSE 1 END AS BIT) circular_pdf_available FROM dbo.job_posting j LEFT JOIN dbo.job_circular_pdf p ON p.job_id=j.job_id WHERE j.status='PUBLISHED' ORDER BY j.application_end_at");
  }

  public Map<String, Object> publicJob(long id) {
    return one(id);
  }

  public Map<String, Object> adminJob(long id) {
    Map<String, Object> result = new LinkedHashMap<>(one(id));
    List<Map<String, Object>> agePolicies =
        jdbc.queryForList(
            "SELECT applicant_category,maximum_age FROM dbo.job_age_policy WHERE job_id=?",
            id);
    for (Map<String, Object> policy : agePolicies) {
      String category = Objects.toString(value(policy, "applicant_category"));
      if ("BANK_STAFF".equals(category))
        result.put("existing_employee_max_age", value(policy, "maximum_age"));
      if ("GENERAL".equals(category))
        result.put("external_applicant_max_age", value(policy, "maximum_age"));
    }
    result.put(
        "educationRequirements",
        jdbc.queryForList(
            """
            SELECT qualification_id,minimum_result,result_type,match_mode
              FROM dbo.job_education_requirement
             WHERE job_id=?
             ORDER BY requirement_id
            """,
            id));
    return result;
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
  public Map<String, Object> updateSchedule(long id, JobController.ScheduleRequest request) {
    if (!request.applicationEndAt().isAfter(request.applicationStartAt()))
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_APPLICATION_WINDOW",
          "Application end must be after start.");
    int updated =
        jdbc.update(
            connection -> {
              PreparedStatement statement =
                  connection.prepareStatement(
                      "UPDATE dbo.job_posting SET application_start_at=?,application_end_at=? WHERE job_id=? AND status IN ('DRAFT','APPROVED','PUBLISHED')");
              statement.setTimestamp(1, Timestamp.from(request.applicationStartAt().toInstant()));
              statement.setTimestamp(2, Timestamp.from(request.applicationEndAt().toInstant()));
              statement.setLong(3, id);
              return statement;
            });
    if (updated == 0)
      throw new ApiException(
          HttpStatus.CONFLICT,
          "JOB_SCHEDULE_NOT_EDITABLE",
          "The schedule of a closed job cannot be changed.");
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

  @Transactional
  public void delete(long id) {
    List<String> statuses =
        jdbc.queryForList(
            "SELECT status FROM dbo.job_posting WITH(UPDLOCK,HOLDLOCK) WHERE job_id=?",
            String.class,
            id);
    if (statuses.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found.");
    String status = statuses.getFirst();
    if ("CLOSED".equals(status)) {
      jdbc.update(
          "UPDATE dbo.job_posting SET is_archived=1,version=version+1 WHERE job_id=?",
          id);
      return;
    }
    if (!"DRAFT".equals(status))
      throw new ApiException(
          HttpStatus.CONFLICT,
          "JOB_NOT_DELETABLE",
          "Only draft or closed jobs can be removed.");
    Integer applications =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.job_application WHERE job_id=?", Integer.class, id);
    if (applications != null && applications > 0)
      throw new ApiException(
          HttpStatus.CONFLICT,
          "JOB_HAS_APPLICATIONS",
          "This job cannot be deleted because applications already exist.");

    if (tableExists("recruitment_stage")) {
      if (tableExists("stage_candidate"))
        jdbc.update(
            "DELETE candidate FROM dbo.stage_candidate candidate JOIN dbo.recruitment_stage stage ON stage.stage_id=candidate.stage_id WHERE stage.job_id=?",
            id);
      if (tableExists("shortlist_batch"))
        jdbc.update(
            "DELETE batch FROM dbo.shortlist_batch batch JOIN dbo.recruitment_stage stage ON stage.stage_id=batch.stage_id WHERE stage.job_id=?",
            id);
      jdbc.update("DELETE dbo.recruitment_stage WHERE job_id=?", id);
    }
    for (String table :
        List.of(
            "job_circular_pdf",
            "job_document_requirement",
            "job_experience_requirement",
            "job_education_requirement",
            "job_age_policy"))
      if (tableExists(table)) jdbc.update("DELETE dbo." + table + " WHERE job_id=?", id);
    jdbc.update("DELETE dbo.job_posting WHERE job_id=?", id);
  }

  private boolean tableExists(String table) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND name=?",
            Integer.class,
            table);
    return count != null && count > 0;
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
          multiple_application_restricted=?,
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
        !r.multipleApplicationRestricted(),
        r.multipleApplicationRestricted(),
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
    if (r.specificEducationRequired()
        && (r.educationRequirements() == null || r.educationRequirements().isEmpty()))
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "EDUCATION_REQUIREMENTS_REQUIRED",
          "Add at least one education requirement.");
    if (r.specificEducationRequired() && r.educationRequirements() != null) {
      Set<String> uniqueRequirements = new HashSet<>();
      for (var requirement : r.educationRequirements()) {
        String matchMode =
            requirement.matchMode() == null ? "EXACT" : requirement.matchMode();
        if (!uniqueRequirements.add(requirement.qualificationId() + ":" + matchMode))
          throw new ApiException(
              HttpStatus.BAD_REQUEST,
              "DUPLICATE_EDUCATION_REQUIREMENT",
              "The same education requirement was added more than once.");
        jdbc.update(
            "INSERT dbo.job_education_requirement(job_id,qualification_id,minimum_result,rules_version,result_type,match_mode) VALUES(?,?,?,1,?,?)",
            jobId,
            requirement.qualificationId(),
            requirement.minimumResult(),
            requirement.resultType(),
            matchMode);
      }
    }
  }

  private Map<String, Object> one(long id) {
    var rows =
        jdbc.queryForList(
            "SELECT j.*,CAST(CASE WHEN p.job_id IS NULL THEN 0 ELSE 1 END AS BIT) circular_pdf_available,(SELECT COUNT(*) FROM dbo.job_application a WHERE a.job_id=j.job_id) application_count FROM dbo.job_posting j LEFT JOIN dbo.job_circular_pdf p ON p.job_id=j.job_id WHERE j.job_id=?",
            id);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found.");
    return rows.getFirst();
  }

  private String trim(String s) {
    return s == null ? null : s.strip();
  }

  private Object value(Map<String, Object> row, String key) {
    Object result = row.get(key);
    return result != null ? result : row.get(key.toUpperCase(Locale.ROOT));
  }
}
