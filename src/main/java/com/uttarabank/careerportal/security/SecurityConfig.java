package com.uttarabank.careerportal.security;

import com.uttarabank.careerportal.audit.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, JwtAuthenticationFilter jwt, AuditEventWriter auditWriter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/login",
                        "/register",
                        "/portal",
                        "/portal/**",
                        "/admin",
                        "/admin/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/jobs",
                        "/api/v1/jobs/*",
                        "/api/v1/master-data/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasAnyRole("HR_ADMIN", "SYSTEM_ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new SiteActivityFilter(auditWriter), JwtAuthenticationFilter.class)
        .build();
  }
}
