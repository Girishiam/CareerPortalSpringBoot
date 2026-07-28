package com.uttarabank.careerportal.security;

import java.util.Set;

public record AuthenticatedUser(long userId, Set<String> roles) {}
