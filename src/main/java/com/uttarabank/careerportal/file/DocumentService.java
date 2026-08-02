package com.uttarabank.careerportal.file;

import com.uttarabank.careerportal.applicant.ApplicantService;
import com.uttarabank.careerportal.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
  private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
  private static final long PROFILE_IMAGE_MAX_BYTES = 1024L * 1024L;
  private final JdbcTemplate jdbc;
  private final ApplicantService applicants;
  private final Path root;
  private final long max;
  private final int minW, minH, maxW, maxH;

  public DocumentService(
      JdbcTemplate jdbc,
      ApplicantService applicants,
      @Value("${career-portal.files.root}") Path root,
      @Value("${career-portal.files.max-bytes}") long max,
      @Value("${career-portal.files.photo-min-width}") int minW,
      @Value("${career-portal.files.photo-min-height}") int minH,
      @Value("${career-portal.files.photo-max-width}") int maxW,
      @Value("${career-portal.files.photo-max-height}") int maxH) {
    this.jdbc = jdbc;
    this.applicants = applicants;
    this.root = root.toAbsolutePath().normalize();
    this.max = max;
    this.minW = minW;
    this.minH = minH;
    this.maxW = maxW;
    this.maxH = maxH;
  }

  @Transactional
  public DocumentController.DocumentResponse store(String rawType, MultipartFile upload) {
    String type = rawType.strip().toUpperCase(Locale.ROOT);
    if (!Set.of("PHOTO", "SIGNATURE", "NID", "CERTIFICATE").contains(type))
      throw bad("INVALID_DOCUMENT_TYPE", "Unsupported document type.");
    try {
      byte[] bytes = upload.getBytes();
      if (bytes.length == 0 || bytes.length > max)
        throw bad("INVALID_FILE_SIZE", "File is empty or exceeds the size limit.");
      if (Set.of("PHOTO", "SIGNATURE").contains(type)
          && bytes.length > PROFILE_IMAGE_MAX_BYTES)
        throw bad("INVALID_FILE_SIZE", "Photo and signature files must not exceed 1 MB.");
      String media = detect(bytes);
      Integer width = null, height = null;
      if (Set.of("PHOTO", "SIGNATURE").contains(type)) {
        if (!Set.of("image/jpeg", "image/png").contains(media))
          throw bad("INVALID_FILE_TYPE", "Photo and signature must be JPEG or PNG.");
        BufferedImage image;
        try {
          image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
          throw bad("CORRUPTED_IMAGE", "Image cannot be decoded.");
        }
        if (image == null) throw bad("CORRUPTED_IMAGE", "Image cannot be decoded.");
        width = image.getWidth();
        height = image.getHeight();
        int requiredWidth = 300;
        int requiredHeight = type.equals("PHOTO") ? 300 : 80;
        if (width != requiredWidth || height != requiredHeight)
          throw bad(
              "INVALID_IMAGE_DIMENSIONS",
              type.equals("PHOTO")
                  ? "Photo dimensions must be exactly 300 x 300 pixels."
                  : "Signature dimensions must be exactly 300 x 80 pixels.");
      }
      String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      long applicant = applicants.applicantId();
      jdbc.queryForObject(
          "SELECT applicant_id FROM dbo.applicant_profile WITH(UPDLOCK,HOLDLOCK) WHERE applicant_id=?",
          Long.class,
          applicant);
      List<Map<String, Object>> existing =
          jdbc.queryForList(
              """
              SELECT f.file_id,f.width,f.height,f.validation_status
              FROM dbo.applicant_document d
              JOIN dbo.file_asset f ON f.file_id=d.file_id
              WHERE d.applicant_id=? AND d.document_type=? AND d.active=1 AND f.sha256=?
              """,
              applicant,
              type,
              hash);
      if (!existing.isEmpty()) {
        Map<String, Object> file = existing.getFirst();
        return new DocumentController.DocumentResponse(
            ((Number) file.get("file_id")).longValue(),
            type,
            file.get("validation_status").toString(),
            file.get("width") == null ? null : ((Number) file.get("width")).intValue(),
            file.get("height") == null ? null : ((Number) file.get("height")).intValue());
      }
      String key = hash.substring(0, 2) + "/" + hash;
      Path target = root.resolve(key).normalize();
      if (!target.startsWith(root)) throw new SecurityException("Invalid path");
      Files.createDirectories(target.getParent());
      if (!Files.exists(target)) Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
      List<Long> matchingAssets =
          jdbc.queryForList(
              "SELECT file_id FROM dbo.file_asset WITH(UPDLOCK,HOLDLOCK) WHERE sha256=?",
              Long.class,
              hash);
      long fileId;
      if (!matchingAssets.isEmpty()) {
        fileId = matchingAssets.getFirst();
      } else {
        var keys = new GeneratedKeyHolder();
        Integer w = width, h = height;
        jdbc.update(
            c -> {
              var p =
                  c.prepareStatement(
                      "INSERT dbo.file_asset(storage_key,original_name,media_type,size_bytes,sha256,width,height,validation_status) VALUES(?,?,?,?,?,?,?,'VALID')",
                      Statement.RETURN_GENERATED_KEYS);
              p.setString(1, key);
              p.setString(2, safeName(upload.getOriginalFilename()));
              p.setString(3, media);
              p.setLong(4, bytes.length);
              p.setString(5, hash);
              p.setObject(6, w);
              p.setObject(7, h);
              return p;
            },
            keys);
        fileId = Objects.requireNonNull(keys.getKey()).longValue();
      }
      jdbc.update(
          "UPDATE dbo.applicant_document SET active=0 WHERE applicant_id=? AND document_type=? AND active=1",
          applicant,
          type);
      jdbc.update(
          "INSERT dbo.applicant_document(applicant_id,document_type,file_id) VALUES(?,?,?)",
          applicant,
          type,
          fileId);
      return new DocumentController.DocumentResponse(fileId, type, "VALID", width, height);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("Document upload failed. documentType={} filename={}", rawType,
          upload.getOriginalFilename(), e);
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "FILE_PROCESSING_FAILED",
          "The uploaded file could not be processed.");
    }
  }

  public List<Map<String, Object>> documents() {
    return jdbc.queryForList(
        "SELECT f.file_id,d.document_type,f.original_name,f.media_type,f.size_bytes,f.width,f.height,f.validation_status,f.created_at FROM dbo.applicant_document d JOIN dbo.file_asset f ON f.file_id=d.file_id WHERE d.applicant_id=? AND d.active=1 ORDER BY f.created_at DESC",
        applicants.applicantId());
  }

  public DocumentContent content(String rawType) {
    String type = rawType.strip().toUpperCase(Locale.ROOT);
    if (!Set.of("PHOTO", "SIGNATURE").contains(type))
      throw bad("INVALID_DOCUMENT_TYPE", "Only photo and signature can be displayed.");
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT f.storage_key,f.media_type
            FROM dbo.applicant_document d
            JOIN dbo.file_asset f ON f.file_id=d.file_id
            WHERE d.applicant_id=? AND d.document_type=? AND d.active=1
            """,
            applicants.applicantId(),
            type);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found.");
    try {
      Map<String, Object> row = rows.getFirst();
      Path file = root.resolve(row.get("storage_key").toString()).normalize();
      if (!file.startsWith(root) || !Files.isRegularFile(file))
        throw new IOException("Stored file is unavailable.");
      return new DocumentContent(
          row.get("media_type").toString(), Files.readAllBytes(file));
    } catch (IOException exception) {
      throw new ApiException(
          HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document file is unavailable.");
    }
  }

  public DocumentContent applicationContent(long applicationId, String rawType) {
    String type = rawType.strip().toUpperCase(Locale.ROOT);
    if (!Set.of("PHOTO", "SIGNATURE").contains(type))
      throw bad("INVALID_DOCUMENT_TYPE", "Only photo and signature can be displayed.");
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT file_asset.storage_key,file_asset.media_type
            FROM dbo.application_document document
            JOIN dbo.file_asset file_asset ON file_asset.file_id=document.file_id
            WHERE document.application_id=? AND document.document_type=?
            """,
            applicationId,
            type);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found.");
    return readContent(rows.getFirst());
  }

  private DocumentContent readContent(Map<String, Object> row) {
    try {
      Path file = root.resolve(row.get("storage_key").toString()).normalize();
      if (!file.startsWith(root) || !Files.isRegularFile(file))
        throw new IOException("Stored file is unavailable.");
      return new DocumentContent(row.get("media_type").toString(), Files.readAllBytes(file));
    } catch (IOException exception) {
      throw new ApiException(
          HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document file is unavailable.");
    }
  }

  public record DocumentContent(String mediaType, byte[] bytes) {}

  private String detect(byte[] b) {
    if (b.length >= 8 && b[0] == (byte) 0x89 && b[1] == 0x50 && b[2] == 0x4e && b[3] == 0x47)
      return "image/png";
    if (b.length >= 3 && b[0] == (byte) 0xff && b[1] == (byte) 0xd8 && b[2] == (byte) 0xff)
      return "image/jpeg";
    if (b.length >= 5 && new String(b, 0, 5).equals("%PDF-")) return "application/pdf";
    throw bad("INVALID_FILE_SIGNATURE", "File signature is not allowed.");
  }

  private String safeName(String n) {
    if (n == null) return "upload";
    String v = Paths.get(n).getFileName().toString().replaceAll("[\\r\\n]", "");
    return v.length() > 255 ? v.substring(v.length() - 255) : v;
  }

  private ApiException bad(String c, String m) {
    return new ApiException(HttpStatus.BAD_REQUEST, c, m);
  }
}
