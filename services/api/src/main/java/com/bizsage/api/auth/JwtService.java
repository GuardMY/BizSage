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
    // HS256 至少需要 32 字节密钥；启动期拒绝空值和占位短密钥。
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("bizsage.jwt.secret must be set (at least 32 characters)");
    }
    byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
    if (raw.length < 32) {
      throw new IllegalArgumentException("bizsage.jwt.secret must be at least 32 bytes for HS256");
    }
    this.key = Keys.hmacShaKeyFor(raw);
  }

  /** 签发 1 小时有效的标准 JWT。 */
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

  /** 校验 JWT 并提取 Spring Security 需要的身份主体。 */
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
        // role 是后续 authority 映射的必需字段，缺失时不能降级为普通用户。
        throw new IllegalArgumentException("token missing role claim");
      }
      return new JwtPrincipal(claims.getSubject(), Role.valueOf(roleName));
    } catch (JwtException | IllegalArgumentException ex) {
      throw new IllegalArgumentException("invalid token: " + ex.getMessage(), ex);
    }
  }
}
