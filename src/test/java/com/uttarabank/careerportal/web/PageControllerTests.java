package com.uttarabank.careerportal.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PageControllerTests {
  private final PageController controller = new PageController();

  @Test
  void applicantRoutesResolveToSeparateTemplates() {
    assertEquals("redirect:/portal/dashboard", controller.portal());
    assertEquals("portal/dashboard", controller.applicantDashboard());
    assertEquals("portal/profile/personal", controller.applicantPersonalProfile());
    assertEquals("portal/profile/addresses", controller.applicantAddresses());
    assertEquals("portal/profile/education", controller.applicantEducation());
    assertEquals("portal/profile/experience", controller.applicantExperience());
    assertEquals("portal/profile/documents", controller.applicantDocuments());
    assertEquals("portal/jobs/list", controller.applicantJobs());
    assertEquals("portal/jobs/details", controller.applicantJobDetails());
    assertEquals("portal/applications/list", controller.applicantApplications());
    assertEquals("portal/applications/details", controller.applicantApplicationDetails());
  }

  @Test
  void adminRoutesResolveToSeparateTemplates() {
    assertEquals("auth/admin-login", controller.adminLogin());
    assertEquals("redirect:/admin/dashboard", controller.admin());
    assertEquals("admin/dashboard", controller.adminDashboard());
    assertEquals("admin/jobs/list", controller.adminJobs());
    assertEquals("admin/jobs/form", controller.adminJobForm());
    assertEquals("admin/jobs/details", controller.adminJobDetails());
    assertEquals("admin/applications/list", controller.adminApplications());
    assertEquals("admin/applications/details", controller.adminApplicationDetails());
    assertEquals("admin/users/list", controller.adminUsers());
  }
}
