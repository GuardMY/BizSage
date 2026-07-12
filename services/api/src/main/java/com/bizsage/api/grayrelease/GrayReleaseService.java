package com.bizsage.api.grayrelease;

import com.bizsage.api.users.UserAccount;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  public static final String FEATURE_PDF_EXPORT = "pdf-export";
  public static final String FEATURE_ADVANCED_RAG = "advanced-rag";

  private final Map<String, Boolean> featureEnabled;
  private final Map<String, Integer> featureTrafficPercent;
  private final Map<String, List<String>> featureAllowedMemberships;

  public GrayReleaseService(GrayReleaseProperties properties) {
    GrayReleaseProperties.Features features = properties.getFeatures();
    GrayReleaseProperties.FeatureConfig pdfExport = features.getPdfExport();
    GrayReleaseProperties.FeatureConfig advancedRag = features.getAdvancedRag();

    this.featureEnabled = Map.of(
        FEATURE_PDF_EXPORT, pdfExport.isEnabled(),
        FEATURE_ADVANCED_RAG, advancedRag.isEnabled());

    this.featureTrafficPercent = Map.of(
        FEATURE_PDF_EXPORT, pdfExport.getTrafficPercent(),
        FEATURE_ADVANCED_RAG, advancedRag.getTrafficPercent());

    this.featureAllowedMemberships = Map.of(
        FEATURE_PDF_EXPORT, pdfExport.getAllowedMemberships(),
        FEATURE_ADVANCED_RAG, advancedRag.getAllowedMemberships());
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
