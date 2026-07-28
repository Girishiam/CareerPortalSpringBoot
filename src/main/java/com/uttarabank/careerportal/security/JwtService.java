package com.uttarabank.careerportal.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final byte[] key;
  private final String issuer;
  private final long minutes;

  public JwtService(
      @Value("${career-portal.jwt.secret}") String secret,
      @Value("${career-portal.jwt.issuer}") String issuer,
      @Value("${career-portal.jwt.access-token-minutes}") long minutes) {
    try {
      this.key =
          MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    this.issuer = issuer;
    this.minutes = minutes;
  }

  public String create(long userId, Collection<String> roles) {
    try {
      Instant now = Instant.now();
      var claims =
          new JWTClaimsSet.Builder()
              .subject(Long.toString(userId))
              .issuer(issuer)
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(Duration.ofMinutes(minutes))))
              .claim("roles", roles)
              .build();
      var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(new MACSigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  public AuthenticatedUser verify(String token) {
    try {
      var jwt = SignedJWT.parse(token);
      if (!jwt.verify(new MACVerifier(key))) throw new JOSEException("Invalid signature");
      var claims = jwt.getJWTClaimsSet();
      if (!issuer.equals(claims.getIssuer()) || claims.getExpirationTime().before(new Date()))
        throw new JOSEException("Expired token");
      return new AuthenticatedUser(
          Long.parseLong(claims.getSubject()), new HashSet<>(claims.getStringListClaim("roles")));
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid access token", e);
    }
  }
}
