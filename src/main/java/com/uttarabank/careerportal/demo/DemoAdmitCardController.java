package com.uttarabank.careerportal.demo;

import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/demo-admit-cards")
public class DemoAdmitCardController {
  private final DemoAdmitCardService service;

  public DemoAdmitCardController(DemoAdmitCardService service) {
    this.service = service;
  }

  @GetMapping("/batches")
  public List<Map<String, Object>> batches() {
    return service.batches();
  }

  @GetMapping
  public Map<String, Object> cards(
      @RequestParam long batchId,
      @RequestParam(required = false) String tracking,
      @RequestParam(required = false) String roll,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String jobCode,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    return service.cards(batchId, tracking, roll, name, jobCode, status, page, size);
  }

  @GetMapping("/batches/{batchId}/progress")
  public Map<String, Object> progress(@PathVariable long batchId) {
    return service.progress(batchId);
  }

  @PostMapping("/batches/{batchId}/generate")
  public Map<String, Object> generateBatch(@PathVariable long batchId) {
    return service.start(batchId);
  }

  @DeleteMapping("/batches/{batchId}/generated-pdfs")
  public Map<String, Object> resetBatch(@PathVariable long batchId) {
    return service.reset(batchId);
  }

  @PostMapping("/{cardId}/generate")
  public Map<String, Object> generateOne(@PathVariable long cardId) {
    return service.generateOne(cardId);
  }

  @GetMapping("/{cardId}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable long cardId) {
    DemoAdmitCardService.PdfFile file = service.pdf(cardId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(file.filename()).build().toString())
        .body(file.bytes());
  }
}
