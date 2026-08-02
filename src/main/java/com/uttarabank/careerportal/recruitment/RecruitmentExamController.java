package com.uttarabank.careerportal.recruitment;

import com.uttarabank.careerportal.file.DocumentService;
import com.uttarabank.careerportal.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RecruitmentExamController {
  private final RecruitmentExamService service;
  private final DocumentService documents;

  public RecruitmentExamController(RecruitmentExamService service, DocumentService documents) {
    this.service = service;
    this.documents = documents;
  }

  @GetMapping("/admin/exams")
  public List<Map<String, Object>> exams(@RequestParam(required = false) Long jobId) {
    return service.exams(jobId);
  }

  @PostMapping("/admin/exams")
  public Map<String, Object> create(@Valid @RequestBody ExamRequest request) {
    return service.create(request, CurrentUser.get().userId());
  }

  @GetMapping("/admin/exams/{eventId}")
  public Map<String, Object> exam(@PathVariable long eventId) {
    return service.exam(eventId);
  }

  @PostMapping("/admin/exams/{eventId}/candidates")
  public Map<String, Object> selectCandidates(
      @PathVariable long eventId, @Valid @RequestBody CandidateSelection request) {
    return service.selectCandidates(eventId, request.applicationIds());
  }

  @PostMapping("/admin/exams/{eventId}/rolls")
  public Map<String, Object> assignRolls(@PathVariable long eventId) {
    return service.assignRolls(eventId);
  }

  @PatchMapping("/admin/exams/{eventId}/candidates/{candidateId}/result")
  public Map<String, Object> result(
      @PathVariable long eventId,
      @PathVariable long candidateId,
      @Valid @RequestBody ResultRequest request) {
    return service.result(eventId, candidateId, request.resultStatus());
  }

  @PostMapping("/admin/exams/{eventId}/centers")
  public Map<String, Object> center(
      @PathVariable long eventId, @Valid @RequestBody CenterRequest request) {
    return service.addCenter(eventId, request);
  }

  @PostMapping("/admin/exams/{eventId}/centers/{centerId}/rooms")
  public Map<String, Object> room(
      @PathVariable long eventId,
      @PathVariable long centerId,
      @Valid @RequestBody RoomRequest request) {
    return service.addRoom(eventId, centerId, request);
  }

  @PostMapping("/admin/exams/{eventId}/seat-plan/auto-assign")
  public Map<String, Object> autoAssign(@PathVariable long eventId) {
    return service.autoAssignSeats(eventId);
  }

  @PostMapping("/admin/exams/{eventId}/generate")
  public Map<String, Object> generate(@PathVariable long eventId) {
    return service.generate(eventId);
  }

  @PostMapping("/admin/exams/{eventId}/publish")
  public Map<String, Object> publish(@PathVariable long eventId) {
    return service.publish(eventId);
  }

  @GetMapping("/admin/admit-cards")
  public List<Map<String, Object>> adminCards(@RequestParam(required = false) Long jobId) {
    return service.adminCards(jobId);
  }

  @GetMapping("/admin/admit-cards/{candidateId}")
  public Map<String, Object> adminCard(@PathVariable long candidateId) {
    return service.adminCard(candidateId);
  }

  @GetMapping("/admin/admit-cards/{candidateId}/documents/{documentType}")
  public ResponseEntity<byte[]> adminCardDocument(
      @PathVariable long candidateId, @PathVariable String documentType) {
    long applicationId = service.adminCardApplicationId(candidateId);
    DocumentService.DocumentContent content =
        documents.applicationContent(applicationId, documentType);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType(content.mediaType()))
        .body(content.bytes());
  }

  @GetMapping("/me/admit-cards")
  public List<Map<String, Object>> myCards() {
    return service.myCards();
  }

  @GetMapping("/me/admit-cards/{candidateId}")
  public Map<String, Object> myCard(@PathVariable long candidateId) {
    return service.myCard(candidateId);
  }

  @GetMapping("/me/admit-cards/{candidateId}/documents/{documentType}")
  public ResponseEntity<byte[]> cardDocument(
      @PathVariable long candidateId, @PathVariable String documentType) {
    long applicationId = service.myCardApplicationId(candidateId);
    DocumentService.DocumentContent content =
        documents.applicationContent(applicationId, documentType);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType(content.mediaType()))
        .body(content.bytes());
  }

  public record ExamRequest(
      @NotNull Long jobId,
      @NotBlank @Pattern(regexp = "MCQ|WRITTEN|COMBINED") String examType,
      @NotBlank @Size(max = 200) String title,
      @NotNull Instant examStartAt,
      @NotNull Instant examEndAt,
      Instant reportingAt,
      @Size(max = 8000) String instructions) {}

  public record CandidateSelection(@NotEmpty List<@NotNull Long> applicationIds) {}

  public record ResultRequest(
      @NotBlank @Pattern(regexp = "PENDING|PASSED|FAILED|ABSENT") String resultStatus) {}

  public record CenterRequest(
      @NotBlank @Size(max = 20) String centerCode,
      @NotBlank @Size(max = 200) String centerName,
      @NotBlank @Size(max = 500) String address,
      @Size(max = 30) String contactPhone) {}

  public record RoomRequest(
      @NotBlank @Size(max = 50) String roomNumber,
      @Size(max = 80) String floorName,
      @Min(1) @Max(5000) int capacity) {}
}
