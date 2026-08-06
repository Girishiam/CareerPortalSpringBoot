package com.uttarabank.careerportal.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.uttarabank.careerportal.common.ApiException;
import com.uttarabank.careerportal.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceValidationTests {

  @Test
  void rejectsMismatchedPasswordConfirmationBeforeAccessingDatabase() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PasswordEncoder passwords = mock(PasswordEncoder.class);
    JwtService jwt = mock(JwtService.class);
    AuthService service = new AuthService(jdbc, passwords, jwt);
    var request =
        new AuthController.RegistrationRequest(
            "Applicant Name",
            "applicant@example.com",
            "01712345678",
            "StrongPassword123",
            "DifferentPassword123");

    ApiException exception = assertThrows(ApiException.class, () -> service.register(request));

    assertEquals("PASSWORD_MISMATCH", exception.code());
    verifyNoInteractions(jdbc, passwords, jwt);
  }
}
