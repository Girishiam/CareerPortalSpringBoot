package com.uttarabank.careerportal.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
  public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = request.getHeader("X-Correlation-ID");
    if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
    request.setAttribute(ATTRIBUTE, id);
    response.setHeader("X-Correlation-ID", id);
    chain.doFilter(request, response);
  }
}
