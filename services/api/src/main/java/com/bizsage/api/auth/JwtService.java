package com.bizsage.api.auth;

import com.bizsage.api.users.UserAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final String secret;

  public JwtService(@Value("${bizsage.jwt.secret:change-me-v1-dev-secret-change-me}") String secret) {
    this.secret = secret;
  }

  public String issue(UserAccount user) {
    long expiresAt = Instant.now().plusSeconds(3600).getEpochSecond();
    String payload = user.username() + "|" + user.role().name() + "|" + expiresAt;
    String encodedPayload = encode(payload);
    return encodedPayload + "." + sign(encodedPayload);
  }

  public JwtPrincipal verify(String token) {
    if (token == null || !token.contains(".")) {
      throw new IllegalArgumentException("invalid token");
    }
    String[] parts = token.split("\\.", 2);
    if (!sign(parts[0]).equals(parts[1])) {
      throw new IllegalArgumentException("invalid token signature");
    }
    String[] payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|");
    if (payload.length != 3) {
      throw new IllegalArgumentException("invalid token payload");
    }
    if (Long.parseLong(payload[2]) < Instant.now().getEpochSecond()) {
      throw new IllegalArgumentException("token expired");
    }
    return new JwtPrincipal(payload[0], Role.valueOf(payload[1]));
  }

  private String encode(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private String sign(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("token signing failed", exception);
    }
  }
}
