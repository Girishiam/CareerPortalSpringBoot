package com.uttarabank.careerportal.audit;

import jakarta.annotation.*;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuditEventWriter {
  private static final Logger log = LoggerFactory.getLogger(AuditEventWriter.class);
  private static final int BATCH_SIZE = 100;
  private final JdbcTemplate jdbc;
  private final BlockingQueue<AuditEvent> queue = new ArrayBlockingQueue<>(10_000);
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          task -> {
            Thread thread = new Thread(task, "site-audit-writer");
            thread.setDaemon(true);
            return thread;
          });
  private volatile boolean running = true;

  public AuditEventWriter(AuditDatabase database) {
    this.jdbc = database.jdbc();
  }

  @PostConstruct
  void start() {
    worker.submit(this::writeLoop);
  }

  void submit(AuditEvent event) {
    if (!queue.offer(event))
      log.warn(
          "Audit queue full; event retained only in application log. action={} path={} correlationId={}",
          event.action(),
          event.path(),
          event.correlationId());
  }

  private void writeLoop() {
    List<AuditEvent> batch = new ArrayList<>(BATCH_SIZE);
    while (running || !queue.isEmpty()) {
      try {
        AuditEvent first = queue.poll(1, TimeUnit.SECONDS);
        if (first == null) continue;
        batch.add(first);
        queue.drainTo(batch, BATCH_SIZE - 1);
        persist(batch);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception failure) {
        log.error(
            "Audit database batch failed; {} events retained only in application log.",
            batch.size(),
            failure);
      } finally {
        batch.clear();
      }
    }
  }

  private void persist(List<AuditEvent> events) {
    jdbc.batchUpdate(
        """
        INSERT dbo.audit_log(
          actor_user_id,actor_name,actor_email,actor_employee_id,actor_roles,event_category,
          action,entity_type,entity_id,correlation_id,http_method,request_path,query_string,
          content_type,response_status,success,duration_ms,client_ip,forwarded_for,client_host,
          user_agent,browser_name,operating_system,referer)
        SELECT ?,COALESCE(NULLIF(account.username,''),NULLIF(account.employee_id,''),account.email,
                          CASE WHEN ? IS NULL THEN 'Anonymous' ELSE CONCAT('User ',?) END),
               account.email,account.employee_id,?,?,?,?,?,CONVERT(uniqueidentifier,?),?,?,?,?,?,?,?,?,?,?,?,?,?,?
          FROM (VALUES(1)) seed(value)
          LEFT JOIN dbo.user_account account ON account.user_id=?
        """,
        events,
        BATCH_SIZE,
        (PreparedStatement statement, AuditEvent event) -> {
          int index = 1;
          statement.setObject(index++, event.actorId());
          statement.setObject(index++, event.actorId());
          statement.setObject(index++, event.actorId());
          statement.setString(index++, event.actorRoles());
          statement.setString(index++, event.category());
          statement.setString(index++, event.action());
          statement.setString(index++, event.entityType());
          statement.setString(index++, event.entityId());
          statement.setString(index++, event.correlationId());
          statement.setString(index++, event.method());
          statement.setString(index++, event.path());
          statement.setString(index++, event.queryString());
          statement.setString(index++, event.contentType());
          statement.setInt(index++, event.responseStatus());
          statement.setBoolean(index++, event.success());
          statement.setLong(index++, event.durationMs());
          statement.setString(index++, event.clientIp());
          statement.setString(index++, event.forwardedFor());
          statement.setString(index++, event.clientHost());
          statement.setString(index++, event.userAgent());
          statement.setString(index++, event.browser());
          statement.setString(index++, event.operatingSystem());
          statement.setString(index++, event.referer());
          statement.setObject(index, event.actorId());
        });
  }

  @PreDestroy
  void stop() {
    running = false;
    worker.shutdown();
    try {
      if (!worker.awaitTermination(5, TimeUnit.SECONDS)) worker.shutdownNow();
    } catch (InterruptedException interrupted) {
      worker.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
