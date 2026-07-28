package com.uttarabank.careerportal.applicant;

import com.uttarabank.careerportal.common.ApiException;
import com.uttarabank.careerportal.security.CurrentUser;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantService {
  private final JdbcTemplate jdbc;

  public ApplicantService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long applicantId() {
    var ids =
        jdbc.queryForList(
            "SELECT applicant_id FROM dbo.applicant_profile WHERE user_id=?",
            Long.class,
            CurrentUser.get().userId());
    if (ids.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "APPLICANT_NOT_FOUND", "Applicant not found.");
    return ids.getFirst();
  }

  public Map<String, Object> profile() {
    return jdbc.queryForMap(
        """
          SELECT p.applicant_id,p.cv_number,p.full_name,p.father_name,p.mother_name,p.date_of_birth,p.gender,p.marital_status,p.nationality,p.nid_number,u.email,u.mobile
          FROM dbo.applicant_profile p JOIN dbo.user_account u ON u.user_id=p.user_id WHERE p.applicant_id=?
          """,
        applicantId());
  }

  @Transactional
  public Map<String, Object> updateProfile(ApplicantController.ProfileRequest r) {
    String name = required(r.fullName(), "fullName");
    jdbc.update(
        """
          UPDATE p SET full_name=?,father_name=?,mother_name=?,date_of_birth=?,gender=?,marital_status=?,nationality=?,nid_number=?,
          updated_at=SYSUTCDATETIME(),version=version+1
          FROM dbo.applicant_profile p WHERE applicant_id=?
          """,
        name,
        trim(r.fatherName()),
        trim(r.motherName()),
        r.dateOfBirth(),
        r.gender(),
        r.maritalStatus(),
        trim(r.nationality()),
        trim(r.nidNumber()),
        applicantId());
    jdbc.update(
        "UPDATE dbo.user_account SET email=?,mobile=?,version=version+1 WHERE user_id=?",
        lower(r.email()),
        trim(r.mobile()),
        CurrentUser.get().userId());
    return profile();
  }

  @Transactional
  public Map<String, Object> putAddress(String rawType, ApplicantController.AddressRequest r) {
    String type = rawType.toUpperCase(Locale.ROOT);
    if (!Set.of("PRESENT", "PERMANENT").contains(type))
      throw bad("INVALID_ADDRESS_TYPE", "Address type must be PRESENT or PERMANENT.");
    Integer valid =
        jdbc.queryForObject(
            """
          SELECT COUNT(*) FROM dbo.district d JOIN dbo.upazila u ON u.district_id=d.district_id
          WHERE d.district_id=? AND d.division_id=? AND u.upazila_id=?
          """,
            Integer.class,
            r.districtId(),
            r.divisionId(),
            r.upazilaId());
    if (valid == null || valid == 0)
      throw bad(
          "INVALID_ADDRESS_HIERARCHY",
          "District or upazila does not belong to its selected parent.");
    jdbc.update(
        """
          MERGE dbo.applicant_address AS t USING (SELECT ? applicant_id,? address_type) s
          ON t.applicant_id=s.applicant_id AND t.address_type=s.address_type
          WHEN MATCHED THEN UPDATE SET address_line=?,division_id=?,district_id=?,upazila_id=?,postcode=?
          WHEN NOT MATCHED THEN INSERT(applicant_id,address_type,address_line,division_id,district_id,upazila_id,postcode)
          VALUES(s.applicant_id,s.address_type,?,?,?,?,?);
          """,
        applicantId(),
        type,
        required(r.addressLine(), "addressLine"),
        r.divisionId(),
        r.districtId(),
        r.upazilaId(),
        trim(r.postcode()),
        required(r.addressLine(), "addressLine"),
        r.divisionId(),
        r.districtId(),
        r.upazilaId(),
        trim(r.postcode()));
    return jdbc.queryForMap(
        "SELECT * FROM dbo.applicant_address WHERE applicant_id=? AND address_type=?",
        applicantId(),
        type);
  }

  public List<Map<String, Object>> addresses() {
    return jdbc.queryForList(
        "SELECT * FROM dbo.applicant_address WHERE applicant_id=? ORDER BY address_type DESC",
        applicantId());
  }

  @Transactional
  public Map<String, Object> createEducation(ApplicantController.EducationRequest r) {
    validate(r);
    long applicantId = applicantId();
    lockApplicant(applicantId);
    List<Long> existing =
        jdbc.queryForList(
            """
            SELECT education_id
            FROM dbo.applicant_education
            WHERE applicant_id=?
              AND qualification_id=?
              AND ISNULL(subject_id,-1)=ISNULL(?,-1)
              AND ISNULL(institution_id,-1)=ISNULL(?,-1)
              AND ISNULL(institution_name,'')=ISNULL(?,'')
              AND result_type=?
              AND ISNULL(result_value,-1)=ISNULL(?,-1)
              AND ISNULL(result_scale,-1)=ISNULL(?,-1)
              AND ISNULL(result_grade,'')=ISNULL(?,'')
              AND passing_year=?
            """,
            Long.class,
            applicantId,
            r.qualificationId(),
            r.subjectId(),
            r.institutionId(),
            trim(r.institutionName()),
            r.resultType().strip().toUpperCase(Locale.ROOT),
            r.resultValue(),
            r.resultScale(),
            trim(r.resultGrade()),
            r.passingYear());
    if (!existing.isEmpty()) return education(existing.getFirst());
    long id = insertEducation(0, r);
    return education(id);
  }

  public List<Map<String, Object>> educations() {
    return jdbc.queryForList(
        "SELECT * FROM dbo.applicant_education WHERE applicant_id=? ORDER BY is_highest_degree DESC,passing_year DESC",
        applicantId());
  }

  @Transactional
  public Map<String, Object> updateEducation(long id, ApplicantController.EducationRequest r) {
    validate(r);
    owned("applicant_education", "education_id", id);
    jdbc.update(
        "UPDATE dbo.applicant_education SET qualification_id=?,subject_id=?,institution_id=?,institution_name=?,result_type=?, result_value=?,result_scale=?,result_grade=?,passing_year=?,is_highest_degree=? WHERE education_id=? AND applicant_id=?",
        r.qualificationId(),
        r.subjectId(),
        r.institutionId(),
        trim(r.institutionName()),
        r.resultType().toUpperCase(),
        r.resultValue(),
        r.resultScale(),
        trim(r.resultGrade()),
        r.passingYear(),
        r.isHighestDegree(),
        id,
        applicantId());
    return education(id);
  }

  @Transactional
  public void deleteEducation(long id) {
    owned("applicant_education", "education_id", id);
    jdbc.update(
        "DELETE dbo.applicant_education WHERE education_id=? AND applicant_id=?",
        id,
        applicantId());
  }

  @Transactional
  public Map<String, Object> createExperience(ApplicantController.ExperienceRequest r) {
    validate(r);
    long applicantId = applicantId();
    lockApplicant(applicantId);
    List<Long> existing =
        jdbc.queryForList(
            """
            SELECT experience_id
            FROM dbo.applicant_experience
            WHERE applicant_id=?
              AND employer_name=?
              AND designation=?
              AND start_date=?
              AND (end_date=? OR (end_date IS NULL AND ? IS NULL))
              AND is_current=?
            """,
            Long.class,
            applicantId,
            required(r.employerName(), "employerName"),
            required(r.designation(), "designation"),
            r.startDate(),
            r.endDate(),
            r.endDate(),
            r.isCurrent());
    if (!existing.isEmpty()) return experience(existing.getFirst());
    long id = insertExperience(r);
    return experience(id);
  }

  public List<Map<String, Object>> experiences() {
    return jdbc.queryForList(
        "SELECT * FROM dbo.applicant_experience WHERE applicant_id=? ORDER BY start_date DESC",
        applicantId());
  }

  @Transactional
  public Map<String, Object> updateExperience(long id, ApplicantController.ExperienceRequest r) {
    validate(r);
    owned("applicant_experience", "experience_id", id);
    jdbc.update(
        "UPDATE dbo.applicant_experience SET employer_name=?,designation=?,start_date=?,end_date=?,is_current=? WHERE experience_id=? AND applicant_id=?",
        required(r.employerName(), "employerName"),
        required(r.designation(), "designation"),
        r.startDate(),
        r.endDate(),
        r.isCurrent(),
        id,
        applicantId());
    return experience(id);
  }

  @Transactional
  public void deleteExperience(long id) {
    owned("applicant_experience", "experience_id", id);
    jdbc.update(
        "DELETE dbo.applicant_experience WHERE experience_id=? AND applicant_id=?",
        id,
        applicantId());
  }

  private void validate(ApplicantController.EducationRequest r) {
    if (r.passingYear() > Year.now(ZoneOffset.UTC).getValue())
      throw bad("INVALID_PASSING_YEAR", "Passing year cannot be in the future.");
    String type = r.resultType().strip().toUpperCase();
    if (Set.of("GPA", "CGPA").contains(type)
        && (r.resultValue() == null
            || r.resultScale() == null
            || r.resultValue().compareTo(r.resultScale()) > 0))
      throw bad("INVALID_ACADEMIC_RESULT", "GPA/CGPA requires a scale and cannot exceed it.");
    if (Set.of("DIVISION", "CLASS").contains(type)
        && (r.resultGrade() == null || r.resultGrade().isBlank()))
      throw bad("INVALID_ACADEMIC_RESULT", "Division or class is required.");
  }

  private void validate(ApplicantController.ExperienceRequest r) {
    required(r.employerName(), "employerName");
    required(r.designation(), "designation");
    if (r.endDate() != null && r.endDate().isBefore(r.startDate()))
      throw bad("INVALID_EXPERIENCE_DATES", "End date cannot precede start date.");
    if (r.isCurrent() && r.endDate() != null)
      throw bad("INVALID_EXPERIENCE_DATES", "Current employment cannot have an end date.");
  }

  private long insertEducation(long ignored, ApplicantController.EducationRequest r) {
    var k = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT dbo.applicant_education(applicant_id,qualification_id,subject_id,institution_id,institution_name,result_type,result_value,result_scale,result_grade,passing_year,is_highest_degree) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
          Object[] a = {
            applicantId(),
            r.qualificationId(),
            r.subjectId(),
            r.institutionId(),
            trim(r.institutionName()),
            r.resultType().toUpperCase(),
            r.resultValue(),
            r.resultScale(),
            trim(r.resultGrade()),
            r.passingYear(),
            r.isHighestDegree()
          };
          for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
          return p;
        },
        k);
    return Objects.requireNonNull(k.getKey()).longValue();
  }

  private long insertExperience(ApplicantController.ExperienceRequest r) {
    var k = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT dbo.applicant_experience(applicant_id,employer_name,designation,start_date,end_date,is_current) VALUES(?,?,?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
          Object[] a = {
            applicantId(),
            required(r.employerName(), "employerName"),
            required(r.designation(), "designation"),
            r.startDate(),
            r.endDate(),
            r.isCurrent()
          };
          for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
          return p;
        },
        k);
    return Objects.requireNonNull(k.getKey()).longValue();
  }

  private Map<String, Object> education(long id) {
    return jdbc.queryForMap(
        "SELECT * FROM dbo.applicant_education WHERE education_id=? AND applicant_id=?",
        id,
        applicantId());
  }

  private Map<String, Object> experience(long id) {
    return jdbc.queryForMap(
        "SELECT * FROM dbo.applicant_experience WHERE experience_id=? AND applicant_id=?",
        id,
        applicantId());
  }

  private void owned(String table, String column, long id) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo." + table + " WHERE " + column + "=? AND applicant_id=?",
            Integer.class,
            id,
            applicantId());
    if (n == null || n == 0)
      throw new ApiException(HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND", "Record not found.");
  }

  private void lockApplicant(long applicantId) {
    jdbc.queryForObject(
        "SELECT applicant_id FROM dbo.applicant_profile WITH(UPDLOCK,HOLDLOCK) WHERE applicant_id=?",
        Long.class,
        applicantId);
  }

  private ApiException bad(String code, String msg) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, msg);
  }

  private String required(String s, String field) {
    if (s == null || s.strip().isBlank()) throw bad("VALIDATION_FAILED", field + " is required.");
    return s.strip();
  }

  private String trim(String s) {
    return s == null ? null : s.strip();
  }

  private String lower(String s) {
    return s == null ? null : s.strip().toLowerCase(Locale.ROOT);
  }
}
