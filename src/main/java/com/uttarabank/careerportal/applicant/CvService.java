package com.uttarabank.careerportal.applicant;

import com.uttarabank.careerportal.common.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CvService {
  private final JdbcTemplate jdbc;
  private final ApplicantService applicants;

  public CvService(JdbcTemplate jdbc, ApplicantService applicants) {
    this.jdbc = jdbc;
    this.applicants = applicants;
  }

  public Map<String, Object> cv() {
    long id = applicants.applicantId();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("profile", applicants.profile());
    result.put(
        "addresses",
        jdbc.queryForList(
            """
            SELECT a.*,v.name division_name,d.name district_name,u.name upazila_name
            FROM dbo.applicant_address a
            LEFT JOIN dbo.division v ON v.division_id=a.division_id
            LEFT JOIN dbo.district d ON d.district_id=a.district_id
            LEFT JOIN dbo.upazila u ON u.upazila_id=a.upazila_id
            WHERE a.applicant_id=? ORDER BY a.address_type DESC
            """,
            id));
    result.put(
        "educations",
        jdbc.queryForList(
            """
            SELECT e.*,q.name qualification_name,s.name subject_name,
                   COALESCE(i.name,e.institution_name) institution_display_name
            FROM dbo.applicant_education e
            JOIN dbo.qualification q ON q.qualification_id=e.qualification_id
            LEFT JOIN dbo.subject s ON s.subject_id=e.subject_id
            LEFT JOIN dbo.institution i ON i.institution_id=e.institution_id
            WHERE e.applicant_id=? ORDER BY e.passing_year DESC
            """,
            id));
    result.put(
        "experiences",
        jdbc.queryForList(
            "SELECT * FROM dbo.applicant_experience WHERE applicant_id=? ORDER BY start_date DESC",
            id));
    result.put(
        "trainings",
        jdbc.queryForList(
            "SELECT * FROM dbo.applicant_training WHERE applicant_id=? ORDER BY training_id DESC",
            id));
    result.put(
        "languages",
        jdbc.queryForList(
            "SELECT * FROM dbo.applicant_language WHERE applicant_id=? ORDER BY language_name",
            id));
    result.put(
        "activities",
        jdbc.queryForList(
            "SELECT * FROM dbo.applicant_extracurricular_activity WHERE applicant_id=? ORDER BY activity_id DESC",
            id));
    result.put(
        "references",
        jdbc.queryForList(
            "SELECT * FROM dbo.applicant_reference WHERE applicant_id=? ORDER BY reference_id",
            id));
    result.put(
        "documents",
        jdbc.queryForList(
            """
            SELECT d.document_type,f.file_id,f.original_name,f.width,f.height
            FROM dbo.applicant_document d
            JOIN dbo.file_asset f ON f.file_id=d.file_id
            WHERE d.applicant_id=? AND d.active=1
            ORDER BY d.document_type
            """,
            id));
    List<MissingField> missing = missing(id);
    result.put("complete", missing.isEmpty());
    result.put("missingFields", missing);
    return result;
  }

  public List<MissingField> missing(long id) {
    Map<String, Object> profile =
        jdbc.queryForMap(
            """
            SELECT p.*,COALESCE(p.email,u.email) resolved_email,
                   COALESCE(p.mobile,u.mobile) resolved_mobile
            FROM dbo.applicant_profile p
            JOIN dbo.user_account u ON u.user_id=p.user_id
            WHERE p.applicant_id=?
            """,
            id);
    List<MissingField> missing = new ArrayList<>();
    require(missing, profile, "full_name", "Full name", "Personal information", "/portal/profile/personal");
    require(missing, profile, "father_name", "Father's name", "Personal information", "/portal/profile/personal");
    require(missing, profile, "mother_name", "Mother's name", "Personal information", "/portal/profile/personal");
    require(missing, profile, "date_of_birth", "Date of birth", "Personal information", "/portal/profile/personal");
    require(missing, profile, "gender", "Gender", "Personal information", "/portal/profile/personal");
    require(missing, profile, "marital_status", "Marital status", "Personal information", "/portal/profile/personal");
    require(missing, profile, "nationality", "Nationality", "Personal information", "/portal/profile/personal");
    require(missing, profile, "nid_number", "National ID number", "Personal information", "/portal/profile/personal");
    require(missing, profile, "resolved_email", "Email", "Personal information", "/portal/profile/personal");
    require(missing, profile, "resolved_mobile", "Mobile number", "Personal information", "/portal/profile/personal");

    for (String type : List.of("PRESENT", "PERMANENT")) {
      Integer count =
          jdbc.queryForObject(
              """
              SELECT COUNT(*) FROM dbo.applicant_address
              WHERE applicant_id=? AND address_type=? AND address_line IS NOT NULL
                AND division_id IS NOT NULL AND district_id IS NOT NULL AND upazila_id IS NOT NULL
              """,
              Integer.class,
              id,
              type);
      if (count == null || count == 0)
        missing.add(
            new MissingField(
                type + "_ADDRESS",
                type.equals("PRESENT") ? "Present address" : "Permanent address",
                "Addresses",
                "/portal/profile/addresses"));
    }
    addCountRequirement(
        missing,
        id,
        "applicant_education",
        "EDUCATION",
        "At least one education record",
        "Education",
        "/portal/profile/education");
    for (String type : List.of("PHOTO", "SIGNATURE")) {
      Integer count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM dbo.applicant_document WHERE applicant_id=? AND document_type=? AND active=1",
              Integer.class,
              id,
              type);
      if (count == null || count == 0)
        missing.add(
            new MissingField(
                type,
                type.equals("PHOTO") ? "Profile photo" : "Signature",
                "Photo and signature",
                "/portal/profile/documents"));
    }
    return missing;
  }

  public void requireComplete(long id) {
    List<MissingField> missing = missing(id);
    if (!missing.isEmpty())
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "CV_INCOMPLETE",
          "Complete your CV before applying. Missing: "
              + String.join(", ", missing.stream().map(MissingField::label).toList()));
  }

  private void require(
      List<MissingField> missing,
      Map<String, Object> row,
      String key,
      String label,
      String section,
      String url) {
    Object value = row.get(key);
    if (value == null || (value instanceof String text && text.isBlank()))
      missing.add(new MissingField(key.toUpperCase(Locale.ROOT), label, section, url));
  }

  private void addCountRequirement(
      List<MissingField> missing,
      long applicantId,
      String table,
      String key,
      String label,
      String section,
      String url) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo." + table + " WHERE applicant_id=?",
            Integer.class,
            applicantId);
    if (count == null || count == 0) missing.add(new MissingField(key, label, section, url));
  }

  public record MissingField(String key, String label, String section, String url) {}
}
