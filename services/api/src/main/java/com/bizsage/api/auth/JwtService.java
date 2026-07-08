package com.bizsage.api.auth;

import com.bizsage.api.users.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;

  public JwtService(@Value("${bizsage.jwt.secret}") String secret) {
    // Require at least 32 bytes (256 bits) for HS256.
    // Reject short / placeholder secrets at startup.
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("bizsage.jwt.secret must be set (at least 32 characters)");
    }
    byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
    if (raw.length < 32) {
      throw new IllegalArgumentException("bizsage.jwt.secret must be at least 32 bytes for HS256");
    }
    this.key = Keys.hmacShaKeyFor(raw);
  }

  /** Issue a standard JWT (header.payload.signature) valid for 1 hour. */
  public String issue(UserAccount user) {
    long now = Instant.now().getEpochSecond();
    return Jwts.builder()
        .subject(user.username())
        .claim("role", user.role().name())
        .claim("regionId", user.regionId())
        .claim("industryId", user.industryId())
        .claim("membershipLevel", user.membershipLevel())
        .issuedAt(new Date(now * 1000))
        .expiration(new Date((now + 3600) * 1000))
        .signWith(key)
        .compact();
  }

  /** Verify a standard JWT and extract the principal. */
  public JwtPrincipal verify(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token is missing");
    }
    try {
      Claims claims = Jwts.parser()
          .verifyWith(key)
          .build()
          .parseSignedClaims(token)
          .getPayload();

      String roleName = claims.get("role", String.class);
      if (roleName == null) {
        throw new IllegalArgumentException("token missing role claim");
      }
      return new JwtPrincipal(claims.getSubject(), Role.valueOf(roleName));
    } catch (JwtException | IllegalArgumentException ex) {
      throw new IllegalArgumentException("invalid token: " + ex.getMessage(), ex);
    }
  }
}
