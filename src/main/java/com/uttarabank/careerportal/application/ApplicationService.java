package com.uttarabank.careerportal.application;

import com.uttarabank.careerportal.applicant.ApplicantService;
import com.uttarabank.careerportal.common.ApiException;
import com.uttarabank.careerportal.eligibility.EligibilityService;
import java.sql.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
  private final JdbcTemplate jdbc;
  private final ApplicantService applicants;
  private final EligibilityService eligibility;

  public ApplicationService(
      JdbcTemplate jdbc, ApplicantService applicants, EligibilityService eligibility) {
    this.jdbc = jdbc;
    this.applicants = applicants;
    this.eligibility = eligibility;
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
            "SELECT qualification_id,subject_id,institution_name,result_type,result_value,result_scale,result_grade,passing_year FROM dbo.application_education_snapshot WHERE application_id=? ORDER BY passing_year DESC",
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
    var jobs =
        jdbc.queryForList(
            "SELECT rules_version FROM dbo.job_posting WHERE job_id=? AND status='PUBLISHED' AND SYSUTCDATETIME()>=application_start_at AND SYSUTCDATETIME()<application_end_at",
            jobId);
    if (jobs.isEmpty())
      throw new ApiException(
          HttpStatus.CONFLICT,
          "JOB_NOT_ACCEPTING_APPLICATIONS",
          "Job is not accepting applications.");
    Integer limitExceeded =
        jdbc.queryForObject(
            "SELECT CASE WHEN c.circular_id IS NULL THEN 0 WHEN (SELECT COUNT(*) FROM dbo.job_application a JOIN dbo.job_posting x ON x.job_id=a.job_id WHERE a.applicant_id=? AND x.circular_id=c.circular_id)>=p.max_applications_per_applicant THEN 1 ELSE 0 END FROM dbo.job_posting c LEFT JOIN dbo.circular_application_policy p ON p.circular_id=c.circular_id WHERE c.job_id=?",
            Integer.class,
            applicantId,
            jobId);
    var existing =
        jdbc.queryForList(
            "SELECT application_id,status FROM dbo.job_application WHERE job_id=? AND applicant_id=?",
            jobId,
            applicantId);
    long id;
    if (!existing.isEmpty()) id = ((Number) existing.getFirst().get("application_id")).longValue();
    else {
      if (limitExceeded != null && limitExceeded == 1)
        throw new ApiException(
            HttpStatus.CONFLICT,
            "CIRCULAR_APPLICATION_LIMIT_REACHED",
            "Circular application limit reached.");
      var keys = new GeneratedKeyHolder();
      int version = ((Number) jobs.getFirst().get("rules_version")).intValue();
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
    var response =
        jdbc.queryForObject(
            "EXEC dbo.usp_SubmitJobApplication @ApplicationId=?,@ApplicantId=?",
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
    List<String> m = new ArrayList<>();
    Integer p =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.applicant_profile WHERE applicant_id=? AND date_of_birth IS NOT NULL AND father_name IS NOT NULL AND mother_name IS NOT NULL",
            Integer.class,
            id);
    if (p == 0) m.add("PROFILE");
    for (String type : List.of("PRESENT", "PERMANENT")) {
      Integer n =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM dbo.applicant_address WHERE applicant_id=? AND address_type=?",
              Integer.class,
              id,
              type);
      if (n == 0) m.add(type + "_ADDRESS");
    }
    Integer e =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.applicant_education WHERE applicant_id=?", Integer.class, id);
    if (e == 0) m.add("EDUCATION");
    return m;
  }

  private void snapshot(long app, long applicant) {
    jdbc.update(
        "INSERT dbo.application_profile_snapshot SELECT ?,cv_number,full_name,father_name,mother_name,date_of_birth,gender,marital_status,nationality,nid_number FROM dbo.applicant_profile WHERE applicant_id=?",
        app,
        applicant);
    jdbc.update(
        "INSERT dbo.application_education_snapshot(application_id,qualification_id,subject_id,institution_name,result_type,result_value,result_scale,result_grade,passing_year) SELECT ?,e.qualification_id,e.subject_id,COALESCE(i.name,e.institution_name),e.result_type,e.result_value,e.result_scale,e.result_grade,e.passing_year FROM dbo.applicant_education e LEFT JOIN dbo.institution i ON i.institution_id=e.institution_id WHERE e.applicant_id=?",
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
