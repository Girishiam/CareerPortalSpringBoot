package com.uttarabank.careerportal.demo;

import com.uttarabank.careerportal.common.ApiException;
import jakarta.annotation.PreDestroy;
import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DemoAdmitCardService {
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
  private final JdbcTemplate jdbc;
  private final ExecutorService coordinator = Executors.newSingleThreadExecutor();
  private final Set<Long> running = ConcurrentHashMap.newKeySet();
  private final Path outputRoot = Paths.get("data", "demo-admit-cards").toAbsolutePath().normalize();

  public DemoAdmitCardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    // Demo jobs are local to this JVM; recover rows left active by an application restart.
    try {
      jdbc.update("UPDATE demo.admit_card SET generation_status='PENDING' WHERE generation_status='PROCESSING'");
      jdbc.update("UPDATE demo.admit_card_batch SET batch_status='PENDING',completed_at=NULL WHERE batch_status='RUNNING'");
    } catch (RuntimeException ignored) {
      // The optional demo schema may not exist in every environment.
    }
  }

  public List<Map<String, Object>> batches() {
    return jdbc.queryForList("SELECT * FROM demo.admit_card_batch ORDER BY batch_id DESC");
  }

  public Map<String, Object> cards(long batchId, String tracking, String roll, String name,
      String jobCode, String status, int page, int size) {
    requireBatch(batchId);
    String where = " WHERE batch_id=?";
    List<Object> args = new ArrayList<>();
    args.add(batchId);
    if (has(tracking)) { where += " AND tracking_number LIKE ?"; args.add(tracking.strip() + "%"); }
    if (has(roll)) { where += " AND roll_number LIKE ?"; args.add(roll.strip() + "%"); }
    if (has(name)) { where += " AND applicant_name LIKE ?"; args.add("%" + name.strip() + "%"); }
    if (has(jobCode)) { where += " AND job_code=?"; args.add(jobCode.strip()); }
    if (has(status)) { where += " AND generation_status=?"; args.add(status.strip().toUpperCase(Locale.ROOT)); }
    Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM demo.admit_card" + where, Integer.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add((long) page * size);
    pageArgs.add(size);
    List<Map<String, Object>> content = jdbc.queryForList(
        "SELECT demo_card_id,batch_id,applicant_name,roll_number,tracking_number,cv_number,job_code,job_title,exam_type,center_name,room_number,seat_number,generation_status,pdf_size_bytes,generation_ms,generated_at,error_message FROM demo.admit_card"
            + where + " ORDER BY demo_card_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        pageArgs.toArray());
    int count = total == null ? 0 : total;
    return Map.of("content", content, "page", page, "size", size, "totalElements", count,
        "totalPages", count == 0 ? 0 : (count + size - 1) / size);
  }

  public Map<String, Object> progress(long batchId) {
    requireBatch(batchId);
    Map<String, Object> row = jdbc.queryForMap("""
        SELECT batch.batch_id,batch.batch_name,batch.batch_status,batch.total_records,
               batch.worker_count,batch.started_at,batch.completed_at,
               SUM(CASE WHEN card.generation_status='COMPLETED' THEN 1 ELSE 0 END) completed,
               SUM(CASE WHEN card.generation_status='FAILED' THEN 1 ELSE 0 END) failed,
               SUM(CASE WHEN card.generation_status='PROCESSING' THEN 1 ELSE 0 END) processing,
               SUM(CASE WHEN card.generation_status='PENDING' THEN 1 ELSE 0 END) pending
          FROM demo.admit_card_batch batch
          LEFT JOIN demo.admit_card card ON card.batch_id=batch.batch_id
         WHERE batch.batch_id=?
         GROUP BY batch.batch_id,batch.batch_name,batch.batch_status,batch.total_records,
                  batch.worker_count,batch.started_at,batch.completed_at
        """, batchId);
    long completed = number(row, "completed");
    long failed = number(row, "failed");
    long total = number(row, "total_records");
    Instant started = instant(row.get("started_at"));
    long elapsed = started == null ? 0 : Math.max(0, Duration.between(started, Instant.now()).toSeconds());
    double rate = elapsed == 0 ? 0 : completed / (double) elapsed;
    long remaining = rate == 0 ? 0 : Math.round(number(row, "pending") / rate);
    Map<String, Object> result = new LinkedHashMap<>(row);
    result.put("percent", total == 0 ? 0 : (completed + failed) * 100.0 / total);
    result.put("elapsedSeconds", elapsed);
    result.put("estimatedRemainingSeconds", remaining);
    result.put("pdfsPerSecond", rate);
    result.put("running", running.contains(batchId));
    return result;
  }

  public Map<String, Object> start(long batchId) {
    Map<String, Object> batch = requireBatch(batchId);
    if (!running.add(batchId)) throw conflict("This batch is already generating PDFs.");
    // Keep database connections available for admin searches and progress polling while PDFs run.
    int workers = Math.max(1, Math.min(2, ((Number) batch.get("worker_count")).intValue()));
    jdbc.update("UPDATE demo.admit_card SET generation_status='PENDING' WHERE batch_id=? AND generation_status='PROCESSING'", batchId);
    jdbc.update("UPDATE demo.admit_card_batch SET batch_status='RUNNING',started_at=COALESCE(started_at,SYSUTCDATETIME()),completed_at=NULL WHERE batch_id=?", batchId);
    coordinator.submit(() -> runBatch(batchId, workers));
    return Map.of("batchId", batchId, "status", "RUNNING", "workers", workers);
  }

  public Map<String, Object> reset(long batchId) {
    requireBatch(batchId);
    if (running.contains(batchId)) throw conflict("Stop waiting for the active generation run before resetting this batch.");
    Path directory = outputRoot.resolve("batch-" + batchId).normalize();
    if (!directory.startsWith(outputRoot) || directory.equals(outputRoot))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEMO_PATH", "Invalid demo batch directory.");
    try {
      if (Files.exists(directory)) {
        try (var paths = Files.walk(directory)) {
          for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
      }
    } catch (IOException e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DEMO_RESET_FAILED", "Generated demo PDFs could not be deleted.");
    }
    int reset = jdbc.update("""
        UPDATE demo.admit_card
           SET generation_status='PENDING',pdf_path=NULL,pdf_size_bytes=NULL,
               generation_ms=NULL,error_message=NULL,generated_at=NULL
         WHERE batch_id=?
        """, batchId);
    jdbc.update("""
        UPDATE demo.admit_card_batch
           SET batch_status='PENDING',completed_records=0,failed_records=0,
               started_at=NULL,completed_at=NULL
         WHERE batch_id=?
        """, batchId);
    return Map.of("batchId", batchId, "resetRecords", reset, "status", "PENDING");
  }

  public Map<String, Object> generateOne(long cardId) {
    Map<String, Object> card = requireCard(cardId);
    generate(card);
    return requireCard(cardId);
  }

  public PdfFile pdf(long cardId) {
    Map<String, Object> card = requireCard(cardId);
    if (!"COMPLETED".equals(Objects.toString(card.get("generation_status")))) generate(card);
    card = requireCard(cardId);
    Path path = safeOutput(Objects.toString(card.get("pdf_path")));
    try {
      return new PdfFile(Files.readAllBytes(path), "admit-card-" + card.get("roll_number") + ".pdf");
    } catch (IOException e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DEMO_PDF_READ_FAILED", "Generated PDF could not be read.");
    }
  }

  private void runBatch(long batchId, int workerCount) {
    ExecutorService workers = Executors.newFixedThreadPool(workerCount);
    try {
      List<Long> ids = jdbc.queryForList("SELECT demo_card_id FROM demo.admit_card WHERE batch_id=? AND generation_status IN ('PENDING','FAILED') ORDER BY demo_card_id", Long.class, batchId);
      AtomicInteger next = new AtomicInteger();
      List<Future<?>> futures = new ArrayList<>();
      for (int worker = 0; worker < workerCount; worker++) futures.add(workers.submit(() -> {
        for (int index; (index = next.getAndIncrement()) < ids.size();) {
          long id = ids.get(index);
          try { generate(requireCard(id)); }
          catch (RuntimeException ignored) { /* error is persisted per card */ }
        }
      }));
      for (Future<?> future : futures) future.get();
      Integer failed = jdbc.queryForObject("SELECT COUNT(*) FROM demo.admit_card WHERE batch_id=? AND generation_status='FAILED'", Integer.class, batchId);
      jdbc.update("UPDATE demo.admit_card_batch SET batch_status=?,completed_at=SYSUTCDATETIME() WHERE batch_id=?", failed != null && failed > 0 ? "FAILED" : "COMPLETED", batchId);
    } catch (Exception e) {
      jdbc.update("UPDATE demo.admit_card_batch SET batch_status='FAILED',completed_at=SYSUTCDATETIME() WHERE batch_id=?", batchId);
    } finally {
      workers.shutdown();
      running.remove(batchId);
    }
  }

  private void generate(Map<String, Object> card) {
    long id = number(card, "demo_card_id");
    long batchId = number(card, "batch_id");
    jdbc.update("UPDATE demo.admit_card SET generation_status='PROCESSING',error_message=NULL WHERE demo_card_id=?", id);
    long started = System.nanoTime();
    Path directory = outputRoot.resolve("batch-" + batchId).normalize();
    Path output = directory.resolve("admit-card-" + card.get("roll_number") + ".pdf").normalize();
    if (!output.startsWith(outputRoot)) throw new IllegalStateException("Invalid PDF output path");
    try {
      Files.createDirectories(directory);
      writePdf(card, output);
      long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      jdbc.update("UPDATE demo.admit_card SET generation_status='COMPLETED',pdf_path=?,pdf_size_bytes=?,generation_ms=?,generated_at=SYSUTCDATETIME(),error_message=NULL WHERE demo_card_id=?",
          output.toString(), Files.size(output), millis, id);
    } catch (Exception e) {
      jdbc.update("UPDATE demo.admit_card SET generation_status='FAILED',error_message=? WHERE demo_card_id=?", abbreviate(e.getMessage()), id);
      throw new IllegalStateException(e);
    }
  }

  private void writePdf(Map<String, Object> card, Path output) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      try (PDPageContentStream canvas = new PDPageContentStream(document, page)) {
        canvas.setStrokingColor(new Color(0, 98, 49));
        canvas.setLineWidth(1.5f);
        canvas.addRect(28, 28, 539, 786);
        canvas.stroke();
        drawImage(document, canvas, Paths.get("src/main/resources/static/images/logo/uttara-bank-logo.png"), 48, 748, 150, 46);
        text(canvas, bold, 22, 260, 775, "ADMIT CARD");
        text(canvas, bold, 10, 280, 755, upper(card, "exam_type") + " EXAMINATION");
        line(canvas, 45, 735, 550, 735);
        text(canvas, bold, 16, 55, 690, string(card, "applicant_name"));
        labelValue(canvas, regular, bold, 55, 650, "ROLL NUMBER", string(card, "roll_number"));
        drawPhotoOrPlaceholder(document, canvas, string(card, "photo_path"), 445, 620, 90, 108, "APPLICANT PHOTO");
        float y = 590;
        y = row(canvas, regular, bold, y, "POSITION", string(card, "job_code") + " - " + string(card, "job_title"), "EXAM", string(card, "exam_title"));
        y = row(canvas, regular, bold, y, "DATE AND TIME", date(card.get("exam_start_at")) + " - " + date(card.get("exam_end_at")), "REPORTING TIME", date(card.get("reporting_at")));
        y = row(canvas, regular, bold, y, "CENTER", string(card, "center_code") + " - " + string(card, "center_name"), "CENTER ADDRESS", string(card, "center_address"));
        y = row(canvas, regular, bold, y, "ROOM", string(card, "room_number") + ", " + string(card, "floor_name"), "SEAT NUMBER", string(card, "seat_number"));
        row(canvas, regular, bold, y, "TRACKING NUMBER", string(card, "tracking_number"), "CV NUMBER", string(card, "cv_number"));
        text(canvas, bold, 11, 48, 330, "Instructions to candidates");
        line(canvas, 48, 323, 547, 323);
        String instructions = string(card, "instructions");
        int item = 1; float instructionY = 302;
        for (String instruction : instructions.split("\\r?\\n")) {
          if (!instruction.isBlank()) text(canvas, regular, 9, 58, instructionY, (item++) + ". " + instruction);
          instructionY -= 18;
        }
        drawPhotoOrPlaceholder(document, canvas, string(card, "signature_path"), 55, 95, 130, 50, "SIGNATURE");
        text(canvas, regular, 8, 72, 82, "Applicant's signature");
        line(canvas, 45, 60, 550, 60);
        text(canvas, regular, 7, 155, 45, "This computer-generated admit card is valid only for the stated examination.");
      }
      document.save(output.toFile());
    }
  }

  private float row(PDPageContentStream c, PDFont regular, PDFont bold, float y, String l1, String v1, String l2, String v2) throws IOException {
    c.setStrokingColor(Color.DARK_GRAY); c.addRect(45, y - 50, 505, 50); c.moveTo(300, y); c.lineTo(300, y - 50); c.stroke();
    text(c, regular, 7, 52, y - 13, l1); text(c, bold, 8, 52, y - 29, shorten(v1, 48));
    text(c, regular, 7, 307, y - 13, l2); text(c, bold, 8, 307, y - 29, shorten(v2, 42));
    return y - 50;
  }

  private void labelValue(PDPageContentStream c, PDFont regular, PDFont bold, float x, float y, String label, String value) throws IOException {
    text(c, regular, 8, x, y, label); text(c, bold, 20, x, y - 25, value);
  }
  private void text(PDPageContentStream c, PDFont font, float size, float x, float y, String value) throws IOException {
    c.beginText(); c.setNonStrokingColor(Color.BLACK); c.setFont(font, size); c.newLineAtOffset(x, y); c.showText(ascii(value)); c.endText();
  }
  private void line(PDPageContentStream c, float x1, float y1, float x2, float y2) throws IOException { c.moveTo(x1, y1); c.lineTo(x2, y2); c.stroke(); }
  private void drawImage(PDDocument d, PDPageContentStream c, Path path, float x, float y, float w, float h) throws IOException {
    if (Files.isRegularFile(path)) c.drawImage(PDImageXObject.createFromByteArray(d, Files.readAllBytes(path), path.getFileName().toString()), x, y, w, h);
  }
  private void drawPhotoOrPlaceholder(PDDocument d, PDPageContentStream c, String raw, float x, float y, float w, float h, String fallback) throws IOException {
    Path path = raw == null || raw.isBlank() ? null : Paths.get(raw).normalize();
    if (path != null && Files.isRegularFile(path)) drawImage(d, c, path, x, y, w, h);
    else { c.setStrokingColor(Color.GRAY); c.addRect(x, y, w, h); c.stroke(); text(c, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 7, x + 8, y + h / 2, fallback); }
  }

  private Map<String, Object> requireBatch(long id) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM demo.admit_card_batch WHERE batch_id=?", id);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "DEMO_BATCH_NOT_FOUND", "Demo batch was not found.");
    return lower(rows.getFirst());
  }
  private Map<String, Object> requireCard(long id) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM demo.admit_card WHERE demo_card_id=?", id);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "DEMO_CARD_NOT_FOUND", "Demo admit card was not found.");
    return lower(rows.getFirst());
  }
  private Map<String, Object> lower(Map<String, Object> source) { Map<String,Object> result=new LinkedHashMap<>(); source.forEach((k,v)->result.put(k.toLowerCase(Locale.ROOT),v)); return result; }
  private Path safeOutput(String raw) { Path path=Paths.get(raw).toAbsolutePath().normalize(); if(!path.startsWith(outputRoot)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DEMO_PATH","Invalid demo PDF path."); return path; }
  private static boolean has(String value) { return value != null && !value.isBlank(); }
  private static long number(Map<String,Object> row,String key) { Object value=row.get(key); if(value==null)value=row.get(key.toUpperCase(Locale.ROOT)); return value instanceof Number n?n.longValue():0; }
  private static String string(Map<String,Object> row,String key) { Object value=row.get(key); return value==null?"":value.toString(); }
  private static String upper(Map<String,Object> row,String key) { return string(row,key).toUpperCase(Locale.ROOT); }
  private static String date(Object value) { Instant instant=instant(value); return instant==null?"-":DATE_TIME.withZone(ZoneId.systemDefault()).format(instant); }
  private static Instant instant(Object value) { if(value instanceof Timestamp t)return t.toInstant(); if(value instanceof java.util.Date d)return d.toInstant(); return null; }
  private static String ascii(String value) { return Objects.toString(value,"").replaceAll("[^\\x20-\\x7E]","-"); }
  private static String shorten(String value,int max) { String clean=ascii(value); return clean.length()<=max?clean:clean.substring(0,max-3)+"..."; }
  private static String abbreviate(String value) { String text=Objects.toString(value,"PDF generation failed."); return text.length()<=1000?text:text.substring(0,1000); }
  private static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT,"DEMO_BATCH_RUNNING",message); }
  @PreDestroy public void close() { coordinator.shutdownNow(); }
  public record PdfFile(byte[] bytes, String filename) {}
}
