package com.uttarabank.careerportal.file;

import java.util.*;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me/documents")
public class DocumentController {
  private final DocumentService service;

  public DocumentController(DocumentService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public DocumentResponse upload(
      @RequestParam String documentType, @RequestPart("file") MultipartFile file) {
    return service.store(documentType, file);
  }

  @GetMapping
  public List<Map<String, Object>> documents() {
    return service.documents();
  }

  @GetMapping("/{documentType}/content")
  public ResponseEntity<byte[]> content(@PathVariable String documentType) {
    DocumentService.DocumentContent content = service.content(documentType);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType(content.mediaType()))
        .body(content.bytes());
  }

  public record DocumentResponse(
      long fileId, String documentType, String validationStatus, Integer width, Integer height) {}
}
