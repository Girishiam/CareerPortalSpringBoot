package com.uttarabank.careerportal.applicant;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/cv")
public class CvController {
  private final CvService service;

  public CvController(CvService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> cv() {
    return service.cv();
  }
}
