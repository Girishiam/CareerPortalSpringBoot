package com.uttarabank.careerportal.job;

import com.uttarabank.careerportal.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.cache.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class JobCircularService {
  private static final long MAX_BYTES = 5L * 1024L * 1024L;
  private final JdbcTemplate jdbc;

  public JobCircularService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  @CacheEvict(cacheNames = "publicJobs", allEntries = true)
  public Map<String, Object> save(long jobId, MultipartFile upload) {
    Integer job =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.job_posting WHERE job_id=?", Integer.class, jobId);
    if (job == null || job == 0)
      throw new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found.");
    try {
      byte[] bytes = upload.getBytes();
      if (bytes.length == 0 || bytes.length > MAX_BYTES)
        throw bad("INVALID_FILE_SIZE", "Circular PDF must not exceed 5 MB.");
      if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
        throw bad("INVALID_FILE_TYPE", "Circular letter must be a valid PDF file.");
      String name = safeName(upload.getOriginalFilename());
      jdbc.update("DELETE dbo.job_circular_pdf WHERE job_id=?", jobId);
      jdbc.update(
          "INSERT dbo.job_circular_pdf(job_id,original_name,media_type,size_bytes,file_content) VALUES(?,?,'application/pdf',?,?)",
          jobId,
          name,
          bytes.length,
          bytes);
      jdbc.update("UPDATE dbo.job_posting SET circular_letter_name=? WHERE job_id=?", name, jobId);
      return Map.of("jobId", jobId, "originalName", name, "sizeBytes", bytes.length);
    } catch (ApiException exception) {
      throw exception;
    } catch (Exception exception) {
      throw bad("FILE_PROCESSING_FAILED", "The circular PDF could not be processed.");
    }
  }

  public CircularFile content(long jobId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT original_name,media_type,file_content FROM dbo.job_circular_pdf WHERE job_id=?",
            jobId);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "CIRCULAR_NOT_FOUND", "Circular PDF not found.");
    Map<String, Object> row = rows.getFirst();
    return new CircularFile(
        row.get("original_name").toString(),
        row.get("media_type").toString(),
        (byte[]) row.get("file_content"));
  }

  private String safeName(String raw) {
    String name = raw == null ? "job-circular.pdf" : raw.replaceAll("[\\\\/\\r\\n]", "");
    return name.length() > 260 ? name.substring(name.length() - 260) : name;
  }

  private ApiException bad(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  public record CircularFile(String originalName, String mediaType, byte[] bytes) {}
}
