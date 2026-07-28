package com.uttarabank.careerportal.eligibility;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EligibilityService {
  private final JdbcTemplate jdbc;

  public EligibilityService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Result evaluate(long applicationId, long jobId, long applicantId, int rulesVersion) {
    List<String> failures = new ArrayList<>();
    var profile =
        jdbc.queryForMap(
            "SELECT date_of_birth FROM dbo.applicant_profile WHERE applicant_id=?", applicantId);
    LocalDate dob = (LocalDate) profile.get("date_of_birth");
    LocalDate ref =
        jdbc.queryForObject(
            "SELECT age_reference_date FROM dbo.job_posting WHERE job_id=?",
            LocalDate.class,
            jobId);
    Integer max =
        jdbc.query(
            "SELECT maximum_age FROM dbo.job_age_policy WHERE job_id=? AND rules_version=? AND applicant_category='GENERAL'",
            rs -> rs.next() ? rs.getInt(1) : null,
            jobId,
            rulesVersion);
    if (dob == null) failures.add("DATE_OF_BIRTH_REQUIRED");
    else if (max != null && Period.between(dob, ref).getYears() > max)
      failures.add("AGE_LIMIT_EXCEEDED");
    Integer missingEducation =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.job_education_requirement r WHERE r.job_id=? AND r.rules_version=? AND NOT EXISTS(SELECT 1 FROM dbo.applicant_education e WHERE e.applicant_id=? AND e.qualification_id=r.qualification_id AND (r.minimum_result IS NULL OR e.result_value>=r.minimum_result))",
            Integer.class,
            jobId,
            rulesVersion,
            applicantId);
    if (missingEducation != null && missingEducation > 0)
      failures.add("EDUCATION_REQUIREMENT_NOT_MET");
    Integer months =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(DATEDIFF(MONTH,start_date,COALESCE(end_date,CAST(SYSUTCDATETIME() AS DATE)))),0) FROM dbo.applicant_experience WHERE applicant_id=?",
            Integer.class,
            applicantId);
    Integer minMonths =
        jdbc.query(
            "SELECT MAX(minimum_months) FROM dbo.job_experience_requirement WHERE job_id=? AND rules_version=?",
            rs -> rs.next() ? rs.getInt(1) : 0,
            jobId,
            rulesVersion);
    if (months < minMonths) failures.add("EXPERIENCE_REQUIREMENT_NOT_MET");
    Integer missingDocs =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.job_document_requirement r WHERE r.job_id=? AND r.rules_version=? AND r.mandatory=1 AND NOT EXISTS(SELECT 1 FROM dbo.applicant_document d JOIN dbo.file_asset f ON f.file_id=d.file_id WHERE d.applicant_id=? AND d.document_type=r.document_type AND d.active=1 AND f.validation_status='VALID')",
            Integer.class,
            jobId,
            rulesVersion,
            applicantId);
    if (missingDocs != null && missingDocs > 0) failures.add("MANDATORY_DOCUMENT_MISSING");
    return new Result(failures.isEmpty(), failures);
  }

  public record Result(boolean eligible, List<String> failures) {}
}
