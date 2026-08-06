package com.uttarabank.careerportal.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/applicants/register")
  public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
    return service.register(request);
  }

  @PostMapping("/login")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return service.login(request);
  }

  @PostMapping("/applicants/login")
  public TokenResponse applicantLogin(@Valid @RequestBody LoginRequest request) {
    return service.loginApplicant(request);
  }

  @PostMapping("/admins/login")
  public TokenResponse adminLogin(@Valid @RequestBody LoginRequest request) {
    return service.loginAdmin(request);
  }

  public record RegistrationRequest(
      @NotBlank @Size(max = 150) String fullName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Pattern(regexp = "^01[3-9]\\d{8}$") String mobile,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(min = 12, max = 72) String confirmPassword) {}

  public record RegistrationResponse(
      long userId, long applicantId, String cvNumber, boolean verificationRequired) {}

  public record LoginRequest(@NotBlank String login, @NotBlank String password) {}

  public record TokenResponse(
      String accessToken, String tokenType, List<String> roles, String destination) {}
}
