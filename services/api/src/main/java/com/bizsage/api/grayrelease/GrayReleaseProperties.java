package com.bizsage.api.grayrelease;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Native Spring Boot binding for gray-release configuration.
 *
 * <p>Keeps the YAML source in standard list/object form instead of relying on
 * SpEL literals embedded in property values.
 */
@Component
@ConfigurationProperties(prefix = "bizsage.gray-release")
public class GrayReleaseProperties {

  private String cohort = "internal-operators-and-seed-paid-users";
  private String supportWindow = "09:00-21:00 Asia/Shanghai";
  private Features features = new Features();

  public String getCohort() {
    return cohort;
  }

  public void setCohort(String cohort) {
    this.cohort = cohort;
  }

  public String getSupportWindow() {
    return supportWindow;
  }

  public void setSupportWindow(String supportWindow) {
    this.supportWindow = supportWindow;
  }

  public Features getFeatures() {
    return features;
  }

  public void setFeatures(Features features) {
    this.features = features;
  }

  public static class Features {
    private FeatureConfig paidIntelligence = new FeatureConfig(true, 10, List.of("SEED_PAID", "INTERNAL"));
    private FeatureConfig pdfExport = new FeatureConfig(true, 10, List.of("SEED_PAID", "INTERNAL"));
    private FeatureConfig advancedRag = new FeatureConfig(false, 0, List.of());

    public FeatureConfig getPaidIntelligence() {
      return paidIntelligence;
    }

    public void setPaidIntelligence(FeatureConfig paidIntelligence) {
      this.paidIntelligence = paidIntelligence;
    }

    public FeatureConfig getPdfExport() {
      return pdfExport;
    }

    public void setPdfExport(FeatureConfig pdfExport) {
      this.pdfExport = pdfExport;
    }

    public FeatureConfig getAdvancedRag() {
      return advancedRag;
    }

    public void setAdvancedRag(FeatureConfig advancedRag) {
      this.advancedRag = advancedRag;
    }
  }

  public static class FeatureConfig {
    private boolean enabled;
    private int trafficPercent;
    private List<String> allowedMemberships = List.of();

    public FeatureConfig() {}

    public FeatureConfig(boolean enabled, int trafficPercent, List<String> allowedMemberships) {
      this.enabled = enabled;
      this.trafficPercent = trafficPercent;
      this.allowedMemberships = List.copyOf(allowedMemberships);
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getTrafficPercent() {
      return trafficPercent;
    }

    public void setTrafficPercent(int trafficPercent) {
      this.trafficPercent = trafficPercent;
    }

    public List<String> getAllowedMemberships() {
      return allowedMemberships;
    }

    public void setAllowedMemberships(List<String> allowedMemberships) {
      this.allowedMemberships = allowedMemberships == null ? List.of() : List.copyOf(allowedMemberships);
    }
  }
}
