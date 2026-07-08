package com.bizsage.api.grayrelease;

import com.bizsage.api.users.UserAccount;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * V2: Gray-release feature-flag service.
 *
 * <p>Controls whether a specific feature is enabled for a given user based on:
 * <ul>
 *   <li>Global feature enable/disable flag</li>
 *   <li>Traffic percentage (hash-based stable assignment)</li>
 *   <li>Allowed membership levels</li>
 * </ul>
 *
 * <p>Features are configured in {@code application.yml} under
 * {@code bizsage.gray-release.features.*}.
 */
@Service
public class GrayReleaseService {

  private static final Logger log = LoggerFactory.getLogger(GrayReleaseService.class);

  /** Feature keys for the three V2 guarded features. */
  public static final String FEATURE_PAID_INTELLIGENCE = "paid-intelligence";
  public static final String FEATURE_PDF_EXPORT = "pdf-export";
  public static final String FEATURE_ADVANCED_RAG = "advanced-rag";

  private final Map<String, Boolean> featureEnabled;
  private final Map<String, Integer> featureTrafficPercent;
  private final Map<String, List<String>> featureAllowedMemberships;

  @SuppressWarnings("unchecked")
  public GrayReleaseService(
      @Value("#{${bizsage.gray-release.features.paid-intelligence.enabled:true}}") boolean paidIntelEnabled,
      @Value("#{${bizsage.gray-release.features.paid-intelligence.traffic-percent:10}}") int paidIntelTraffic,
      @Value("#{${bizsage.gray-release.features.paid-intelligence.allowed-memberships:{'SEED_PAID','INTERNAL'}}}")
          List<String> paidIntelMemberships,
      @Value("#{${bizsage.gray-release.features.pdf-export.enabled:true}}") boolean pdfExportEnabled,
      @Value("#{${bizsage.gray-release.features.pdf-export.traffic-percent:10}}") int pdfExportTraffic,
      @Value("#{${bizsage.gray-release.features.pdf-export.allowed-memberships:{'SEED_PAID','INTERNAL'}}}")
          List<String> pdfExportMemberships,
      @Value("#{${bizsage.gray-release.features.advanced-rag.enabled:false}}") boolean advancedRagEnabled,
      @Value("#{${bizsage.gray-release.features.advanced-rag.traffic-percent:0}}") int advancedRagTraffic,
      @Value("#{${bizsage.gray-release.features.advanced-rag.allowed-memberships:{}}}")
          List<String> advancedRagMemberships) {

    this.featureEnabled = Map.of(
        FEATURE_PAID_INTELLIGENCE, paidIntelEnabled,
        FEATURE_PDF_EXPORT, pdfExportEnabled,
        FEATURE_ADVANCED_RAG, advancedRagEnabled);

    this.featureTrafficPercent = Map.of(
        FEATURE_PAID_INTELLIGENCE, paidIntelTraffic,
        FEATURE_PDF_EXPORT, pdfExportTraffic,
        FEATURE_ADVANCED_RAG, advancedRagTraffic);

    this.featureAllowedMemberships = Map.of(
        FEATURE_PAID_INTELLIGENCE, List.copyOf(paidIntelMemberships),
        FEATURE_PDF_EXPORT, List.copyOf(pdfExportMemberships),
        FEATURE_ADVANCED_RAG, List.copyOf(advancedRagMemberships));
  }

  /**
   * Check whether a feature is enabled for the given user.
   *
   * <p>Logic: feature must be globally enabled AND the user must either be
   * in an allowed membership level OR fall within the traffic percentage
   * (determined by a stable hash of the user's ID).
   */
  public boolean isFeatureEnabled(String feature, UserAccount user) {
    Boolean enabled = featureEnabled.getOrDefault(feature, false);
    if (!enabled) {
      return false;
    }

    List<String> allowedMemberships = featureAllowedMemberships.getOrDefault(feature, List.of());
    if (allowedMemberships.contains(user.membershipLevel())) {
      return true;
    }

    // Traffic-percentage based rollout: stable hash of (user.id, feature)
    int trafficPercent = featureTrafficPercent.getOrDefault(feature, 0);
    if (trafficPercent <= 0) {
      return false;
    }

    int bucket = Math.abs((feature + user.id()).hashCode()) % 100;
    boolean inTraffic = bucket < trafficPercent;
    if (inTraffic) {
      log.debug("User {} in {}% traffic cohort for feature '{}' (bucket {})",
          user.username(), trafficPercent, feature, bucket);
    }
    return inTraffic;
  }

  /** Return the configured traffic percentage for a feature. */
  public int getTrafficPercentage(String feature) {
    return featureTrafficPercent.getOrDefault(feature, 0);
  }
}
