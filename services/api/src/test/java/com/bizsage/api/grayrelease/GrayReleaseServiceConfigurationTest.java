package com.bizsage.api.grayrelease;

import com.bizsage.api.auth.Role;
import com.bizsage.api.users.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GrayReleaseServiceConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
      .withBean(GrayReleaseProperties.class)
      .withBean(GrayReleaseService.class);

  @Test
  void standardMembershipListsBindWithoutSpelParsing() {
    contextRunner
        .withPropertyValues(
            "bizsage.gray-release.features.paid-intelligence.enabled=true",
            "bizsage.gray-release.features.paid-intelligence.traffic-percent=10",
            "bizsage.gray-release.features.paid-intelligence.allowed-memberships[0]=SEED_PAID",
            "bizsage.gray-release.features.paid-intelligence.allowed-memberships[1]=INTERNAL",
            "bizsage.gray-release.features.pdf-export.enabled=true",
            "bizsage.gray-release.features.pdf-export.traffic-percent=10",
            "bizsage.gray-release.features.pdf-export.allowed-memberships[0]=SEED_PAID",
            "bizsage.gray-release.features.pdf-export.allowed-memberships[1]=INTERNAL",
            "bizsage.gray-release.features.advanced-rag.enabled=false",
            "bizsage.gray-release.features.advanced-rag.traffic-percent=0")
        .run(context -> {
          assertThat(context).hasSingleBean(GrayReleaseService.class);
          GrayReleaseService service = context.getBean(GrayReleaseService.class);

          UserAccount paidUser = new UserAccount(
              1L, "seed_paid", "password", Role.USER, null, null,
              "cn-default", "general", "SEED_PAID", "cashflow,inventory");
          UserAccount freeUser = new UserAccount(
              2L, "user", "password", Role.USER, null, null,
              "cn-default", "general", "FREE", "cashflow");

          assertThat(service.isFeatureEnabled(GrayReleaseService.FEATURE_PAID_INTELLIGENCE, paidUser))
              .isTrue();
          assertThat(service.getTrafficPercentage(GrayReleaseService.FEATURE_PAID_INTELLIGENCE))
              .isEqualTo(10);
          assertThat(service.isFeatureEnabled(GrayReleaseService.FEATURE_ADVANCED_RAG, freeUser))
              .isFalse();
        });
  }
}
