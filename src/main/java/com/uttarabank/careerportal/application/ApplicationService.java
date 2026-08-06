package com.uttarabank.careerportal.application;

import com.uttarabank.careerportal.applicant.ApplicantService;
import com.uttarabank.careerportal.applicant.CvService;
import com.uttarabank.careerportal.common.ApiException;
import com.uttarabank.careerportal.eligibility.EligibilityService;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
  private static final SecureRandom TRACKING_RANDOM = new SecureRandom();
  private final JdbcTemplate jdbc;
  private final ApplicantService applicants;
  private final EligibilityService eligibility;
  private final CvService cv;

  public ApplicationService(
      JdbcTemplate jdbc,
      ApplicantService applicants,
      EligibilityService eligibility,
      CvService cv) {
    this.jdbc = jdbc;
    this.applicants = applicants;
    this.eligibility = eligibility;
    this.cv = cv;
  }

  public List<Map<String, Object>> applications() {
    return jdbc.queryForList(
        "SELECT a.application_id,a.status,a.tracking_number,a.eligibility_status,a.submitted_at,j.job_code,j.job_title FROM dbo.job_application a JOIN dbo.job_posting j ON j.job_id=a.job_id WHERE a.applicant_id=? ORDER BY a.created_at DESC",
        applicants.applicantId());
  }

  public Map<String, Object> application(long applicationId) {
    long applicantId = applicants.applicantId();
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
              snapshot.father_name,
              snapshot.mother_name,
              snapshot.date_of_birth,
              snapshot.gender,
              snapshot.marital_status,
              snapshot.nationality,
              snapshot.nid_number
            FROM dbo.job_application AS application
            INNER JOIN dbo.job_posting AS job ON job.job_id = application.job_id
            INNER JOIN dbo.applicant_profile AS applicant
              ON applicant.applicant_id = application.applicant_id
            LEFT JOIN dbo.application_profile_snapshot AS snapshot
              ON snapshot.application_id = application.application_id
            WHERE application.application_id = ?
              AND application.applicant_id = ?
            """,
            applicationId,
            applicantId);
    if (rows.isEmpty()) {
      throw new ApiException(
          HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Application not found.");
    }

    Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
    result.put(
        "educations",
        jdbc.queryForList(
            "SELECT qualification_id,qualification_name,subject_id,subject_name,institution_name,result_type,result_value,result_scale,result_grade,passing_year FROM dbo.application_education_snapshot WHERE application_id=? ORDER BY passing_year DESC",
            applicationId));
    result.put(
        "experiences",
        jdbc.queryForList(
            "SELECT employer_name,designation,start_date,end_date FROM dbo.application_experience_snapshot WHERE application_id=? ORDER BY start_date DESC",
            applicationId));
    result.put(
        "documents",
        jdbc.queryForList(
            "SELECT d.document_type,f.original_name,f.validation_status FROM dbo.application_document d JOIN dbo.file_asset f ON f.file_id=d.file_id WHERE d.application_id=? ORDER BY d.document_type",
            applicationId));
    return result;
  }

  @Transactional
  public ApplicationController.DraftResponse draft(long jobId) {
    long applicantId = applicants.applicantId();
    cv.requireComplete(applicantId);
    var jobs =
        jdbc.queryForList(
            "SELECT status,rules_version,multiple_application_restricted,application_start_at,application_end_at FROM dbo.job_posting WHERE job_id=?",
            jobId);
    if (jobs.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found.");
    Map<String, Object> job = jobs.getFirst();
    if (!"PUBLISHED".equals(job.get("status")))
      throw new ApiException(
          HttpStatus.CONFLICT, "JOB_NOT_ACCEPTING_APPLICATIONS", "This job is not published.");
    Instant now = Instant.now();
    Instant startsAt = ((Timestamp) job.get("application_start_at")).toInstant();
    Instant endsAt = ((Timestamp) job.get("application_end_at")).toInstant();
    if (now.isBefore(startsAt))
      throw new ApiException(
          HttpStatus.CONFLICT, "APPLICATION_NOT_OPEN", "Applications have not opened yet.");
    if (!now.isBefore(endsAt))
      throw new ApiException(
          HttpStatus.CONFLICT, "APPLICATION_CLOSED", "The application deadline has passed.");
    Integer otherPostConflict =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM dbo.job_application application
            JOIN dbo.job_posting existing_job ON existing_job.job_id=application.job_id
            WHERE application.applicant_id=?
              AND application.job_id<>?
              AND application.status='SUBMITTED'
              AND (
                existing_job.multiple_application_restricted=1
                OR ?=1
              )
            """,
            Integer.class,
            applicantId,
            jobId,
            Boolean.TRUE.equals(job.get("multiple_application_restricted"))
                    || Objects.equals(job.get("multiple_application_restricted"), 1)
                ? 1
                : 0);
    if (otherPostConflict != null && otherPostConflict > 0)
      throw new ApiException(
          HttpStatus.CONFLICT,
          "MULTIPLE_POST_APPLICATION_NOT_ALLOWED",
          "Multiple position applications are not allowed for this job.");
    var existing =
        jdbc.queryForList(
            "SELECT application_id,status FROM dbo.job_application WHERE job_id=? AND applicant_id=?",
            jobId,
            applicantId);
    long id;
    if (!existing.isEmpty()) {
      Map<String, Object> existingApplication = existing.getFirst();
      if ("SUBMITTED".equals(existingApplication.get("status")))
        throw new ApiException(
            HttpStatus.CONFLICT,
            "APPLICATION_ALREADY_SUBMITTED",
            "You have already submitted an application for this position.");
      id = ((Number) existingApplication.get("application_id")).longValue();
    } else {
      var keys = new GeneratedKeyHolder();
      int version = ((Number) job.get("rules_version")).intValue();
      jdbc.update(
          c -> {
            var p =
                c.prepareStatement(
                    "INSERT dbo.job_application(job_id,applicant_id,rules_version) VALUES(?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            p.setLong(1, jobId);
            p.setLong(2, applicantId);
            p.setInt(3, version);
            return p;
          },
          keys);
      id = Objects.requireNonNull(keys.getKey()).longValue();
    }
    List<String> missing = missing(applicantId);
    return new ApplicationController.DraftResponse(id, "DRAFT", missing.isEmpty(), missing);
  }

  @Transactional
  public ApplicationController.SubmitResponse submit(long applicationId, long userId) {
    long applicantId = applicants.applicantId();
    var rows =
        jdbc.queryForList(
            "SELECT a.job_id,a.rules_version FROM dbo.job_application a WITH(UPDLOCK,HOLDLOCK) JOIN dbo.job_posting j ON j.job_id=a.job_id WHERE a.application_id=? AND a.applicant_id=? AND a.status='DRAFT' AND SYSUTCDATETIME()<j.application_end_at",
            applicationId,
            applicantId);
    if (rows.isEmpty())
      throw new ApiException(
          HttpStatus.NOT_FOUND,
          "DRAFT_NOT_FOUND",
          "Draft application not found or deadline passed.");
    List<String> missing = missing(applicantId);
    if (!missing.isEmpty())
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "APPLICATION_INCOMPLETE",
          "Missing required sections: " + String.join(", ", missing));
    long jobId = ((Number) rows.getFirst().get("job_id")).longValue();
    int version = ((Number) rows.getFirst().get("rules_version")).intValue();
    var result = eligibility.evaluate(applicationId, jobId, applicantId, version);
    jdbc.update(
        "INSERT dbo.eligibility_evaluation(application_id,eligible,failures_json) VALUES(?,?,?)",
        applicationId,
        result.eligible(),
        result.failures().toString());
    if (!result.eligible())
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "APPLICANT_INELIGIBLE",
          String.join(", ", result.failures()));
    snapshot(applicationId, applicantId);
    String trackingNumber = allocateTrackingNumber();
    String statusColumn = writableStatusColumn();
    jdbc.update(
        "UPDATE dbo.job_application SET "
            + statusColumn
            + "='SUBMITTED',tracking_number=?,eligibility_status='ELIGIBLE',submitted_at=SYSUTCDATETIME(),version=version+1 WHERE application_id=? AND applicant_id=? AND status='DRAFT'",
        trackingNumber,
        applicationId,
        applicantId);
    var response =
        jdbc.queryForObject(
            "SELECT application_id,tracking_number,status,eligibility_status,submitted_at FROM dbo.job_application WHERE application_id=? AND applicant_id=?",
            (rs, n) ->
                new ApplicationController.SubmitResponse(
                    rs.getLong("application_id"),
                    rs.getString("tracking_number"),
                    rs.getString("status"),
                    rs.getString("eligibility_status"),
                    rs.getTimestamp("submitted_at").toInstant()),
            applicationId,
            applicantId);
    jdbc.update(
        "INSERT dbo.notification_outbox(user_id,event_type,payload) VALUES(?,'APPLICATION_SUBMITTED',?)",
        userId,
        "{\"applicationId\":" + applicationId + "}");
    return response;
  }

  private List<String> missing(long id) {
    return cv.missing(id).stream().map(CvService.MissingField::key).toList();
  }

  private String writableStatusColumn() {
    Boolean statusIsComputed =
        jdbc.queryForObject(
            """
            SELECT CONVERT(BIT, is_computed)
              FROM sys.columns
             WHERE object_id=OBJECT_ID('dbo.job_application')
               AND name='status'
            """,
            Boolean.class);
    return Boolean.TRUE.equals(statusIsComputed) ? "application_status" : "status";
  }

  private String allocateTrackingNumber() {
    for (int attempt = 0; attempt < 25; attempt++) {
      String candidate = Integer.toString(10_000_000 + TRACKING_RANDOM.nextInt(90_000_000));
      List<String> existing =
          jdbc.queryForList(
              "SELECT tracking_number FROM dbo.job_application WITH(UPDLOCK,HOLDLOCK) WHERE tracking_number=?",
              String.class,
              candidate);
      if (existing.isEmpty()) return candidate;
    }
    throw new IllegalStateException("Could not allocate a unique tracking number.");
  }

  private void snapshot(long app, long applicant) {
    jdbc.update(
        """
        INSERT dbo.application_profile_snapshot(
          application_id,cv_number,full_name,father_name,mother_name,
          date_of_birth,gender,marital_status,nationality,nid_number
        )
        SELECT ?,cv_number,full_name,father_name,mother_name,
               date_of_birth,gender,marital_status,nationality,nid_number
          FROM dbo.applicant_profile
         WHERE applicant_id=?
        """,
        app,
        applicant);
    jdbc.update(
        "INSERT dbo.application_education_snapshot(application_id,qualification_id,qualification_name,subject_id,subject_name,institution_name,result_type,result_value,result_scale,result_grade,passing_year) SELECT ?,e.qualification_id,COALESCE(NULLIF(LTRIM(RTRIM(e.qualification_name)),''),q.name),e.subject_id,COALESCE(NULLIF(LTRIM(RTRIM(e.subject_name)),''),s.name),COALESCE(NULLIF(LTRIM(RTRIM(e.institution_name)),''),i.name),e.result_type,e.result_value,e.result_scale,e.result_grade,e.passing_year FROM dbo.applicant_education e JOIN dbo.qualification q ON q.qualification_id=e.qualification_id LEFT JOIN dbo.subject s ON s.subject_id=e.subject_id LEFT JOIN dbo.institution i ON i.institution_id=e.institution_id WHERE e.applicant_id=?",
        app,
        applicant);
    jdbc.update(
        "INSERT dbo.application_experience_snapshot(application_id,employer_name,designation,start_date,end_date) SELECT ?,employer_name,designation,start_date,end_date FROM dbo.applicant_experience WHERE applicant_id=?",
        app,
        applicant);
    jdbc.update(
        "INSERT dbo.application_document(application_id,document_type,file_id) SELECT ?,document_type,file_id FROM dbo.applicant_document WHERE applicant_id=? AND active=1",
        app,
        applicant);
  }
}
