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
    LocalDate dob =
        jdbc.query(
            "SELECT date_of_birth FROM dbo.applicant_profile WHERE applicant_id=?",
            rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null,
            applicantId);
    LocalDate ref =
        jdbc.query(
            "SELECT age_reference_date FROM dbo.job_posting WHERE job_id=?",
            rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null,
            jobId);
    if (ref == null) failures.add("AGE_REFERENCE_DATE_REQUIRED");
    Integer max =
        jdbc.query(
            "SELECT maximum_age FROM dbo.job_age_policy WHERE job_id=? AND rules_version=? AND applicant_category='GENERAL'",
            rs -> rs.next() ? rs.getInt(1) : null,
            jobId,
            rulesVersion);
    if (dob == null) failures.add("DATE_OF_BIRTH_REQUIRED");
    else if (ref != null && max != null && Period.between(dob, ref).getYears() > max)
      failures.add("AGE_LIMIT_EXCEEDED");
    Integer missingEducation =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM dbo.job_education_requirement requirement
            JOIN dbo.qualification required_qualification
              ON required_qualification.qualification_id=requirement.qualification_id
            WHERE requirement.job_id=?
              AND requirement.rules_version=?
              AND NOT EXISTS (
                SELECT 1
                FROM dbo.applicant_education education
                JOIN dbo.qualification applicant_qualification
                  ON applicant_qualification.qualification_id=education.qualification_id
                WHERE education.applicant_id=?
                  AND (
                    (requirement.match_mode='EXACT'
                      AND education.qualification_id=requirement.qualification_id)
                    OR (requirement.match_mode='EQUIVALENT_LEVEL'
                      AND applicant_qualification.level_rank=required_qualification.level_rank
                      AND required_qualification.level_rank>0)
                    OR (requirement.match_mode='MINIMUM_LEVEL'
                      AND applicant_qualification.level_rank>=required_qualification.level_rank
                      AND required_qualification.level_rank>0)
                  )
                  AND (
                    requirement.minimum_result IS NULL
                    OR (
                      requirement.result_type='GPA'
                      AND education.result_type IN ('GPA','CGPA')
                      AND education.result_value>=requirement.minimum_result
                    )
                    OR (
                      requirement.result_type='DIVISION'
                      AND education.result_type IN ('DIVISION','CLASS')
                      AND CASE
                        WHEN UPPER(education.result_grade) IN ('FIRST','1ST','FIRST DIVISION','FIRST CLASS') THEN 1
                        WHEN UPPER(education.result_grade) IN ('SECOND','2ND','SECOND DIVISION','SECOND CLASS') THEN 2
                        WHEN UPPER(education.result_grade) IN ('THIRD','3RD','THIRD DIVISION','THIRD CLASS') THEN 3
                        ELSE 99
                      END<=requirement.minimum_result
                    )
                  )
              )
            """,
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
