package com.uttarabank.careerportal.audit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A small bulkhead pool so best-effort audit writes never occupy request-serving connections. */
@Component
public class AuditDatabase {
  private final HikariDataSource dataSource;
  private final JdbcTemplate jdbc;

  public AuditDatabase(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${spring.datasource.driver-class-name}") String driverClassName) {
    HikariConfig config = new HikariConfig();
    config.setPoolName("audit-writer-pool");
    config.setJdbcUrl(url);
    config.setUsername(username);
    config.setPassword(password);
    config.setDriverClassName(driverClassName);
    config.setMaximumPoolSize(1);
    config.setMinimumIdle(0);
    config.setConnectionTimeout(2_000);
    config.setValidationTimeout(1_000);
    config.setIdleTimeout(60_000);
    config.setMaxLifetime(300_000);
    dataSource = new HikariDataSource(config);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.setQueryTimeout(5);
  }

  JdbcTemplate jdbc() {
    return jdbc;
  }

  @PreDestroy
  void close() {
    dataSource.close();
  }
}
