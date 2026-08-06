package com.uttarabank.careerportal.audit;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Dhaka");
  private final JdbcTemplate jdbc;

  public AuditLogService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> search(
      Long userId,
      String actorType,
      String category,
      String email,
      String action,
      String entityType,
      String entityId,
      String ip,
      String method,
      Integer status,
      Boolean success,
      LocalDate from,
      LocalDate to,
      String query,
      int page,
      int size) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> parameters = new ArrayList<>();
    equal(where, parameters, "audit.actor_user_id", userId);
    equal(where, parameters, "audit.event_category", text(category));
    equal(where, parameters, "audit.http_method", text(method));
    equal(where, parameters, "audit.response_status", status);
    equal(where, parameters, "audit.success", success);
    if ("ANONYMOUS".equals(actorType)) where.append(" AND audit.actor_user_id IS NULL");
    if ("ADMIN".equals(actorType))
      where.append(" AND (audit.event_category='ADMIN' OR audit.actor_roles LIKE '%ADMIN%')");
    if ("APPLICANT".equals(actorType))
      where.append(
          " AND (audit.event_category='APPLICANT' OR audit.actor_roles LIKE '%APPLICANT%')");
    contains(where, parameters, "audit.actor_email", email);
    contains(where, parameters, "audit.action", action);
    contains(where, parameters, "audit.entity_type", entityType);
    contains(where, parameters, "audit.entity_id", entityId);
    if (text(ip) != null) {
      where.append(" AND (audit.client_ip LIKE ? OR audit.forwarded_for LIKE ?)");
      parameters.add(like(ip));
      parameters.add(like(ip));
    }
    if (from != null) {
      where.append(" AND audit.created_at>=?");
      parameters.add(Timestamp.from(from.atStartOfDay(BUSINESS_ZONE).toInstant()));
    }
    if (to != null) {
      where.append(" AND audit.created_at<?");
      parameters.add(Timestamp.from(to.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()));
    }
    if (text(query) != null) {
      where.append(
          " AND (audit.actor_name LIKE ? OR audit.actor_email LIKE ? OR audit.actor_roles LIKE ? OR audit.event_category LIKE ? OR audit.action LIKE ? OR audit.entity_type LIKE ? OR audit.entity_id LIKE ? OR audit.request_path LIKE ? OR audit.query_string LIKE ? OR audit.client_ip LIKE ? OR CONVERT(varchar(36),audit.correlation_id) LIKE ?)");
      for (int index = 0; index < 11; index++) parameters.add(like(query));
    }

    Long total =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.audit_log audit" + where,
            Long.class,
            parameters.toArray());
    List<Object> rowParameters = new ArrayList<>(parameters);
    rowParameters.add((long) page * size);
    rowParameters.add(size);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT audit.audit_id,audit.actor_user_id,
                   COALESCE(audit.actor_name,account.username,account.employee_id,account.email) actor_name,
                   COALESCE(audit.actor_email,account.email) actor_email,
                   COALESCE(audit.actor_employee_id,account.employee_id) actor_employee_id,
                   audit.actor_roles,audit.event_category,audit.action,audit.entity_type,
                   audit.entity_id,audit.http_method,audit.request_path,audit.query_string,
                   audit.content_type,audit.response_status,audit.success,audit.duration_ms,
                   audit.client_ip,audit.forwarded_for,audit.client_host,audit.browser_name,
                   audit.operating_system,audit.user_agent,audit.referer,
                   CONVERT(varchar(36),audit.correlation_id) correlation_id,
                   audit.details,audit.created_at
            FROM dbo.audit_log audit
            LEFT JOIN dbo.user_account account ON account.user_id=audit.actor_user_id
            """
                + where
                + " ORDER BY audit.created_at DESC,audit.audit_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            rowParameters.toArray());
    long count = Objects.requireNonNullElse(total, 0L);
    return Map.of(
        "content", rows,
        "page", page,
        "size", size,
        "totalElements", count,
        "totalPages", (int) Math.ceil((double) count / size));
  }

  private void equal(StringBuilder where, List<Object> parameters, String column, Object value) {
    if (value == null) return;
    where.append(" AND ").append(column).append("=?");
    parameters.add(value);
  }

  private void contains(StringBuilder where, List<Object> parameters, String column, String value) {
    if (text(value) == null) return;
    where.append(" AND ").append(column).append(" LIKE ?");
    parameters.add(like(value));
  }

  private String like(String value) {
    return "%" + text(value) + "%";
  }

  private String text(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
