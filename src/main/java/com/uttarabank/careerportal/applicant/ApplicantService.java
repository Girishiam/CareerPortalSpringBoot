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
          SELECT p.applicant_id,p.cv_number,p.full_name,p.father_name,p.mother_name,p.date_of_birth,p.gender,p.marital_status,p.nationality,p.nid_number,
                 p.passport_number,COALESCE(p.email,u.email) email,COALESCE(p.mobile,u.mobile) mobile
          FROM dbo.applicant_profile p JOIN dbo.user_account u ON u.user_id=p.user_id WHERE p.applicant_id=?
          """,
        applicantId());
  }

  @Transactional
  public Map<String, Object> updateProfile(ApplicantController.ProfileRequest r) {
    String name = required(r.fullName(), "fullName");
    long applicantId = applicantId();
    String nid = normalizeNid(r.nidNumber());
    String passport = normalizePassport(r.passportNumber());
    String email = lower(r.email());
    String mobile = trim(r.mobile());
    validateIdentityUniqueness(applicantId, nid, passport, email, mobile);
    jdbc.update(
        """
          UPDATE p SET full_name=?,father_name=?,mother_name=?,date_of_birth=?,gender=?,marital_status=?,nationality=?,nid_number=?,
          passport_number=?,email=?,mobile=?,
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
        nid,
        passport,
        email,
        mobile,
        applicantId);
    jdbc.update(
        "UPDATE dbo.user_account SET email=?,mobile=?,version=version+1 WHERE user_id=?",
        email,
        mobile,
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
            ORDER BY education_id
            """,
            Long.class,
            applicantId,
            r.qualificationId());
    if (!existing.isEmpty()) {
      updateEducationRow(existing.getFirst(), r);
      return education(existing.getFirst());
    }
    long id = insertEducation(0, r);
    return education(id);
  }

  public List<Map<String, Object>> educations() {
    return jdbc.queryForList(
        """
        SELECT e.*,COALESCE(NULLIF(LTRIM(RTRIM(e.qualification_name)),''),q.name) qualification_display_name,
               COALESCE(NULLIF(LTRIM(RTRIM(e.subject_name)),''),s.name) subject_display_name,
               COALESCE(NULLIF(LTRIM(RTRIM(e.institution_name)),''),i.name) institution_display_name
        FROM dbo.applicant_education e
        JOIN dbo.qualification q ON q.qualification_id=e.qualification_id
        LEFT JOIN dbo.subject s ON s.subject_id=e.subject_id
        LEFT JOIN dbo.institution i ON i.institution_id=e.institution_id
        WHERE e.applicant_id=?
        ORDER BY e.is_highest_degree DESC,e.passing_year DESC
        """,
        applicantId());
  }

  @Transactional
  public Map<String, Object> updateEducation(long id, ApplicantController.EducationRequest r) {
    validate(r);
    owned("applicant_education", "education_id", id);
    updateEducationRow(id, r);
    return education(id);
  }

  private void updateEducationRow(long id, ApplicantController.EducationRequest r) {
    jdbc.update(
        "UPDATE dbo.applicant_education SET qualification_id=?,qualification_name=?,subject_id=?,subject_name=?,institution_id=?,institution_name=?,result_type=?, result_value=?,result_scale=?,result_grade=?,passing_year=?,is_highest_degree=? WHERE education_id=? AND applicant_id=?",
        r.qualificationId(),
        customName("qualification", r.qualificationId(), r.qualificationName(), "qualification"),
        r.subjectId(),
        customName("subject", r.subjectId(), r.subjectName(), "subject / group"),
        r.institutionId(),
        customName("institution", r.institutionId(), r.institutionName(), "university / board"),
        r.resultType().toUpperCase(),
        r.resultValue(),
        r.resultScale(),
        trim(r.resultGrade()),
        r.passingYear(),
        r.isHighestDegree(),
        id,
        applicantId());
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

  public List<Map<String, Object>> trainings() {
    return jdbc.queryForList(
        "SELECT * FROM dbo.applicant_training WHERE applicant_id=? ORDER BY training_id DESC",
        applicantId());
  }

  @Transactional
  public Map<String, Object> createTraining(ApplicantController.TrainingRequest r) {
    if ((r.trainingTitle() == null || r.trainingTitle().isBlank())
        && (r.trainingSummary() == null || r.trainingSummary().isBlank()))
      throw bad("EMPTY_TRAINING", "Enter a training title or summary.");
    Long id =
        jdbc.queryForObject(
            "INSERT dbo.applicant_training(applicant_id,training_title,training_summary,duration_months) OUTPUT INSERTED.training_id VALUES(?,?,?,?)",
            Long.class,
            applicantId(),
            trim(r.trainingTitle()),
            trim(r.trainingSummary()),
            r.durationMonths());
    return training(Objects.requireNonNull(id));
  }

  @Transactional
  public Map<String, Object> updateTraining(long id, ApplicantController.TrainingRequest r) {
    owned("applicant_training", "training_id", id);
    jdbc.update(
        "UPDATE dbo.applicant_training SET training_title=?,training_summary=?,duration_months=? WHERE training_id=? AND applicant_id=?",
        trim(r.trainingTitle()),
        trim(r.trainingSummary()),
        r.durationMonths(),
        id,
        applicantId());
    return training(id);
  }

  @Transactional
  public void deleteTraining(long id) {
    owned("applicant_training", "training_id", id);
    jdbc.update(
        "DELETE dbo.applicant_training WHERE training_id=? AND applicant_id=?", id, applicantId());
  }

  public List<Map<String, Object>> languages() {
    return jdbc.queryForList(
        "SELECT * FROM dbo.applicant_language WHERE applicant_id=? ORDER BY language_name",
        applicantId());
  }

  @Transactional
  public Map<String, Object> createLanguage(ApplicantController.LanguageRequest r) {
    Long id =
        jdbc.queryForObject(
            "INSERT dbo.applicant_language(applicant_id,language_name,speaking,writing,listening,reading) OUTPUT INSERTED.language_id VALUES(?,?,?,?,?,?)",
            Long.class,
            applicantId(),
            required(r.languageName(), "languageName"),
            upperOrNull(r.speaking()),
            upperOrNull(r.writing()),
            upperOrNull(r.listening()),
            upperOrNull(r.reading()));
    return language(Objects.requireNonNull(id));
  }

  @Transactional
  public Map<String, Object> updateLanguage(long id, ApplicantController.LanguageRequest r) {
    owned("applicant_language", "language_id", id);
    jdbc.update(
        "UPDATE dbo.applicant_language SET language_name=?,speaking=?,writing=?,listening=?,reading=? WHERE language_id=? AND applicant_id=?",
        required(r.languageName(), "languageName"),
        upperOrNull(r.speaking()),
        upperOrNull(r.writing()),
        upperOrNull(r.listening()),
        upperOrNull(r.reading()),
        id,
        applicantId());
    return language(id);
  }

  @Transactional
  public void deleteLanguage(long id) {
    owned("applicant_language", "language_id", id);
    jdbc.update(
        "DELETE dbo.applicant_language WHERE language_id=? AND applicant_id=?", id, applicantId());
  }

  public List<Map<String, Object>> activities() {
    return jdbc.queryForList(
        "SELECT * FROM dbo.applicant_extracurricular_activity WHERE applicant_id=? ORDER BY activity_id DESC",
        applicantId());
  }

  @Transactional
  public Map<String, Object> createActivity(ApplicantController.ActivityRequest r) {
    Long id =
        jdbc.queryForObject(
            "INSERT dbo.applicant_extracurricular_activity(applicant_id,activity_name,organization,role_name,activity_summary,achievement) OUTPUT INSERTED.activity_id VALUES(?,?,?,?,?,?)",
            Long.class,
            applicantId(),
            required(r.activityName(), "activityName"),
            trim(r.organization()),
            trim(r.roleName()),
            trim(r.activitySummary()),
            trim(r.achievement()));
    return activity(Objects.requireNonNull(id));
  }

  @Transactional
  public Map<String, Object> updateActivity(long id, ApplicantController.ActivityRequest r) {
    owned("applicant_extracurricular_activity", "activity_id", id);
    jdbc.update(
        "UPDATE dbo.applicant_extracurricular_activity SET activity_name=?,organization=?,role_name=?,activity_summary=?,achievement=? WHERE activity_id=? AND applicant_id=?",
        required(r.activityName(), "activityName"),
        trim(r.organization()),
        trim(r.roleName()),
        trim(r.activitySummary()),
        trim(r.achievement()),
        id,
        applicantId());
    return activity(id);
  }

  @Transactional
  public void deleteActivity(long id) {
    owned("applicant_extracurricular_activity", "activity_id", id);
    jdbc.update(
        "DELETE dbo.applicant_extracurricular_activity WHERE activity_id=? AND applicant_id=?",
        id,
        applicantId());
  }

  public List<Map<String, Object>> references() {
    return jdbc.queryForList(
        "SELECT * FROM dbo.applicant_reference WHERE applicant_id=? ORDER BY reference_id",
        applicantId());
  }

  @Transactional
  public Map<String, Object> createReference(ApplicantController.ReferenceRequest r) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.applicant_reference WHERE applicant_id=?",
            Integer.class,
            applicantId());
    if (count != null && count >= 2)
      throw bad("REFERENCE_LIMIT_REACHED", "You can add at most two references.");
    Long id =
        jdbc.queryForObject(
            "INSERT dbo.applicant_reference(applicant_id,full_name,organization,designation,relationship,email,mobile) OUTPUT INSERTED.reference_id VALUES(?,?,?,?,?,?,?)",
            Long.class,
            applicantId(),
            required(r.fullName(), "fullName"),
            required(r.organization(), "organization"),
            required(r.designation(), "designation"),
            trim(r.relationship()),
            lower(r.email()),
            trim(r.mobile()));
    return reference(Objects.requireNonNull(id));
  }

  @Transactional
  public Map<String, Object> updateReference(long id, ApplicantController.ReferenceRequest r) {
    owned("applicant_reference", "reference_id", id);
    jdbc.update(
        "UPDATE dbo.applicant_reference SET full_name=?,organization=?,designation=?,relationship=?,email=?,mobile=? WHERE reference_id=? AND applicant_id=?",
        required(r.fullName(), "fullName"),
        required(r.organization(), "organization"),
        required(r.designation(), "designation"),
        trim(r.relationship()),
        lower(r.email()),
        trim(r.mobile()),
        id,
        applicantId());
    return reference(id);
  }

  @Transactional
  public void deleteReference(long id) {
    owned("applicant_reference", "reference_id", id);
    jdbc.update(
        "DELETE dbo.applicant_reference WHERE reference_id=? AND applicant_id=?",
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
    customName("subject", r.subjectId(), r.subjectName(), "subject / group");
    customName("institution", r.institutionId(), r.institutionName(), "university / board");
    customName("qualification", r.qualificationId(), r.qualificationName(), "qualification");
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
                  "INSERT dbo.applicant_education(applicant_id,qualification_id,qualification_name,subject_id,subject_name,institution_id,institution_name,result_type,result_value,result_scale,result_grade,passing_year,is_highest_degree) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
          Object[] a = {
            applicantId(),
            r.qualificationId(),
            customName(
                "qualification", r.qualificationId(), r.qualificationName(), "qualification"),
            r.subjectId(),
            customName("subject", r.subjectId(), r.subjectName(), "subject / group"),
            r.institutionId(),
            customName("institution", r.institutionId(), r.institutionName(), "university / board"),
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

  private Map<String, Object> training(long id) {
    return jdbc.queryForMap(
        "SELECT * FROM dbo.applicant_training WHERE training_id=? AND applicant_id=?",
        id,
        applicantId());
  }

  private Map<String, Object> language(long id) {
    return jdbc.queryForMap(
        "SELECT * FROM dbo.applicant_language WHERE language_id=? AND applicant_id=?",
        id,
        applicantId());
  }

  private Map<String, Object> activity(long id) {
    return jdbc.queryForMap(
        "SELECT * FROM dbo.applicant_extracurricular_activity WHERE activity_id=? AND applicant_id=?",
        id,
        applicantId());
  }

  private Map<String, Object> reference(long id) {
    return jdbc.queryForMap(
        "SELECT * FROM dbo.applicant_reference WHERE reference_id=? AND applicant_id=?",
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

  private String customName(String type, Long id, String customValue, String label) {
    String custom = trim(customValue);
    if (id == null) {
      if (custom != null && !custom.isBlank())
        throw bad(
            "INVALID_EDUCATION_SELECTION", "Select Others before entering a custom " + label + ".");
      return null;
    }
    String sql =
        switch (type) {
          case "subject" -> "SELECT name FROM dbo.subject WHERE subject_id=?";
          case "institution" -> "SELECT name FROM dbo.institution WHERE institution_id=?";
          case "qualification" -> "SELECT name FROM dbo.qualification WHERE qualification_id=?";
          default -> throw new IllegalArgumentException("Unsupported education lookup: " + type);
        };
    List<String> names = jdbc.queryForList(sql, String.class, id);
    if (names.isEmpty()) throw bad("INVALID_EDUCATION_SELECTION", "Select a valid " + label + ".");
    boolean other = names.getFirst() != null && names.getFirst().strip().matches("(?i)others?");
    if (other) return required(custom, label);
    return null;
  }

  private String trim(String s) {
    return s == null ? null : s.strip();
  }

  private String lower(String s) {
    return s == null ? null : s.strip().toLowerCase(Locale.ROOT);
  }

  private String upperOrNull(String s) {
    return s == null || s.isBlank() ? null : s.strip().toUpperCase(Locale.ROOT);
  }

  private String normalizeNid(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.replaceAll("[\\s.-]", "");
    if (!normalized.matches("\\d{10}|\\d{13}|\\d{17}"))
      throw bad("INVALID_NID", "NID must contain 10, 13, or 17 digits.");
    return normalized;
  }

  private String normalizePassport(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z0-9]{6,20}"))
      throw bad("INVALID_PASSPORT", "Passport number must contain 6 to 20 letters or digits.");
    return normalized;
  }

  private void validateIdentityUniqueness(
      long applicantId, String nid, String passport, String email, String mobile) {
    Integer identityConflict =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM dbo.applicant_profile
            WHERE applicant_id<>?
              AND (
                (? IS NOT NULL AND normalized_nid=?)
                OR (? IS NOT NULL AND normalized_passport=?)
              )
            """,
            Integer.class,
            applicantId,
            nid,
            nid,
            passport,
            passport);
    if (identityConflict != null && identityConflict > 0)
      throw new ApiException(
          HttpStatus.CONFLICT,
          "IDENTITY_ALREADY_REGISTERED",
          "This NID or passport number is already registered.");

    Integer contactConflict =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM dbo.user_account
            WHERE user_id<>?
              AND ((? IS NOT NULL AND email=?) OR (? IS NOT NULL AND mobile=?))
            """,
            Integer.class,
            CurrentUser.get().userId(),
            email,
            email,
            mobile,
            mobile);
    if (contactConflict != null && contactConflict > 0)
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CONTACT_ALREADY_REGISTERED",
          "This email or mobile number is already registered.");
  }
}
