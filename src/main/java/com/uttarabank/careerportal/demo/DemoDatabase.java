package com.uttarabank.careerportal.demo;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DemoDatabase {
  private final HikariDataSource dataSource;
  private final JdbcTemplate jdbc;

  public DemoDatabase(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${spring.datasource.driver-class-name}") String driverClassName) {
    HikariConfig config = new HikariConfig();
    config.setPoolName("demo-admit-card-pool");
    config.setJdbcUrl(url);
    config.setUsername(username);
    config.setPassword(password);
    config.setDriverClassName(driverClassName);
    config.setMaximumPoolSize(6);
    config.setMinimumIdle(1);
    config.setConnectionTimeout(30_000);
    config.setValidationTimeout(5_000);
    config.setMaxLifetime(300_000);
    config.setKeepaliveTime(60_000);
    dataSource = new HikariDataSource(config);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.setQueryTimeout(30);
  }

  public JdbcTemplate jdbc() {
    return jdbc;
  }

  @PreDestroy
  void close() {
    dataSource.close();
  }
}
