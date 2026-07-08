package com.bizsage.api.privacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrivacyConfiguration {
  @Bean
  PrivacyService privacyService(
      @Value("${bizsage.privacy.key}") String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("bizsage.privacy.key must be set (32 UTF-8 bytes)");
    }
    return new PrivacyService(key);
  }
}
