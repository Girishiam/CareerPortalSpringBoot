package com.uttarabank.careerportal.audit;

import com.uttarabank.careerportal.common.CorrelationIdFilter;
import com.uttarabank.careerportal.security.AuthenticatedUser;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SiteActivityFilter extends OncePerRequestFilter {
  private static final String API_PREFIX = "/api/";
  private static final Pattern SECRET_QUERY =
      Pattern.compile("(?i)(password|token|secret|authorization|otp)=([^&]*)");
  private final AuditEventWriter writer;

  public SiteActivityFilter(AuditEventWriter writer) {
    this.writer = writer;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(API_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long started = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      record(request, response, (System.nanoTime() - started) / 1_000_000);
    }
  }

  private void record(HttpServletRequest request, HttpServletResponse response, long durationMs) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    AuthenticatedUser actor =
        authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
            ? user
            : null;
    String roles =
        actor == null
            ? null
            : actor.roles().stream().sorted().reduce((a, b) -> a + "," + b).orElse(null);
    String path = request.getRequestURI();
    AuditTarget target = target(request.getMethod(), path);
    String socketIp = normalizeIp(request.getRemoteAddr());
    String remoteIp = clipped(resolveClientIp(request, socketIp), 45);
    String remoteHost = clipped(request.getRemoteHost(), 255);
    if (Objects.equals(remoteHost, request.getRemoteAddr()) || Objects.equals(remoteHost, socketIp))
      remoteHost = null;
    String userAgent = clipped(request.getHeader("User-Agent"), 1000);
    int status = response.getStatus();
    writer.submit(
        new AuditEvent(
            actor == null ? null : actor.userId(),
            roles,
            category(path, actor),
            target.action(),
            target.entityType(),
            target.entityId(),
            correlationId(request),
            request.getMethod(),
            clipped(path, 500),
            sanitizeQuery(request.getQueryString()),
            clipped(request.getContentType(), 150),
            status,
            status < 400,
            durationMs,
            remoteIp,
            clipped(forwardingChain(request), 500),
            remoteHost,
            userAgent,
            browser(userAgent),
            operatingSystem(userAgent),
            clipped(request.getHeader("Referer"), 1000)));
  }

  private String category(String path, AuthenticatedUser actor) {
    if (path.startsWith("/api/v1/auth/")) return "AUTH";
    if (path.startsWith("/api/v1/admin/")
        || actor != null
            && actor.roles().stream()
                .anyMatch(role -> role.equals("HR_ADMIN") || role.equals("SYSTEM_ADMIN")))
      return "ADMIN";
    return actor == null ? "PUBLIC" : "APPLICANT";
  }

  private AuditTarget target(String method, String path) {
    String normalized = path.replaceFirst("^/api(?:/v\\d+)?/?", "");
    String[] parts = normalized.isBlank() ? new String[] {"api"} : normalized.split("/");
    int start = parts.length > 1 && parts[0].equals("admin") ? 1 : 0;
    String entity =
        parts[Math.min(start, parts.length - 1)].replace('-', '_').toUpperCase(Locale.ROOT);
    String id =
        Arrays.stream(parts).filter(value -> value.matches("\\d+")).findFirst().orElse(null);
    String operation =
        switch (method) {
          case "GET", "HEAD" -> "READ";
          case "POST" -> "CREATE";
          case "DELETE" -> "DELETE";
          default -> "UPDATE";
        };
    String last = parts[parts.length - 1];
    if (!last.matches("\\d+") && !last.equalsIgnoreCase(entity.replace('_', '-'))) {
      operation = last.replace('-', '_').toUpperCase(Locale.ROOT);
    }
    return new AuditTarget(operation + "_" + singular(entity), entity, id);
  }

  private String singular(String value) {
    return value.endsWith("S") && value.length() > 1
        ? value.substring(0, value.length() - 1)
        : value;
  }

  private String sanitizeQuery(String query) {
    if (query == null || query.isBlank()) return null;
    return clipped(SECRET_QUERY.matcher(query).replaceAll("$1=[REDACTED]"), 1000);
  }

  private String browser(String ua) {
    if (ua == null) return null;
    if (ua.contains("Edg/")) return "Microsoft Edge";
    if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
    if (ua.contains("Chrome/")) return "Google Chrome";
    if (ua.contains("Firefox/")) return "Mozilla Firefox";
    if (ua.contains("Safari/") && !ua.contains("Chrome/")) return "Safari";
    return "Other";
  }

  private String operatingSystem(String ua) {
    if (ua == null) return null;
    if (ua.contains("Windows")) return "Windows";
    if (ua.contains("Android")) return "Android";
    if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
    if (ua.contains("Mac OS")) return "macOS";
    if (ua.contains("Linux")) return "Linux";
    return "Other";
  }

  private String resolveClientIp(HttpServletRequest request, String socketIp) {
    if (isTrustedProxy(socketIp)) {
      String candidate = firstIp(request.getHeader("CF-Connecting-IP"));
      if (candidate == null) candidate = firstIp(request.getHeader("X-Forwarded-For"));
      if (candidate == null) candidate = firstIp(request.getHeader("X-Real-IP"));
      if (candidate == null) candidate = forwardedIp(request.getHeader("Forwarded"));
      if (candidate != null) return candidate;
    }
    return socketIp;
  }

  private String forwardingChain(HttpServletRequest request) {
    for (String name : List.of("X-Forwarded-For", "Forwarded", "CF-Connecting-IP", "X-Real-IP")) {
      String value = request.getHeader(name);
      if (value != null && !value.isBlank()) return value.strip();
    }
    return null;
  }

  private String firstIp(String value) {
    if (value == null || value.isBlank()) return null;
    return normalizeIp(value.split(",", 2)[0]);
  }

  private String forwardedIp(String value) {
    if (value == null || value.isBlank()) return null;
    for (String part : value.split(",", 2)[0].split(";"))
      if (part.strip().regionMatches(true, 0, "for=", 0, 4))
        return normalizeIp(part.strip().substring(4));
    return null;
  }

  private String normalizeIp(String value) {
    if (value == null) return null;
    String ip = value.strip().replace("\"", "");
    if (ip.startsWith("[")) {
      int end = ip.indexOf(']');
      if (end > 0) ip = ip.substring(1, end);
    } else if (ip.matches("[0-9.]+:[0-9]+")) ip = ip.substring(0, ip.lastIndexOf(':'));
    int zone = ip.indexOf('%');
    if (zone > 0) ip = ip.substring(0, zone);
    if (ip.startsWith("::ffff:")) ip = ip.substring(7);
    if (ip.isBlank() || "unknown".equalsIgnoreCase(ip) || ip.length() > 45) return null;
    if (ip.contains(":")) return ip.matches("[0-9A-Fa-f:]+") ? ip : null;
    if (!ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) return null;
    for (String octet : ip.split("\\.")) if (Integer.parseInt(octet) > 255) return null;
    return ip;
  }

  private boolean isTrustedProxy(String ip) {
    if (ip == null) return false;
    return ip.equals("::1")
        || ip.equals("0:0:0:0:0:0:0:1")
        || ip.startsWith("127.")
        || ip.startsWith("10.")
        || ip.startsWith("192.168.")
        || ip.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*")
        || ip.startsWith("fc")
        || ip.startsWith("fd");
  }

  private String clipped(String value, int max) {
    if (value == null || value.isBlank()) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }

  private String correlationId(HttpServletRequest request) {
    String value = Objects.toString(request.getAttribute(CorrelationIdFilter.ATTRIBUTE), "");
    try {
      return UUID.fromString(value).toString();
    } catch (IllegalArgumentException ignored) {
      return UUID.randomUUID().toString();
    }
  }

  private record AuditTarget(String action, String entityType, String entityId) {}
}
