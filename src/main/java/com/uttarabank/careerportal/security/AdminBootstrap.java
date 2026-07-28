package com.uttarabank.careerportal.security;

import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(
    prefix = "career-portal.admin-bootstrap",
    name = "enabled",
    havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final TransactionTemplate transactions;
  private final String email;
  private final String password;

  public AdminBootstrap(
      JdbcTemplate jdbc,
      PasswordEncoder passwords,
      TransactionTemplate transactions,
      @Value("${career-portal.admin-bootstrap.email:}") String email,
      @Value("${career-portal.admin-bootstrap.password:}") String password) {
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.transactions = transactions;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    if (email == null || email.isBlank() || password == null || password.length() < 12) {
      throw new IllegalStateException(
          "Admin bootstrap requires ADMIN_EMAIL and an ADMIN_PASSWORD of at least 12 characters.");
    }
    transactions.executeWithoutResult(status -> createOrRepairAdministrator());
  }

  private void createOrRepairAdministrator() {
    ensureSystemAdminRole();
    String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
    var users =
        jdbc.queryForList(
            "SELECT user_id FROM dbo.user_account WHERE LOWER(email)=?",
            Long.class,
            normalizedEmail);
    long userId = users.isEmpty() ? createAdministrator(normalizedEmail) : users.getFirst();

    if (hasColumn("user_account", "user_type")) {
      jdbc.update(
          "UPDATE dbo.user_account SET user_type='ADMIN',status='ACTIVE' WHERE user_id=?",
          userId);
    } else {
      jdbc.update("UPDATE dbo.user_account SET status='ACTIVE' WHERE user_id=?", userId);
    }

    jdbc.update(
        """
        INSERT dbo.user_role(user_id,role_id)
        SELECT ?,role.role_id
        FROM dbo.role AS role
        WHERE role.code='SYSTEM_ADMIN'
          AND NOT EXISTS(
            SELECT 1 FROM dbo.user_role AS assigned
            WHERE assigned.user_id=? AND assigned.role_id=role.role_id
          )
        """,
        userId,
        userId);
  }

  private void ensureSystemAdminRole() {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.role WHERE code='SYSTEM_ADMIN'", Integer.class);
    if (count != null && count > 0) return;

    if (hasColumn("role", "role_code")) {
      jdbc.update(
          """
          INSERT dbo.role(role_code,role_name,code,name,description,is_active)
          VALUES('SYSTEM_ADMIN','System Administrator','SYSTEM_ADMIN',
                 'System Administrator','Career portal system administrator',1)
          """);
    } else {
      jdbc.update("INSERT dbo.role(code,name) VALUES('SYSTEM_ADMIN','System Administrator')");
    }
  }

  private long createAdministrator(String normalizedEmail) {
    String username = "system-admin";
    String hash = passwords.encode(password);
    GeneratedKeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          String sql =
              hasColumn("user_account", "user_type")
                  ? "INSERT dbo.user_account(email,username,password_hash,status,user_type) VALUES(?,?,?,'ACTIVE','ADMIN')"
                  : "INSERT dbo.user_account(email,username,password_hash,status) VALUES(?,?,?,'ACTIVE')";
          var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          statement.setString(1, normalizedEmail);
          statement.setString(2, username);
          statement.setString(3, hash);
          return statement;
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
            WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=? AND COLUMN_NAME=?
            """,
            Integer.class,
            table,
            column);
    return count != null && count > 0;
  }
}
