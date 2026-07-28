package com.uttarabank.careerportal.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.uttarabank.careerportal.applicant.ApplicantService;
import com.uttarabank.careerportal.common.ApiException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceValidationTests {
  private JdbcTemplate jdbc;
  private ApplicantService applicants;
  private DocumentService service;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    applicants = mock(ApplicantService.class);
    service =
        new DocumentService(
            jdbc, applicants, Path.of("target/test-files"), 1024, 200, 200, 1000, 1000);
  }

  @Test
  void rejectsUnsupportedDocumentType() {
    var file = new MockMultipartFile("file", "file.png", "image/png", pngHeader());

    assertCode("INVALID_DOCUMENT_TYPE", () -> service.store("EXECUTABLE", file));
    verifyNoInteractions(jdbc, applicants);
  }

  @Test
  void rejectsEmptyFile() {
    var file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

    assertCode("INVALID_FILE_SIZE", () -> service.store("PHOTO", file));
    verifyNoInteractions(jdbc, applicants);
  }

  @Test
  void rejectsFileOverConfiguredLimit() {
    var file = new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[1025]);

    assertCode("INVALID_FILE_SIZE", () -> service.store("NID", file));
    verifyNoInteractions(jdbc, applicants);
  }

  @Test
  void rejectsUnknownMagicBytesEvenWhenBrowserClaimsPng() {
    var file =
        new MockMultipartFile("file", "fake.png", "image/png", "this is not an image".getBytes());

    assertCode("INVALID_FILE_SIGNATURE", () -> service.store("PHOTO", file));
    verifyNoInteractions(jdbc, applicants);
  }

  @Test
  void rejectsTruncatedImageWithValidHeader() {
    var file = new MockMultipartFile("file", "broken.png", "image/png", pngHeader());

    assertCode("CORRUPTED_IMAGE", () -> service.store("PHOTO", file));
    verifyNoInteractions(jdbc, applicants);
  }

  private byte[] pngHeader() {
    return new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
  }

  private void assertCode(String code, Runnable action) {
    ApiException exception = assertThrows(ApiException.class, action::run);
    assertEquals(code, exception.code());
  }
}
