package com.uttarabank.careerportal.audit;

record AuditEvent(
    Long actorId,
    String actorRoles,
    String category,
    String action,
    String entityType,
    String entityId,
    String correlationId,
    String method,
    String path,
    String queryString,
    String contentType,
    int responseStatus,
    boolean success,
    long durationMs,
    String clientIp,
    String forwardedFor,
    String clientHost,
    String userAgent,
    String browser,
    String operatingSystem,
    String referer) {}
