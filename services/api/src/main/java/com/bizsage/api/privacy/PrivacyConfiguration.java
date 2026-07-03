package com.bizsage.api.privacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrivacyConfiguration {
  @Bean
  PrivacyService privacyService(
      @Value("${bizsage.privacy.key:0123456789abcdef0123456789abcdef}") String key) {
    return new PrivacyService(key);
  }
}
