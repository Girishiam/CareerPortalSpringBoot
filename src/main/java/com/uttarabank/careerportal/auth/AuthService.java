package com.uttarabank.careerportal.auth;

import com.uttarabank.careerportal.common.*;
import com.uttarabank.careerportal.security.JwtService;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final JwtService jwt;

  public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords, JwtService jwt) {
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.jwt = jwt;
  }

  @Transactional
  public AuthController.RegistrationResponse register(AuthController.RegistrationRequest input) {
    String email = input.email().strip().toLowerCase(Locale.ROOT),
        mobile = input.mobile().strip(),
        name = input.fullName().strip();
    if (name.isBlank())
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Full name is required.");
    Integer exists =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.user_account WHERE email=? OR mobile=?",
            Integer.class,
            email,
            mobile);
    if (exists != null && exists > 0)
      throw new ApiException(
          HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "Email or mobile is already registered.");
    String username = "app-" + UUID.randomUUID().toString().substring(0, 12);
    long userId =
        hasColumn("user_account", "user_type")
            ? insert(
                "INSERT dbo.user_account(email,mobile,username,password_hash,status,user_type) VALUES (?,?,?,?,'ACTIVE','APPLICANT')",
                email,
                mobile,
                username,
                passwords.encode(input.password()))
            : insert(
                "INSERT dbo.user_account(email,mobile,username,password_hash,status) VALUES (?,?,?,?,'ACTIVE')",
                email,
                mobile,
                username,
                passwords.encode(input.password()));
    jdbc.update(
        "INSERT dbo.user_role(user_id,role_id) SELECT ?,role_id FROM dbo.role WHERE code='APPLICANT'",
        userId);

    String cv = nextAvailableCvNumber();
    long applicantId =
        insert(
            "INSERT dbo.applicant_profile(user_id,cv_number,full_name) VALUES (?,?,?)",
            userId,
            cv,
            name);

    jdbc.update(
        "INSERT dbo.audit_log(actor_user_id,action,entity_type,entity_id,correlation_id) VALUES (?,?,?,?,NEWID())",
        userId,
        "APPLICANT_REGISTERED",
        "APPLICANT",
        Long.toString(applicantId));
    return new AuthController.RegistrationResponse(userId, applicantId, cv, false);
  }

  private String nextAvailableCvNumber() {
    while (true) {
      Long sequenceValue =
          jdbc.queryForObject("SELECT NEXT VALUE FOR dbo.cv_number_seq", Long.class);
      String cvNumber = "UTB-CV-" + String.format("%09d", Objects.requireNonNull(sequenceValue));

      Integer existing =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM dbo.applicant_profile WHERE cv_number=?",
              Integer.class,
              cvNumber);

      if (existing == null || existing == 0) {
        return cvNumber;
      }
    }
  }

  public AuthController.TokenResponse login(AuthController.LoginRequest input) {
    return login(input, LoginAudience.ANY);
  }

  public AuthController.TokenResponse loginApplicant(AuthController.LoginRequest input) {
    return login(input, LoginAudience.APPLICANT);
  }

  public AuthController.TokenResponse loginAdmin(AuthController.LoginRequest input) {
    return login(input, LoginAudience.ADMIN);
  }

  private AuthController.TokenResponse login(
      AuthController.LoginRequest input, LoginAudience audience) {
    String login = input.login().strip().toLowerCase(Locale.ROOT);
    var rows =
        jdbc.query(
            "SELECT user_id,password_hash,status FROM dbo.user_account WHERE email=? OR mobile=? OR username=? OR employee_id=?",
            (rs, n) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getString(3)},
            login,
            login,
            login,
            login);
    if (rows.size() != 1 || !passwords.matches(input.password(), (String) rows.getFirst()[1]))
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid login or password.");
    long userId = (long) rows.getFirst()[0];
    var roles =
        jdbc.queryForList(
            "SELECT r.code FROM dbo.role r JOIN dbo.user_role ur ON ur.role_id=r.role_id WHERE ur.user_id=?",
            String.class,
            userId);
    boolean isAdmin =
        roles.stream().anyMatch(role -> role.equals("HR_ADMIN") || role.equals("SYSTEM_ADMIN"));
    boolean isApplicant = roles.contains("APPLICANT");
    if ((audience == LoginAudience.ADMIN && !isAdmin)
        || (audience == LoginAudience.APPLICANT && !isApplicant)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid login or password.");
    }
    String destination = isAdmin ? "/admin" : "/portal";
    return new AuthController.TokenResponse(
        jwt.create(userId, roles), "Bearer", roles, destination);
  }

  private enum LoginAudience {
    ANY,
    APPLICANT,
    ADMIN
  }

  private long insert(String sql, Object... args) {
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
          return ps;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  private boolean hasColumn(String table, String column) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = ? AND COLUMN_NAME = ?
            """,
            Integer.class,
            table,
            column);
    return count != null && count > 0;
  }
}
