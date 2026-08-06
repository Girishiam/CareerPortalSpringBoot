package com.uttarabank.careerportal.common;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Logs the effective pool settings after all property sources and IDE overrides are applied. */
@Component
public class DatabasePoolDiagnostics implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DatabasePoolDiagnostics.class);
  private final DataSource dataSource;

  public DatabasePoolDiagnostics(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    if (dataSource instanceof HikariDataSource hikari) {
      log.info(
          "Main database pool configured. name={} maximumPoolSize={} minimumIdle={} connectionTimeoutMs={} query isolation=application",
          hikari.getPoolName(),
          hikari.getMaximumPoolSize(),
          hikari.getMinimumIdle(),
          hikari.getConnectionTimeout());
    } else {
      log.info("Main database datasource configured. type={}", dataSource.getClass().getName());
    }
  }
}
