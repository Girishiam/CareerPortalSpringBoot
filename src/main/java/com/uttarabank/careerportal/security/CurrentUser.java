package com.uttarabank.careerportal.security;

import com.uttarabank.careerportal.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
  private CurrentUser() {}

  public static AuthenticatedUser get() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof AuthenticatedUser user) return user;
    throw new ApiException(
        HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
  }
}
