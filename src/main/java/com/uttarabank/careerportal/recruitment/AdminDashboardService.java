package com.uttarabank.careerportal.recruitment;

import com.uttarabank.careerportal.common.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {
  private final JdbcTemplate jdbc;

  public AdminDashboardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> dashboard() {
    Map<String, Object> metrics =
        new LinkedHashMap<>(
            jdbc.queryForMap(
                """
                SELECT
                  (SELECT COUNT(*) FROM dbo.job_posting) AS total_jobs,
                  (SELECT COUNT(*) FROM dbo.job_posting WHERE status = 'PUBLISHED') AS published_jobs,
                  (SELECT COUNT(*) FROM dbo.applicant_profile) AS total_applicants,
                  (SELECT COUNT(*) FROM dbo.job_application WHERE status = 'SUBMITTED') AS submitted_applications
                """));
    metrics.put(
        "recentApplications",
        jdbc.queryForList(
            """
            SELECT TOP (8)
              application.application_id,
              application.tracking_number,
              application.status,
              application.submitted_at,
              job.job_code,
              job.job_title,
              snapshot.full_name
            FROM dbo.job_application AS application
            INNER JOIN dbo.job_posting AS job
              ON job.job_id = application.job_id
            LEFT JOIN dbo.application_profile_snapshot AS snapshot
              ON snapshot.application_id = application.application_id
            ORDER BY application.created_at DESC
            """));
    return metrics;
  }

  public List<Map<String, Object>> jobs() {
    return jdbc.queryForList(
        """
        SELECT
          job.job_id,
          job.job_code,
          job.job_title,
          job.status,
          job.vacancy_count,
          job.employment_type,
          job.application_start_at,
          job.application_end_at,
          job.version,
          COUNT(application.application_id) AS application_count
        FROM dbo.job_posting AS job
        LEFT JOIN dbo.job_application AS application
          ON application.job_id = job.job_id
        GROUP BY
          job.job_id,
          job.job_code,
          job.job_title,
          job.status,
          job.vacancy_count,
          job.employment_type,
          job.application_start_at,
          job.application_end_at,
          job.version
        ORDER BY job.job_id DESC
        """);
  }

  public Map<String, Object> applications(long jobId, int page, int size) {
    Integer jobCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.job_posting WHERE job_id=?", Integer.class, jobId);
    if (jobCount == null || jobCount == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found.");
    }

    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.job_application WHERE job_id=?", Long.class, jobId);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT
              application.application_id,
              application.tracking_number,
              application.status,
              application.eligibility_status,
              application.submitted_at,
              applicant.cv_number,
              COALESCE(snapshot.full_name, applicant.full_name) AS full_name,
              account.email,
              account.mobile
            FROM dbo.job_application AS application
            INNER JOIN dbo.applicant_profile AS applicant
              ON applicant.applicant_id = application.applicant_id
            INNER JOIN dbo.user_account AS account
              ON account.user_id = applicant.user_id
            LEFT JOIN dbo.application_profile_snapshot AS snapshot
              ON snapshot.application_id = application.application_id
            WHERE application.job_id = ?
            ORDER BY application.created_at DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """,
            jobId,
            page * size,
            size);

    return Map.of(
        "content", rows,
        "page", page,
        "size", size,
        "totalElements", Objects.requireNonNullElse(total, 0L),
        "totalPages", (int) Math.ceil((double) Objects.requireNonNullElse(total, 0L) / size));
  }

  public Map<String, Object> application(long applicationId) {
    List<Map<String, Object>> records =
        jdbc.queryForList(
            """
            SELECT
              application.application_id,
              application.tracking_number,
              application.status,
              application.eligibility_status,
              application.submitted_at,
              job.job_id,
              job.job_code,
              job.job_title,
              applicant.cv_number,
              COALESCE(snapshot.full_name, applicant.full_name) AS full_name,
              snapshot.father_name,
              snapshot.mother_name,
              snapshot.date_of_birth,
              snapshot.gender,
              snapshot.marital_status,
              snapshot.nationality,
              snapshot.nid_number,
              account.email,
              account.mobile
            FROM dbo.job_application AS application
            INNER JOIN dbo.job_posting AS job
              ON job.job_id = application.job_id
            INNER JOIN dbo.applicant_profile AS applicant
              ON applicant.applicant_id = application.applicant_id
            INNER JOIN dbo.user_account AS account
              ON account.user_id = applicant.user_id
            LEFT JOIN dbo.application_profile_snapshot AS snapshot
              ON snapshot.application_id = application.application_id
            WHERE application.application_id = ?
            """,
            applicationId);

    if (records.isEmpty()) {
      throw new ApiException(
          HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Application not found.");
    }

    Map<String, Object> result = new LinkedHashMap<>(records.getFirst());
    result.put(
        "educations",
        jdbc.queryForList(
            """
            SELECT
              qualification_id,
              subject_id,
              institution_name,
              result_type,
              result_value,
              result_scale,
              result_grade,
              passing_year
            FROM dbo.application_education_snapshot
            WHERE application_id = ?
            ORDER BY passing_year DESC
            """,
            applicationId));
    result.put(
        "experiences",
        jdbc.queryForList(
            """
            SELECT employer_name, designation, start_date, end_date
            FROM dbo.application_experience_snapshot
            WHERE application_id = ?
            ORDER BY start_date DESC
            """,
            applicationId));
    result.put(
        "documents",
        jdbc.queryForList(
            """
            SELECT document.document_type, file_asset.original_name, file_asset.validation_status
            FROM dbo.application_document AS document
            INNER JOIN dbo.file_asset AS file_asset
              ON file_asset.file_id = document.file_id
            WHERE document.application_id = ?
            ORDER BY document.document_type
            """,
            applicationId));
    return result;
  }

  public Map<String, Object> users(int page, int size) {
    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.user_account", Long.class);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT
              account.user_id,
              account.email,
              account.mobile,
              account.username,
              account.employee_id,
              account.status,
              account.created_at,
              STRING_AGG(role.code, ', ') AS roles
            FROM dbo.user_account AS account
            LEFT JOIN dbo.user_role AS user_role
              ON user_role.user_id = account.user_id
            LEFT JOIN dbo.role AS role
              ON role.role_id = user_role.role_id
            GROUP BY
              account.user_id,
              account.email,
              account.mobile,
              account.username,
              account.employee_id,
              account.status,
              account.created_at
            ORDER BY account.created_at DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """,
            page * size,
            size);

    long totalElements = Objects.requireNonNullElse(total, 0L);
    return Map.of(
        "content", rows,
        "page", page,
        "size", size,
        "totalElements", totalElements,
        "totalPages", (int) Math.ceil((double) totalElements / size));
  }
}
