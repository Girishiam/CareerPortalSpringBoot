package com.uttarabank.careerportal.recruitment;

import com.uttarabank.careerportal.common.ApiException;
import java.sql.Timestamp;
import java.time.*;
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
        "jobApplicationCounts",
        jdbc.queryForList(
            """
            SELECT job.job_id,job.job_code,job.job_title,job.status,job.vacancy_count,
              COUNT(CASE WHEN application.status='SUBMITTED' THEN 1 END) applicant_count,
              COUNT(CASE WHEN application.status='SUBMITTED' AND application.eligibility_status='ELIGIBLE' THEN 1 END) eligible_count,
              COUNT(CASE WHEN application.status='SUBMITTED' AND application.eligibility_status='INELIGIBLE' THEN 1 END) ineligible_count
            FROM dbo.job_posting job
            LEFT JOIN dbo.job_application application ON application.job_id=job.job_id
            WHERE job.is_archived=0
            GROUP BY job.job_id,job.job_code,job.job_title,job.status,job.vacancy_count
            ORDER BY applicant_count DESC,job.job_id DESC
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
              COALESCE(snapshot.full_name, applicant.full_name) AS full_name
            FROM dbo.job_application AS application
            INNER JOIN dbo.job_posting AS job
              ON job.job_id = application.job_id
            INNER JOIN dbo.applicant_profile AS applicant
              ON applicant.applicant_id = application.applicant_id
            LEFT JOIN dbo.application_profile_snapshot AS snapshot
              ON snapshot.application_id = application.application_id
            WHERE application.status = 'SUBMITTED'
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
          job.designation,
          job.job_location,
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
        WHERE job.is_archived = 0
        GROUP BY
          job.job_id,
          job.job_code,
          job.job_title,
          job.designation,
          job.job_location,
          job.status,
          job.vacancy_count,
          job.employment_type,
          job.application_start_at,
          job.application_end_at,
          job.version
        ORDER BY job.job_id DESC
        """);
  }

  public Map<String, Object> applications(
      Long jobId,
      String trackingNumber,
      String cvNumber,
      String mobile,
      String email,
      String candidateName,
      String eligibility,
      LocalDate submittedFrom,
      LocalDate submittedTo,
      int page,
      int size) {
    Search search =
        search(
            jobId,
            trackingNumber,
            cvNumber,
            mobile,
            email,
            candidateName,
            eligibility,
            submittedFrom,
            submittedTo);
    Long total =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.job_application application
            JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id
            JOIN dbo.user_account account ON account.user_id=applicant.user_id
            LEFT JOIN dbo.application_profile_snapshot snapshot ON snapshot.application_id=application.application_id
            """
                + search.where(),
            Long.class,
            search.parameters().toArray());
    List<Object> parameters = new ArrayList<>(search.parameters());
    parameters.add((long) page * size);
    parameters.add(size);
    List<Map<String, Object>> rows =
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
              CONCAT(
                COALESCE(applicant.email,account.email,''),
                CASE
                  WHEN COALESCE(applicant.mobile,account.mobile) IS NULL THEN ''
                  ELSE CONCAT(' | ',COALESCE(applicant.mobile,account.mobile))
                END
              ) email,
              COALESCE(applicant.mobile,account.mobile) mobile
            FROM dbo.job_application AS application
            INNER JOIN dbo.job_posting AS job
              ON job.job_id = application.job_id
            INNER JOIN dbo.applicant_profile AS applicant
              ON applicant.applicant_id = application.applicant_id
            INNER JOIN dbo.user_account AS account
              ON account.user_id = applicant.user_id
            LEFT JOIN dbo.application_profile_snapshot AS snapshot
              ON snapshot.application_id = application.application_id
            """
                + search.where()
                + """
            ORDER BY application.submitted_at DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """,
            parameters.toArray());

    return Map.of(
        "content", rows,
        "page", page,
        "size", size,
        "totalElements", Objects.requireNonNullElse(total, 0L),
        "totalPages", (int) Math.ceil((double) Objects.requireNonNullElse(total, 0L) / size));
  }

  public byte[] exportApplications(
      Long jobId,
      String trackingNumber,
      String cvNumber,
      String mobile,
      String email,
      String candidateName,
      String eligibility,
      LocalDate submittedFrom,
      LocalDate submittedTo) {
    return ApplicationXlsxWriter.write(
        applicationExportRows(
            jobId,
            trackingNumber,
            cvNumber,
            mobile,
            email,
            candidateName,
            eligibility,
            submittedFrom,
            submittedTo));
  }

  List<Map<String, Object>> applicationExportRows(
      Long jobId,
      String trackingNumber,
      String cvNumber,
      String mobile,
      String email,
      String candidateName,
      String eligibility,
      LocalDate submittedFrom,
      LocalDate submittedTo) {
    Search search =
        search(
            jobId,
            trackingNumber,
            cvNumber,
            mobile,
            email,
            candidateName,
            eligibility,
            submittedFrom,
            submittedTo);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT application.application_id,application.tracking_number,application.submitted_at,
                   application.status,application.eligibility_status,
                   job.job_code,job.job_title,job.designation job_designation,
                   job.employment_type,job.job_location,applicant.cv_number,
                   COALESCE(snapshot.full_name,applicant.full_name) full_name,
                   applicant.father_name,applicant.mother_name,applicant.date_of_birth,
                   applicant.gender,applicant.marital_status,applicant.nationality,
                   applicant.nid_number,applicant.passport_number,
                   COALESCE(applicant.email,account.email) email,
                   COALESCE(applicant.mobile,account.mobile) mobile,
                   addresses.present_address,addresses.permanent_address,
                   education.education,experience.experience,training.training,
                   languages.languages,activities.activities,
                   reference_data.reference_details,documents.documents
            FROM dbo.job_application application
            JOIN dbo.job_posting job ON job.job_id=application.job_id
            JOIN dbo.applicant_profile applicant
              ON applicant.applicant_id=application.applicant_id
            JOIN dbo.user_account account ON account.user_id=applicant.user_id
            LEFT JOIN dbo.application_profile_snapshot snapshot
              ON snapshot.application_id=application.application_id
            OUTER APPLY (
              SELECT
                MAX(CASE WHEN address.address_type='PRESENT' THEN
                  CONCAT(address.address_line,', ',upazila.name,', ',district.name,', ',division.name,
                    CASE WHEN address.postcode IS NULL THEN '' ELSE CONCAT(' - ',address.postcode) END)
                END) present_address,
                MAX(CASE WHEN address.address_type='PERMANENT' THEN
                  CONCAT(address.address_line,', ',upazila.name,', ',district.name,', ',division.name,
                    CASE WHEN address.postcode IS NULL THEN '' ELSE CONCAT(' - ',address.postcode) END)
                END) permanent_address
              FROM dbo.applicant_address address
              LEFT JOIN dbo.division division ON division.division_id=address.division_id
              LEFT JOIN dbo.district district ON district.district_id=address.district_id
              LEFT JOIN dbo.upazila upazila ON upazila.upazila_id=address.upazila_id
              WHERE address.applicant_id=applicant.applicant_id
            ) addresses
            OUTER APPLY (
              SELECT STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                qualification.name,
                CASE WHEN subject.name IS NULL THEN '' ELSE CONCAT(' - ',subject.name) END,
                ' | ',COALESCE(institution.name,record.institution_name,''),
                ' | ',record.result_type,' ',
                COALESCE(CONVERT(VARCHAR(30),record.result_value),record.result_grade,''),
                CASE WHEN record.result_scale IS NULL THEN '' ELSE CONCAT('/',record.result_scale) END,
                ' | ',record.passing_year,
                CASE WHEN record.is_highest_degree=1 THEN ' | Highest degree' ELSE '' END
              )),N' || ') education
              FROM dbo.applicant_education record
              LEFT JOIN dbo.qualification qualification
                ON qualification.qualification_id=record.qualification_id
              LEFT JOIN dbo.subject subject ON subject.subject_id=record.subject_id
              LEFT JOIN dbo.institution institution ON institution.institution_id=record.institution_id
              WHERE record.applicant_id=applicant.applicant_id
            ) education
            OUTER APPLY (
              SELECT STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                record.employer_name,' | ',record.designation,' | ',record.start_date,' to ',
                COALESCE(CONVERT(VARCHAR(10),record.end_date,23),'Present')
              )),N' || ') experience
              FROM dbo.applicant_experience record
              WHERE record.applicant_id=applicant.applicant_id
            ) experience
            OUTER APPLY (
              SELECT STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                record.training_title,' | ',record.training_summary,' | ',
                record.duration_months,' month(s)'
              )),N' || ') training
              FROM dbo.applicant_training record
              WHERE record.applicant_id=applicant.applicant_id
            ) training
            OUTER APPLY (
              SELECT STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                record.language_name,' | Speaking: ',record.speaking,
                ', Writing: ',record.writing,', Listening: ',record.listening,
                ', Reading: ',record.reading
              )),N' || ') languages
              FROM dbo.applicant_language record
              WHERE record.applicant_id=applicant.applicant_id
            ) languages
            OUTER APPLY (
              SELECT STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                record.activity_name,' | ',record.organization,' | ',record.role_name,
                ' | ',record.activity_summary,' | ',record.achievement
              )),N' || ') activities
              FROM dbo.applicant_extracurricular_activity record
              WHERE record.applicant_id=applicant.applicant_id
            ) activities
            OUTER APPLY (
              SELECT STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                record.full_name,' | ',record.organization,' | ',record.designation,
                ' | ',record.relationship,' | ',record.email,' | ',record.mobile
              )),N' || ') reference_details
              FROM dbo.applicant_reference record
              WHERE record.applicant_id=applicant.applicant_id
            ) reference_data
            OUTER APPLY (
              SELECT STRING_AGG(CONVERT(NVARCHAR(MAX),CONCAT(
                document.document_type,' | ',file_asset.original_name,' | ',
                file_asset.media_type,' | ',file_asset.size_bytes,' bytes | ',
                file_asset.validation_status
              )),N' || ') documents
              FROM dbo.applicant_document document
              JOIN dbo.file_asset file_asset ON file_asset.file_id=document.file_id
              WHERE document.applicant_id=applicant.applicant_id AND document.active=1
            ) documents
            """
                + search.where()
                + """
            ORDER BY application.submitted_at DESC
            """,
            search.parameters().toArray());
    return rows;
  }

  public String applicationExportFilename(
      Long jobId,
      String trackingNumber,
      String cvNumber,
      String mobile,
      String email,
      String candidateName,
      String eligibility,
      LocalDate submittedFrom,
      LocalDate submittedTo) {
    String scope = "all-jobs";
    if (jobId != null) {
      List<Map<String, Object>> jobs =
          jdbc.queryForList("SELECT job_code,job_title FROM dbo.job_posting WHERE job_id=?", jobId);
      if (!jobs.isEmpty()) {
        Map<String, Object> job = jobs.getFirst();
        scope =
            "job-id-"
                + jobId
                + "-"
                + safeFilename(Objects.toString(job.get("job_code"), ""))
                + "-"
                + safeFilename(Objects.toString(job.get("job_title"), ""));
      }
    }
    List<String> filters = new ArrayList<>();
    if (hasText(trackingNumber)) filters.add("tracking-number");
    if (hasText(cvNumber)) filters.add("cv-number");
    if (hasText(mobile)) filters.add("mobile");
    if (hasText(email)) filters.add("email");
    if (hasText(candidateName)) filters.add("candidate-name");
    if (hasText(eligibility)) filters.add("eligibility-" + safeFilename(eligibility));
    if (submittedFrom != null) filters.add("submitted-from");
    if (submittedTo != null) filters.add("submitted-to");
    String filterScope =
        filters.isEmpty() ? "filters-none" : "filters-" + String.join("-", filters);
    String base = "candidate-applications-" + scope + "-" + filterScope + "-" + LocalDate.now();
    return (base.length() > 210 ? base.substring(0, 210).replaceAll("-+$", "") : base) + ".xlsx";
  }

  private static String safeFilename(String value) {
    String safe =
        value == null
            ? "export"
            : value.strip().replaceAll("[^\\p{L}\\p{N}._-]+", "-").replaceAll("^-+|-+$", "");
    return safe.isBlank() ? "export" : safe;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private Search search(
      Long jobId,
      String trackingNumber,
      String cvNumber,
      String mobile,
      String email,
      String candidateName,
      String eligibility,
      LocalDate submittedFrom,
      LocalDate submittedTo) {
    StringBuilder where = new StringBuilder(" WHERE application.status='SUBMITTED'");
    List<Object> parameters = new ArrayList<>();
    addEqual(where, parameters, "application.job_id", jobId);
    addEqual(where, parameters, "application.tracking_number", normalizeTracking(trackingNumber));
    addContains(where, parameters, "applicant.cv_number", cvNumber);
    addContains(where, parameters, "COALESCE(applicant.mobile,account.mobile)", mobile);
    addContains(where, parameters, "COALESCE(applicant.email,account.email)", email);
    addContains(
        where, parameters, "COALESCE(snapshot.full_name,applicant.full_name)", candidateName);
    addEqual(where, parameters, "application.eligibility_status", text(eligibility));
    ZoneId zone = ZoneId.of("Asia/Dhaka");
    if (submittedFrom != null) {
      where.append(" AND application.submitted_at>=?");
      parameters.add(Timestamp.from(submittedFrom.atStartOfDay(zone).toInstant()));
    }
    if (submittedTo != null) {
      where.append(" AND application.submitted_at<?");
      parameters.add(Timestamp.from(submittedTo.plusDays(1).atStartOfDay(zone).toInstant()));
    }
    return new Search(where.toString(), parameters);
  }

  private void addEqual(StringBuilder where, List<Object> parameters, String column, Object value) {
    if (value == null) return;
    where.append(" AND ").append(column).append("=?");
    parameters.add(value);
  }

  private void addContains(
      StringBuilder where, List<Object> parameters, String column, String value) {
    String normalized = text(value);
    if (normalized == null) return;
    where.append(" AND ").append(column).append(" LIKE ?");
    parameters.add("%" + normalized + "%");
  }

  private String text(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private String normalizeTracking(String value) {
    if (value == null || value.isBlank()) return null;
    String tracking = value.strip();
    if (!tracking.matches("\\d+"))
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_TRACKING_NUMBER",
          "Tracking number must contain digits only.");
    return tracking;
  }

  private record Search(String where, List<Object> parameters) {}

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
              application.applicant_id,
              job.job_id,
              job.job_code,
              job.job_title,
              job.designation AS job_designation,
              job.employment_type,
              job.job_location,
              applicant.cv_number,
              COALESCE(snapshot.full_name, applicant.full_name) AS full_name,
              COALESCE(snapshot.father_name,applicant.father_name) father_name,
              COALESCE(snapshot.mother_name,applicant.mother_name) mother_name,
              COALESCE(snapshot.date_of_birth,applicant.date_of_birth) date_of_birth,
              COALESCE(snapshot.gender,applicant.gender) gender,
              COALESCE(snapshot.marital_status,applicant.marital_status) marital_status,
              COALESCE(snapshot.nationality,applicant.nationality) nationality,
              COALESCE(snapshot.nid_number,applicant.nid_number) nid_number,
              applicant.passport_number,
              COALESCE(applicant.email,account.email) email,
              COALESCE(applicant.mobile,account.mobile) mobile
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
    long applicantId = ((Number) result.get("applicant_id")).longValue();
    result.put(
        "addresses",
        jdbc.queryForList(
            """
            SELECT address.address_type,address.address_line,address.postcode,
                   division.name division_name,district.name district_name,
                   upazila.name upazila_name
            FROM dbo.applicant_address address
            LEFT JOIN dbo.division division ON division.division_id=address.division_id
            LEFT JOIN dbo.district district ON district.district_id=address.district_id
            LEFT JOIN dbo.upazila upazila ON upazila.upazila_id=address.upazila_id
            WHERE address.applicant_id=?
            ORDER BY address.address_type DESC
            """,
            applicantId));
    result.put(
        "educations",
        jdbc.queryForList(
            """
            SELECT
              snapshot.qualification_id,
              COALESCE(NULLIF(LTRIM(RTRIM(snapshot.qualification_name)),''),qualification.name) qualification_name,
              snapshot.subject_id,
              COALESCE(NULLIF(LTRIM(RTRIM(snapshot.subject_name)),''),subject.name) subject_name,
              snapshot.institution_name,
              snapshot.result_type,
              snapshot.result_value,
              snapshot.result_scale,
              snapshot.result_grade,
              snapshot.passing_year
            FROM dbo.application_education_snapshot snapshot
            LEFT JOIN dbo.qualification qualification
              ON qualification.qualification_id=snapshot.qualification_id
            LEFT JOIN dbo.subject subject ON subject.subject_id=snapshot.subject_id
            WHERE snapshot.application_id = ?
            ORDER BY snapshot.passing_year DESC
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
            SELECT document.document_type,file_asset.original_name,file_asset.media_type,
                   file_asset.size_bytes,file_asset.validation_status,file_asset.created_at
            FROM dbo.application_document AS document
            INNER JOIN dbo.file_asset AS file_asset
              ON file_asset.file_id = document.file_id
            WHERE document.application_id = ?
            ORDER BY document.document_type
            """,
            applicationId));
    result.put(
        "trainings",
        jdbc.queryForList(
            "SELECT training_title,training_summary,duration_months FROM dbo.applicant_training WHERE applicant_id=? ORDER BY training_id",
            applicantId));
    result.put(
        "languages",
        jdbc.queryForList(
            "SELECT language_name,speaking,writing,listening,reading FROM dbo.applicant_language WHERE applicant_id=? ORDER BY language_name",
            applicantId));
    result.put(
        "activities",
        jdbc.queryForList(
            "SELECT activity_name,organization,role_name,activity_summary,achievement FROM dbo.applicant_extracurricular_activity WHERE applicant_id=? ORDER BY activity_id",
            applicantId));
    result.put(
        "references",
        jdbc.queryForList(
            "SELECT full_name,organization,designation,relationship,email,mobile FROM dbo.applicant_reference WHERE applicant_id=? ORDER BY reference_id",
            applicantId));
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
