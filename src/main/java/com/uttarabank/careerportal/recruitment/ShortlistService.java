package com.uttarabank.careerportal.recruitment;

import com.uttarabank.careerportal.common.ApiException;
import java.io.*;
import java.sql.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ShortlistService {
  private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);
  private final JdbcTemplate jdbc;
  private final AdminDashboardService applications;

  public ShortlistService(JdbcTemplate jdbc, AdminDashboardService applications) {
    this.jdbc = jdbc;
    this.applications = applications;
  }

  @Transactional
  public List<Map<String, Object>> stages(long jobId) {
    requireJob(jobId);
    defaultStage(jobId, "MCQ", "MCQ / Preliminary", 10, false);
    defaultStage(jobId, "WRITTEN", "Written Examination", 20, true);
    defaultStage(jobId, "VIVA", "Viva / Interview", 30, true);
    return jdbc.queryForList(
        """
      SELECT stage.*,
        (SELECT COUNT(*) FROM dbo.stage_candidate candidate WHERE candidate.stage_id=stage.stage_id AND candidate.decision_status='SHORTLISTED') shortlisted_count,
        (SELECT COUNT(*) FROM dbo.stage_candidate candidate WHERE candidate.stage_id=stage.stage_id AND candidate.result_status='PASSED') passed_count
      FROM dbo.recruitment_stage stage WHERE stage.job_id=? AND stage.active=1
      ORDER BY stage.stage_order,stage.stage_id
      """,
        jobId);
  }

  @Transactional
  public Map<String, Object> createStage(ShortlistController.StageRequest request, long userId) {
    requireJob(request.jobId());
    long id =
        insert(
            """
      INSERT dbo.recruitment_stage(job_id,stage_code,stage_name,stage_type,stage_order,status,
        candidate_label,requires_previous_pass,notification_event_type,active)
      VALUES(?,?,?,?,?,'DRAFT',?,?,'CANDIDATE_SHORTLISTED',1)
      """,
            request.jobId(),
            request.stageCode(),
            request.stageName().strip(),
            request.stageType(),
            request.stageOrder(),
            clean(request.candidateLabel()),
            request.requiresPreviousPass());
    return Map.of("stageId", id, "createdBy", userId);
  }

  public Map<String, Object> candidates(long stageId, String q, int page, int size) {
    Map<String, Object> stage = stage(stageId);
    long jobId = num(stage, "job_id");
    String search = clean(q);
    List<Object> args = new ArrayList<>(List.of(stageId, jobId));
    String filter = "";
    if (search != null) {
      filter =
          " AND (application.tracking_number LIKE ? OR applicant.cv_number LIKE ? OR COALESCE(snapshot.full_name,applicant.full_name) LIKE ? OR COALESCE(applicant.mobile,account.mobile) LIKE ?)";
      for (int i = 0; i < 4; i++) args.add("%" + search + "%");
    }
    List<Object> countArgs = new ArrayList<>(args);
    Long total =
        jdbc.queryForObject(
            """
      SELECT COUNT(*) FROM dbo.job_application application
      JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id
      JOIN dbo.user_account account ON account.user_id=applicant.user_id
      LEFT JOIN dbo.application_profile_snapshot snapshot ON snapshot.application_id=application.application_id
      LEFT JOIN dbo.stage_candidate selected ON selected.stage_id=? AND selected.application_id=application.application_id
      WHERE application.job_id=? AND application.status='SUBMITTED'
      """
                + filter,
            Long.class,
            countArgs.toArray());
    args.add((long) page * size);
    args.add(size);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
      SELECT application.application_id,application.tracking_number,application.eligibility_status,
        applicant.cv_number,COALESCE(snapshot.full_name,applicant.full_name) full_name,
        COALESCE(applicant.email,account.email) email,COALESCE(applicant.mobile,account.mobile) mobile,
        selected.decision_status,selected.result_status,selected.selection_source,selected.remarks,
        CASE WHEN selected.stage_candidate_id IS NULL THEN 0 ELSE 1 END selected
      FROM dbo.job_application application
      JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id
      JOIN dbo.user_account account ON account.user_id=applicant.user_id
      LEFT JOIN dbo.application_profile_snapshot snapshot ON snapshot.application_id=application.application_id
      LEFT JOIN dbo.stage_candidate selected ON selected.stage_id=? AND selected.application_id=application.application_id
      WHERE application.job_id=? AND application.status='SUBMITTED'
      """
                + filter
                + " ORDER BY application.submitted_at,application.application_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            args.toArray());
    return Map.of(
        "stage",
        stage,
        "content",
        rows,
        "page",
        page,
        "size",
        size,
        "totalElements",
        Objects.requireNonNullElse(total, 0L),
        "totalPages",
        (int) Math.ceil(Objects.requireNonNullElse(total, 0L) / (double) size));
  }

  @Transactional
  public Map<String, Object> select(
      long stageId, List<Long> ids, String remarks, boolean notify, String source, long userId) {
    Map<String, Object> stage = stage(stageId);
    long jobId = num(stage, "job_id");
    int selected = 0, notified = 0;
    String safeRemarks = remarks(remarks);
    for (Long id : new LinkedHashSet<>(ids)) {
      validateEligible(stage, id, jobId);
      selected +=
          jdbc.update(
              """
        MERGE dbo.stage_candidate AS target
        USING (SELECT ? stage_id,? application_id) source
        ON target.stage_id=source.stage_id AND target.application_id=source.application_id
        WHEN MATCHED THEN UPDATE SET decision_status='SHORTLISTED',selection_source=?,remarks=?,selected_by=?,selected_at=SYSUTCDATETIME()
        WHEN NOT MATCHED THEN INSERT(stage_id,application_id,decision_status,result_status,selection_source,remarks,selected_by)
          VALUES(source.stage_id,source.application_id,'SHORTLISTED','PENDING',?,?,?);
        """,
              stageId,
              id,
              source,
              safeRemarks,
              userId,
              source,
              safeRemarks,
              userId);
      if (notify) {
        queueNotification(
            id,
            stageId,
            Objects.toString(val(stage, "notification_event_type"), "CANDIDATE_SHORTLISTED"));
        notified++;
      }
    }
    if (notify)
      jdbc.update(
          "UPDATE dbo.stage_candidate SET notified_at=SYSUTCDATETIME() WHERE stage_id=? AND application_id IN (SELECT value FROM STRING_SPLIT(?,','))",
          stageId,
          ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
    return Map.of("selected", selected, "notificationsQueued", notified, "stageId", stageId);
  }

  public Map<String, Object> remove(long stageId, long applicationId) {
    int n =
        jdbc.update(
            "DELETE dbo.stage_candidate WHERE stage_id=? AND application_id=?",
            stageId,
            applicationId);
    return Map.of("removed", n);
  }

  @Transactional
  public Map<String, Object> result(
      long stageId, long applicationId, String status, String remarks, boolean notify) {
    int n =
        jdbc.update(
            "UPDATE dbo.stage_candidate SET result_status=?,remarks=COALESCE(?,remarks) WHERE stage_id=? AND application_id=?",
            status,
            remarks(remarks),
            stageId,
            applicationId);
    if (n == 0) throw bad("Candidate is not shortlisted for this stage.");
    if (notify) queueNotification(applicationId, stageId, "RECRUITMENT_STAGE_RESULT");
    return Map.of(
        "applicationId", applicationId, "resultStatus", status, "notificationQueued", notify);
  }

  public byte[] exportTemplate(long stageId) {
    Map<String, Object> stage = stage(stageId);
    List<Map<String, Object>> rows =
        applications.applicationExportRows(
            num(stage, "job_id"), null, null, null, null, null, null, null, null);
    Map<Long, Map<String, Object>> flags = new HashMap<>();
    for (Map<String, Object> flag :
        jdbc.queryForList(
            "SELECT application_id,decision_status,remarks FROM dbo.stage_candidate WHERE stage_id=?",
            stageId)) flags.put(num(flag, "application_id"), flag);
    for (Map<String, Object> row : rows) {
      Map<String, Object> flag = flags.get(num(row, "application_id"));
      row.put(
          "selected",
          flag != null && "SHORTLISTED".equalsIgnoreCase(txt(flag, "decision_status"))
              ? "YES"
              : "NO");
      row.put("remarks", flag == null ? "" : txt(flag, "remarks"));
    }
    return ApplicationXlsxWriter.writeStage(
        rows, "Stage Applicants - " + txt(stage, "job_code") + " - " + txt(stage, "stage_name"));
  }

  public String exportFilename(long stageId) {
    Map<String, Object> stage = stage(stageId);
    return "shortlist-"
        + safe(txt(stage, "job_code"))
        + "-"
        + safe(txt(stage, "stage_code"))
        + ".xlsx";
  }

  @Transactional
  public Map<String, Object> importFile(
      long stageId, MultipartFile file, boolean notify, long userId) {
    if (file.isEmpty() || file.getSize() > 10_000_000)
      throw bad("Choose an XLSX file smaller than 10 MB.");
    int total = 0, selected = 0, removed = 0, errors = 0;
    List<String> messages = new ArrayList<>();
    try (Workbook book = WorkbookFactory.create(file.getInputStream())) {
      Sheet sheet = book.getSheetAt(0);
      ImportColumns columns = importColumns(sheet);
      for (int i = columns.headerRow() + 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null || rowIsEmpty(row)) continue;
        total++;
        String choice = cell(row, columns.selected());
        try {
          long applicationId = parseId(row.getCell(columns.applicationId()));
          String rowRemarks = columns.remarks() < 0 ? null : cell(row, columns.remarks());
          if (isSelected(choice)) {
            select(stageId, List.of(applicationId), rowRemarks, notify, "XLSX", userId);
            selected++;
          } else if (isNotSelected(choice)) {
            removed +=
                jdbc.update(
                    "DELETE dbo.stage_candidate WHERE stage_id=? AND application_id=?",
                    stageId,
                    applicationId);
          } else throw bad("Selected must be YES or NO.");
        } catch (Exception e) {
          errors++;
          if (messages.size() < 10) messages.add("Row " + (i + 1) + ": " + rowError(e));
        }
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw bad("The uploaded XLSX could not be read: " + rowError(e));
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalRows", total);
    result.put("selectedRows", selected);
    result.put("removedRows", removed);
    result.put("errorRows", errors);
    result.put("errors", messages);
    return result;
  }

  private void validateEligible(Map<String, Object> stage, long applicationId, long jobId) {
    Integer valid =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.job_application WHERE application_id=? AND job_id=? AND status='SUBMITTED' AND eligibility_status='ELIGIBLE'",
            Integer.class,
            applicationId,
            jobId);
    if (valid == null || valid == 0)
      throw bad("Application is not an eligible submitted application for this job.");
    if (Boolean.TRUE.equals(val(stage, "requires_previous_pass"))
        || Objects.equals(val(stage, "requires_previous_pass"), 1)) {
      Integer passed =
          jdbc.queryForObject(
              """
      SELECT COUNT(*) FROM dbo.stage_candidate candidate JOIN dbo.recruitment_stage previous ON previous.stage_id=candidate.stage_id
      WHERE previous.job_id=? AND previous.active=1 AND previous.stage_order=(SELECT MAX(stage_order) FROM dbo.recruitment_stage WHERE job_id=? AND active=1 AND stage_order<?)
        AND candidate.application_id=? AND candidate.result_status='PASSED'
      """,
              Integer.class,
              jobId,
              jobId,
              num(stage, "stage_order"),
              applicationId);
      if (passed == null || passed == 0)
        throw bad("Candidate must pass the immediately preceding stage first.");
    }
  }

  private void queueNotification(long applicationId, long stageId, String type) {
    jdbc.update(
        """
    INSERT dbo.notification_outbox(user_id,event_type,payload,status)
    SELECT applicant.user_id,?,CONCAT('{"stageId":',?,',"applicationId":',?, '}'),'PENDING'
    FROM dbo.job_application application JOIN dbo.applicant_profile applicant ON applicant.applicant_id=application.applicant_id WHERE application.application_id=?
    """,
        type,
        stageId,
        applicationId,
        applicationId);
  }

  private void defaultStage(long jobId, String code, String name, int order, boolean previous) {
    jdbc.update(
        "IF NOT EXISTS(SELECT 1 FROM dbo.recruitment_stage WHERE job_id=? AND stage_code=?) INSERT dbo.recruitment_stage(job_id,stage_code,stage_name,stage_type,stage_order,status,candidate_label,requires_previous_pass,notification_event_type,active) VALUES (?,?,?,?,?,'DRAFT',?,?,'CANDIDATE_SHORTLISTED',1)",
        jobId,
        code,
        jobId,
        code,
        name,
        code,
        order,
        name,
        previous);
  }

  private Map<String, Object> stage(long id) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT stage.*,job.job_code,job.job_title FROM dbo.recruitment_stage stage JOIN dbo.job_posting job ON job.job_id=stage.job_id WHERE stage.stage_id=? AND stage.active=1",
            id);
    if (rows.isEmpty()) throw bad("Recruitment stage was not found.");
    return rows.getFirst();
  }

  private void requireJob(long id) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.job_posting WHERE job_id=?", Integer.class, id);
    if (n == null || n == 0) throw bad("Job was not found.");
  }

  private long insert(String sql, Object... args) {
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
          return s;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  private static String cell(Row row, int index) {
    Cell c = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    return c == null ? "" : FORMATTER.formatCellValue(c).strip();
  }

  private static ImportColumns importColumns(Sheet sheet) {
    for (int rowIndex = sheet.getFirstRowNum();
        rowIndex <= Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 10);
        rowIndex++) {
      Row row = sheet.getRow(rowIndex);
      if (row == null) continue;
      int applicationId = -1, selected = -1, remarks = -1;
      for (Cell cell : row) {
        String header = FORMATTER.formatCellValue(cell).strip();
        if (header.equalsIgnoreCase("Application ID (do not edit)")
            || header.equalsIgnoreCase("Application ID")) applicationId = cell.getColumnIndex();
        else if (header.equalsIgnoreCase("Selected (YES/NO)")
            || header.equalsIgnoreCase("Selected")) selected = cell.getColumnIndex();
        else if (header.equalsIgnoreCase("Remarks")) remarks = cell.getColumnIndex();
      }
      if (applicationId >= 0 && selected >= 0)
        return new ImportColumns(rowIndex, applicationId, selected, remarks);
    }
    throw bad(
        "The workbook must contain Application ID and Selected (YES/NO) columns. Export the stage applicants file first, edit it, and upload that file.");
  }

  private static boolean rowIsEmpty(Row row) {
    for (Cell cell : row) if (!FORMATTER.formatCellValue(cell).isBlank()) return false;
    return true;
  }

  private static long parseId(Cell cell) {
    if (cell == null) throw bad("Application ID is required.");
    if (cell.getCellType() == CellType.NUMERIC) return (long) cell.getNumericCellValue();
    String value = FORMATTER.formatCellValue(cell).strip();
    if (!value.matches("[0-9]+")) throw bad("Application ID must be numeric.");
    return Long.parseLong(value);
  }

  private static String txt(Map<String, Object> row, String key) {
    Object v = val(row, key);
    return v == null ? "" : v.toString();
  }

  private static Object val(Map<String, Object> row, String key) {
    Object v = row.get(key);
    return v == null ? row.get(key.toUpperCase(Locale.ROOT)) : v;
  }

  private static long num(Map<String, Object> row, String key) {
    return ((Number) Objects.requireNonNull(val(row, key))).longValue();
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String remarks(String value) {
    String cleaned = clean(value);
    if (cleaned != null && cleaned.length() > 1000)
      throw bad("Remarks cannot exceed 1000 characters.");
    return cleaned;
  }

  private static boolean isSelected(String value) {
    return value.equalsIgnoreCase("YES")
        || value.equalsIgnoreCase("Y")
        || value.equals("1")
        || value.equalsIgnoreCase("SHORTLISTED");
  }

  private static boolean isNotSelected(String value) {
    return value.equalsIgnoreCase("NO")
        || value.equalsIgnoreCase("N")
        || value.equals("0")
        || value.isBlank();
  }

  private static String limit(String value, int maximum) {
    return value == null || value.length() <= maximum ? value : value.substring(0, maximum);
  }

  private static String rowError(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
    String message = clean(cause.getMessage());
    if (message == null) message = "The row could not be imported.";
    message = message.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ");
    return limit(message, 500);
  }

  private static String safe(String value) {
    String safe =
        value == null
            ? "export"
            : value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    return safe.isBlank() ? "export" : safe;
  }

  private static ApiException bad(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "SHORTLIST_ERROR", message);
  }

  private record ImportColumns(int headerRow, int applicationId, int selected, int remarks) {}
}
