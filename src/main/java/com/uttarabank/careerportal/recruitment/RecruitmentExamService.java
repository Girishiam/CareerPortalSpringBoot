package com.uttarabank.careerportal.recruitment;

import com.uttarabank.careerportal.common.ApiException;
import com.uttarabank.careerportal.security.CurrentUser;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruitmentExamService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final JdbcTemplate jdbc;

  public RecruitmentExamService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> exams(Long jobId) {
    return jdbc.queryForList(
        """
        SELECT event.exam_event_id,event.job_id,event.exam_type,event.title,
               event.exam_start_at,event.exam_end_at,event.reporting_at,event.status,
               event.generated_at,event.published_at,job.job_code,job.job_title,
               COUNT(DISTINCT candidate.exam_candidate_id) candidate_count,
               COUNT(DISTINCT center.center_id) center_count
          FROM dbo.recruitment_exam event
          JOIN dbo.job_posting job ON job.job_id=event.job_id
          LEFT JOIN dbo.recruitment_exam_candidate candidate ON candidate.exam_event_id=event.exam_event_id
          LEFT JOIN dbo.recruitment_exam_center center ON center.exam_event_id=event.exam_event_id
         WHERE (? IS NULL OR event.job_id=?)
         GROUP BY event.exam_event_id,event.job_id,event.exam_type,event.title,
                  event.exam_start_at,event.exam_end_at,event.reporting_at,event.status,
                  event.generated_at,event.published_at,job.job_code,job.job_title
         ORDER BY event.exam_start_at DESC,event.exam_event_id DESC
        """,
        jobId,
        jobId);
  }

  @Transactional
  public Map<String, Object> create(RecruitmentExamController.ExamRequest request, long userId) {
    if (!request.examEndAt().isAfter(request.examStartAt()))
      throw bad("INVALID_EXAM_WINDOW", "Exam end must be after exam start.");
    if (request.reportingAt() != null && request.reportingAt().isAfter(request.examStartAt()))
      throw bad("INVALID_REPORTING_TIME", "Reporting time cannot be after exam start.");
    requireJob(request.jobId());
    Long stageId =
        jdbc.query(
            "SELECT TOP 1 stage_id FROM dbo.recruitment_stage WHERE job_id=? AND active=1 AND stage_type=? ORDER BY stage_order,stage_id",
            rs -> rs.next() ? rs.getLong(1) : null,
            request.jobId(),
            request.examType());
    long id =
        insert(
            """
            INSERT dbo.recruitment_exam(job_id,exam_type,title,exam_start_at,exam_end_at,
                                  reporting_at,instructions,created_by,stage_id)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,
            request.jobId(),
            request.examType(),
            request.title().strip(),
            Timestamp.from(request.examStartAt()),
            Timestamp.from(request.examEndAt()),
            request.reportingAt() == null ? null : Timestamp.from(request.reportingAt()),
            clean(request.instructions()),
            userId,
            stageId);
    return exam(id);
  }

  public Map<String, Object> exam(long eventId) {
    Map<String, Object> event = requireEvent(eventId);
    Map<String, Object> result = new LinkedHashMap<>(event);
    result.put(
        "centers",
        jdbc.queryForList(
            """
            SELECT center.center_id,center.center_code,center.center_name,center.address,
                   center.contact_phone,room.room_id,room.room_number,room.floor_name,
                   room.capacity,
                   (SELECT COUNT(*) FROM dbo.recruitment_exam_candidate candidate
                     WHERE candidate.room_id=room.room_id) assigned_count
              FROM dbo.recruitment_exam_center center
              LEFT JOIN dbo.recruitment_exam_room room ON room.center_id=center.center_id
             WHERE center.exam_event_id=?
             ORDER BY center.center_code,room.room_number
            """,
            eventId));
    result.put(
        "candidates",
        jdbc.queryForList(
            """
            SELECT candidate.exam_candidate_id,candidate.application_id,candidate.roll_number,
                   candidate.seat_number,candidate.result_status,
                   candidate.admit_card_generated_at,candidate.admit_card_published_at,
                   candidate.notification_queued_at,
                   application.tracking_number,applicant.cv_number,
                   COALESCE(snapshot.full_name,applicant.full_name) full_name,
                   COALESCE(applicant.email,account.email) email,
                   COALESCE(applicant.mobile,account.mobile) mobile,
                   center.center_name,room.room_number
              FROM dbo.recruitment_exam_candidate candidate
              JOIN dbo.job_application application ON application.application_id=candidate.application_id
              JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id
              JOIN dbo.user_account account ON account.user_id=applicant.user_id
              LEFT JOIN dbo.application_profile_snapshot snapshot
                ON snapshot.application_id=application.application_id
              LEFT JOIN dbo.recruitment_exam_room room ON room.room_id=candidate.room_id
              LEFT JOIN dbo.recruitment_exam_center center ON center.center_id=room.center_id
             WHERE candidate.exam_event_id=?
             ORDER BY candidate.roll_number,candidate.exam_candidate_id
            """,
            eventId));
    return result;
  }

  @Transactional
  public Map<String, Object> selectCandidates(long eventId, List<Long> applicationIds) {
    Map<String, Object> event = requireDraft(eventId);
    long jobId = number(event, "job_id");
    String type = Objects.toString(value(event, "exam_type"));
    int added = 0;
    for (Long applicationId : new LinkedHashSet<>(applicationIds)) {
      Integer valid =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM dbo.job_application WHERE application_id=? AND job_id=? AND status='SUBMITTED'",
              Integer.class,
              applicationId,
              jobId);
      if (valid == null || valid == 0)
        throw bad(
            "INVALID_EXAM_CANDIDATE",
            "Every selected candidate must have a submitted application for this job.");
      Long stageId = nullableNumber(event, "stage_id");
      if (stageId != null && !isShortlisted(stageId, applicationId))
        throw bad(
            "NOT_SHORTLISTED", "Candidate must be shortlisted for this recruitment stage first.");
      if ("WRITTEN".equals(type)
          && hasScreeningEvent(jobId, eventId)
          && !passedScreening(jobId, applicationId))
        throw bad(
            "MCQ_NOT_PASSED",
            "Written-exam candidates must have PASSED an earlier MCQ or combined screening event.");
      if ("VIVA".equals(type) && !passedWritten(jobId, applicationId))
        throw bad(
            "WRITTEN_NOT_PASSED", "Viva candidates must have PASSED the written stage first.");
      added +=
          jdbc.update(
              """
              IF NOT EXISTS (
                SELECT 1 FROM dbo.recruitment_exam_candidate WHERE exam_event_id=? AND application_id=?
              )
              INSERT dbo.recruitment_exam_candidate(exam_event_id,application_id) VALUES (?,?)
              """,
              eventId,
              applicationId,
              eventId,
              applicationId);
    }
    return Map.of("selected", added, "examEventId", eventId);
  }

  private boolean isShortlisted(long stageId, long applicationId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.stage_candidate WHERE stage_id=? AND application_id=? AND decision_status='SHORTLISTED'",
            Integer.class,
            stageId,
            applicationId);
    return count != null && count > 0;
  }

  private boolean passedWritten(long jobId, long applicationId) {
    Integer count =
        jdbc.queryForObject(
            """
      SELECT COUNT(*) FROM dbo.stage_candidate candidate JOIN dbo.recruitment_stage stage ON stage.stage_id=candidate.stage_id
      WHERE stage.job_id=? AND stage.stage_type='WRITTEN' AND candidate.application_id=? AND candidate.result_status='PASSED'
      """,
            Integer.class,
            jobId,
            applicationId);
    return count != null && count > 0;
  }

  @Transactional
  public Map<String, Object> assignRolls(long eventId) {
    requireDraft(eventId);
    List<Long> ids =
        jdbc.queryForList(
            "SELECT exam_candidate_id FROM dbo.recruitment_exam_candidate WHERE exam_event_id=? AND roll_number IS NULL",
            Long.class,
            eventId);
    for (Long id : ids)
      jdbc.update(
          "UPDATE dbo.recruitment_exam_candidate SET roll_number=? WHERE exam_candidate_id=?",
          nextRoll(),
          id);
    return Map.of("assigned", ids.size(), "examEventId", eventId);
  }

  @Transactional
  public Map<String, Object> result(long eventId, long candidateId, String status) {
    int updated =
        jdbc.update(
            "UPDATE dbo.recruitment_exam_candidate SET result_status=? WHERE exam_event_id=? AND exam_candidate_id=?",
            status,
            eventId,
            candidateId);
    if (updated == 0) throw notFound("Candidate was not found in this exam.");
    return Map.of("examCandidateId", candidateId, "resultStatus", status);
  }

  @Transactional
  public Map<String, Object> addCenter(
      long eventId, RecruitmentExamController.CenterRequest request) {
    requireDraft(eventId);
    long id =
        insert(
            "INSERT dbo.recruitment_exam_center(exam_event_id,center_code,center_name,address,contact_phone) VALUES (?,?,?,?,?)",
            eventId,
            request.centerCode().strip().toUpperCase(Locale.ROOT),
            request.centerName().strip(),
            request.address().strip(),
            clean(request.contactPhone()));
    return Map.of("centerId", id);
  }

  @Transactional
  public Map<String, Object> addRoom(
      long eventId, long centerId, RecruitmentExamController.RoomRequest request) {
    requireDraft(eventId);
    Integer center =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.recruitment_exam_center WHERE center_id=? AND exam_event_id=?",
            Integer.class,
            centerId,
            eventId);
    if (center == null || center == 0) throw notFound("Exam center was not found.");
    long id =
        insert(
            "INSERT dbo.recruitment_exam_room(center_id,room_number,floor_name,capacity) VALUES (?,?,?,?)",
            centerId,
            request.roomNumber().strip(),
            clean(request.floorName()),
            request.capacity());
    return Map.of("roomId", id);
  }

  @Transactional
  public Map<String, Object> autoAssignSeats(long eventId) {
    requireDraft(eventId);
    List<Map<String, Object>> rooms =
        jdbc.queryForList(
            """
            SELECT room.room_id,room.capacity
              FROM dbo.recruitment_exam_room room JOIN dbo.recruitment_exam_center center ON center.center_id=room.center_id
             WHERE center.exam_event_id=? ORDER BY center.center_code,room.room_number
            """,
            eventId);
    List<Long> candidates =
        jdbc.queryForList(
            "SELECT exam_candidate_id FROM dbo.recruitment_exam_candidate WHERE exam_event_id=? ORDER BY roll_number,exam_candidate_id",
            Long.class,
            eventId);
    int capacity =
        rooms.stream().mapToInt(row -> ((Number) value(row, "capacity")).intValue()).sum();
    if (capacity < candidates.size())
      throw bad("INSUFFICIENT_SEATS", "Room capacity is lower than the selected candidate count.");
    jdbc.update(
        "UPDATE dbo.recruitment_exam_candidate SET room_id=NULL,seat_number=NULL WHERE exam_event_id=?",
        eventId);
    int candidateIndex = 0;
    for (Map<String, Object> room : rooms) {
      long roomId = number(room, "room_id");
      int roomCapacity = ((Number) value(room, "capacity")).intValue();
      for (int seat = 1; seat <= roomCapacity && candidateIndex < candidates.size(); seat++)
        jdbc.update(
            "UPDATE dbo.recruitment_exam_candidate SET room_id=?,seat_number=? WHERE exam_candidate_id=?",
            roomId,
            seat,
            candidates.get(candidateIndex++));
    }
    return Map.of("assigned", candidateIndex, "capacity", capacity);
  }

  @Transactional
  public Map<String, Object> generate(long eventId) {
    requireDraft(eventId);
    Integer incomplete =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.recruitment_exam_candidate
             WHERE exam_event_id=? AND (roll_number IS NULL OR room_id IS NULL OR seat_number IS NULL)
            """,
            Integer.class,
            eventId);
    Integer candidates =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.recruitment_exam_candidate WHERE exam_event_id=?",
            Integer.class,
            eventId);
    if (candidates == null || candidates == 0)
      throw bad("NO_EXAM_CANDIDATES", "Select candidates before generating admit cards.");
    if (incomplete != null && incomplete > 0)
      throw bad(
          "INCOMPLETE_SEAT_PLAN", "Assign every roll number, room and seat before generation.");
    jdbc.update(
        "UPDATE dbo.recruitment_exam_candidate SET admit_card_generated_at=SYSUTCDATETIME() WHERE exam_event_id=?",
        eventId);
    jdbc.update(
        "UPDATE dbo.recruitment_exam SET status='GENERATED',generated_at=SYSUTCDATETIME(),version=version+1 WHERE exam_event_id=?",
        eventId);
    return Map.of("generated", candidates, "examEventId", eventId);
  }

  @Transactional
  public Map<String, Object> publish(long eventId) {
    Map<String, Object> event = requireEvent(eventId);
    if (!"GENERATED".equals(Objects.toString(value(event, "status"))))
      throw bad("ADMIT_CARDS_NOT_GENERATED", "Generate admit cards before publishing them.");
    List<Map<String, Object>> candidates =
        jdbc.queryForList(
            """
            SELECT candidate.exam_candidate_id,applicant.user_id
              FROM dbo.recruitment_exam_candidate candidate
              JOIN dbo.job_application application ON application.application_id=candidate.application_id
              JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id
             WHERE candidate.exam_event_id=?
            """,
            eventId);
    for (Map<String, Object> candidate : candidates) {
      long candidateId = number(candidate, "exam_candidate_id");
      long userId = number(candidate, "user_id");
      jdbc.update(
          "INSERT dbo.notification_outbox(user_id,event_type,payload,status) VALUES (?,'ADMIT_CARD_PUBLISHED',?,'PENDING')",
          userId,
          "{\"examEventId\":" + eventId + ",\"examCandidateId\":" + candidateId + "}");
    }
    jdbc.update(
        """
        UPDATE dbo.recruitment_exam_candidate
           SET admit_card_published_at=SYSUTCDATETIME(),notification_queued_at=SYSUTCDATETIME()
         WHERE exam_event_id=?
        """,
        eventId);
    jdbc.update(
        "UPDATE dbo.recruitment_exam SET status='PUBLISHED',published_at=SYSUTCDATETIME(),version=version+1 WHERE exam_event_id=?",
        eventId);
    return Map.of("published", candidates.size(), "notificationsQueued", candidates.size());
  }

  public List<Map<String, Object>> myCards() {
    return jdbc.queryForList(
        """
        SELECT candidate.exam_candidate_id,candidate.roll_number,candidate.seat_number,
               candidate.admit_card_published_at,event.exam_event_id,event.exam_type,event.title,
               event.exam_start_at,event.exam_end_at,event.reporting_at,
               job.job_code,job.job_title,center.center_name,room.room_number
          FROM dbo.recruitment_exam_candidate candidate
          JOIN dbo.recruitment_exam event ON event.exam_event_id=candidate.exam_event_id
          JOIN dbo.job_application application ON application.application_id=candidate.application_id
          JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id
          JOIN dbo.job_posting job ON job.job_id=event.job_id
          JOIN dbo.recruitment_exam_room room ON room.room_id=candidate.room_id
          JOIN dbo.recruitment_exam_center center ON center.center_id=room.center_id
         WHERE applicant.user_id=? AND event.status='PUBLISHED'
         ORDER BY event.exam_start_at DESC
        """,
        CurrentUser.get().userId());
  }

  public List<Map<String, Object>> adminCards(Long jobId) {
    return jdbc.queryForList(
        """
        SELECT candidate.exam_candidate_id,candidate.roll_number,candidate.seat_number,
               candidate.admit_card_generated_at,candidate.admit_card_published_at,
               event.exam_event_id,event.exam_type,event.title,event.exam_start_at,event.status,
               job.job_id,job.job_code,job.job_title,application.tracking_number,
               applicant.cv_number,COALESCE(snapshot.full_name,applicant.full_name) full_name,
               center.center_name,room.room_number
          FROM dbo.recruitment_exam_candidate candidate
          JOIN dbo.recruitment_exam event ON event.exam_event_id=candidate.exam_event_id
          JOIN dbo.job_posting job ON job.job_id=event.job_id
          JOIN dbo.job_application application ON application.application_id=candidate.application_id
          JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id
          LEFT JOIN dbo.application_profile_snapshot snapshot
            ON snapshot.application_id=application.application_id
          LEFT JOIN dbo.recruitment_exam_room room ON room.room_id=candidate.room_id
          LEFT JOIN dbo.recruitment_exam_center center ON center.center_id=room.center_id
         WHERE candidate.admit_card_generated_at IS NOT NULL
           AND (? IS NULL OR event.job_id=?)
         ORDER BY job.job_code,event.exam_start_at DESC,candidate.roll_number
        """,
        jobId,
        jobId);
  }

  public Map<String, Object> adminCard(long candidateId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            admitCardSql(
                "candidate.exam_candidate_id=? AND candidate.admit_card_generated_at IS NOT NULL"),
            candidateId);
    if (rows.isEmpty()) throw notFound("Generated admit card was not found.");
    return rows.getFirst();
  }

  public long adminCardApplicationId(long candidateId) {
    List<Long> rows =
        jdbc.queryForList(
            """
            SELECT application_id
              FROM dbo.recruitment_exam_candidate
             WHERE exam_candidate_id=? AND admit_card_generated_at IS NOT NULL
            """,
            Long.class,
            candidateId);
    if (rows.isEmpty()) throw notFound("Generated admit card was not found.");
    return rows.getFirst();
  }

  public Map<String, Object> myCard(long candidateId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            admitCardSql(
                "candidate.exam_candidate_id=? AND applicant.user_id=? AND event.status='PUBLISHED'"),
            candidateId,
            CurrentUser.get().userId());
    if (rows.isEmpty()) throw notFound("Published admit card was not found.");
    return rows.getFirst();
  }

  private String admitCardSql(String whereClause) {
    return """
        SELECT candidate.exam_candidate_id,candidate.roll_number,candidate.seat_number,
               event.exam_event_id,event.exam_type,event.title,event.exam_start_at,
               event.exam_end_at,event.reporting_at,event.instructions,
               job.job_code,job.job_title,application.tracking_number,applicant.cv_number,
               COALESCE(snapshot.full_name,applicant.full_name) full_name,
               COALESCE(snapshot.father_name,applicant.father_name) father_name,
               COALESCE(snapshot.mother_name,applicant.mother_name) mother_name,
               center.center_code,center.center_name,center.address center_address,
               center.contact_phone,room.room_number,room.floor_name
          FROM dbo.recruitment_exam_candidate candidate
          JOIN dbo.recruitment_exam event ON event.exam_event_id=candidate.exam_event_id
          JOIN dbo.job_application application ON application.application_id=candidate.application_id
          JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id
          JOIN dbo.job_posting job ON job.job_id=event.job_id
          JOIN dbo.recruitment_exam_room room ON room.room_id=candidate.room_id
          JOIN dbo.recruitment_exam_center center ON center.center_id=room.center_id
          LEFT JOIN dbo.application_profile_snapshot snapshot
            ON snapshot.application_id=application.application_id
         WHERE
        """
        + whereClause;
  }

  public long myCardApplicationId(long candidateId) {
    List<Long> rows =
        jdbc.queryForList(
            """
            SELECT candidate.application_id
              FROM dbo.recruitment_exam_candidate candidate
              JOIN dbo.recruitment_exam event ON event.exam_event_id=candidate.exam_event_id
              JOIN dbo.job_application application ON application.application_id=candidate.application_id
              JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id
             WHERE candidate.exam_candidate_id=? AND applicant.user_id=? AND event.status='PUBLISHED'
            """,
            Long.class,
            candidateId,
            CurrentUser.get().userId());
    if (rows.isEmpty()) throw notFound("Published admit card was not found.");
    return rows.getFirst();
  }

  private boolean hasScreeningEvent(long jobId, long eventId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.recruitment_exam WHERE job_id=? AND exam_event_id<>? AND exam_type IN ('MCQ','COMBINED')",
            Integer.class,
            jobId,
            eventId);
    return count != null && count > 0;
  }

  private boolean passedScreening(long jobId, long applicationId) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM dbo.recruitment_exam_candidate candidate
            JOIN dbo.recruitment_exam event ON event.exam_event_id=candidate.exam_event_id
            WHERE event.job_id=? AND event.exam_type IN ('MCQ','COMBINED')
              AND candidate.application_id=? AND candidate.result_status='PASSED'
            """,
            Integer.class,
            jobId,
            applicationId);
    return count != null && count > 0;
  }

  private String nextRoll() {
    for (int attempt = 0; attempt < 100; attempt++) {
      String roll = Integer.toString(100000 + RANDOM.nextInt(900000));
      Integer exists =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM dbo.recruitment_exam_candidate WHERE roll_number=?",
              Integer.class,
              roll);
      if (exists != null && exists == 0) return roll;
    }
    throw new ApiException(
        HttpStatus.CONFLICT,
        "ROLL_NUMBER_EXHAUSTED",
        "A unique roll number could not be assigned.");
  }

  private Map<String, Object> requireDraft(long eventId) {
    Map<String, Object> event = requireEvent(eventId);
    if (!"DRAFT".equals(Objects.toString(value(event, "status"))))
      throw bad("EXAM_LOCKED", "Generated or published exams can no longer be changed.");
    return event;
  }

  private Map<String, Object> requireEvent(long eventId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT event.*,job.job_code,job.job_title
              FROM dbo.recruitment_exam event JOIN dbo.job_posting job ON job.job_id=event.job_id
             WHERE event.exam_event_id=?
            """,
            eventId);
    if (rows.isEmpty()) throw notFound("Exam event was not found.");
    return rows.getFirst();
  }

  private void requireJob(long jobId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.job_posting WHERE job_id=?", Integer.class, jobId);
    if (count == null || count == 0) throw notFound("Job was not found.");
  }

  private long insert(String sql, Object... args) {
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int index = 0; index < args.length; index++)
            statement.setObject(index + 1, args[index]);
          return statement;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  private static Object value(Map<String, Object> row, String key) {
    Object result = row.get(key);
    return result != null ? result : row.get(key.toUpperCase(Locale.ROOT));
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) Objects.requireNonNull(value(row, key))).longValue();
  }

  private static Long nullableNumber(Map<String, Object> row, String key) {
    Object result = value(row, key);
    return result instanceof Number number ? number.longValue() : null;
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static ApiException bad(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  private static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }
}
