package com.uttarabank.careerportal.file;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/applications")
public class AdminDocumentController {
  private final DocumentService documents;

  public AdminDocumentController(DocumentService documents) {
    this.documents = documents;
  }

  @GetMapping("/{applicationId}/documents/{documentType}/content")
  public ResponseEntity<byte[]> content(
      @PathVariable long applicationId, @PathVariable String documentType) {
    DocumentService.DocumentContent content =
        documents.applicationContent(applicationId, documentType);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType(content.mediaType()))
        .body(content.bytes());
  }
}
