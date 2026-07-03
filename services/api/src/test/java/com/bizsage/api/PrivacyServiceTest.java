package com.bizsage.api;

import com.bizsage.api.privacy.PrivacyService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyServiceTest {
  @Test
  void encryptsAndMasksSensitiveValues() {
    PrivacyService privacy = new PrivacyService("0123456789abcdef0123456789abcdef");

    String encrypted = privacy.encrypt("13812345678");

    assertThat(encrypted).isNotEqualTo("13812345678");
    assertThat(privacy.decrypt(encrypted)).isEqualTo("13812345678");
    assertThat(privacy.maskPhone("13812345678")).isEqualTo("138****5678");
    assertThat(privacy.maskIdentity("110101199003071234")).isEqualTo("110101********1234");
  }
}
