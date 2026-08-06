package com.uttarabank.careerportal.web;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PageController {
  @GetMapping("/favicon.ico")
  @ResponseBody
  public ResponseEntity<Void> favicon() {
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/")
  public String home() {
    return "redirect:/login";
  }

  @GetMapping("/login")
  public String login() {
    return "auth/login";
  }

  @GetMapping("/admin/login")
  public String adminLogin() {
    return "auth/admin-login";
  }

  @GetMapping("/register")
  public String register() {
    return "auth/register";
  }

  @GetMapping("/portal")
  public String portal() {
    return "redirect:/portal/dashboard";
  }

  @GetMapping("/portal/dashboard")
  public String applicantDashboard() {
    return "portal/dashboard";
  }

  @GetMapping("/portal/profile/personal")
  public String applicantPersonalProfile() {
    return "portal/profile/personal";
  }

  @GetMapping("/portal/profile/addresses")
  public String applicantAddresses() {
    return "portal/profile/addresses";
  }

  @GetMapping("/portal/profile/education")
  public String applicantEducation() {
    return "portal/profile/education";
  }

  @GetMapping("/portal/profile/experience")
  public String applicantExperience() {
    return "portal/profile/experience";
  }

  @GetMapping("/portal/profile/additional")
  public String applicantAdditionalInformation() {
    return "portal/profile/additional";
  }

  @GetMapping("/portal/profile/documents")
  public String applicantDocuments() {
    return "portal/profile/documents";
  }

  @GetMapping("/portal/jobs")
  public String applicantJobs() {
    return "portal/jobs/list";
  }

  @GetMapping("/portal/jobs/{jobId}")
  public String applicantJobDetails() {
    return "portal/jobs/details";
  }

  @GetMapping("/portal/applications")
  public String applicantApplications() {
    return "portal/applications/list";
  }

  @GetMapping("/portal/applications/{applicationId}")
  public String applicantApplicationDetails() {
    return "portal/applications/details";
  }

  @GetMapping("/admin")
  public String admin() {
    return "redirect:/admin/dashboard";
  }

  @GetMapping("/admin/dashboard")
  public String adminDashboard() {
    return "admin/dashboard";
  }

  @GetMapping("/admin/jobs")
  public String adminJobs() {
    return "admin/jobs/list";
  }

  @GetMapping("/admin/jobs/new")
  public String adminJobForm() {
    return "admin/jobs/form";
  }

  @GetMapping("/admin/jobs/{jobId}/edit")
  public String adminJobEditForm() {
    return "admin/jobs/form";
  }

  @GetMapping("/admin/jobs/{jobId}")
  public String adminJobDetails() {
    return "admin/jobs/details";
  }

  @GetMapping("/admin/applications")
  public String adminApplications() {
    return "admin/applications/list";
  }

  @GetMapping("/admin/shortlists")
  public String adminShortlists() {
    return "admin/shortlists/list";
  }

  @GetMapping("/admin/exams")
  public String adminExams() {
    return "admin/exams/list";
  }

  @GetMapping("/admin/exams/{examEventId}")
  public String adminExamDetails() {
    return "admin/exams/details";
  }

  @GetMapping("/admin/admit-cards")
  public String adminAdmitCards() {
    return "admin/admit-cards/list";
  }

  @GetMapping("/admin/admit-cards/{candidateId}")
  public String adminAdmitCard() {
    return "admin/admit-cards/details";
  }

  @GetMapping("/admin/demo-admit-cards")
  public String adminDemoAdmitCards() {
    return "admin/demo-admit-cards/list";
  }

  @GetMapping("/admin/applications/{applicationId}")
  public String adminApplicationDetails() {
    return "admin/applications/details";
  }

  @GetMapping("/admin/users")
  public String adminUsers() {
    return "admin/users/list";
  }

  @GetMapping("/admin/audit-logs")
  public String adminAuditLogs() {
    return "admin/audit-logs/list";
  }

  @GetMapping("/portal/admit-cards")
  public String applicantAdmitCards() {
    return "portal/admit-cards/list";
  }

  @GetMapping("/portal/admit-cards/{candidateId}")
  public String applicantAdmitCard() {
    return "portal/admit-cards/details";
  }
}
