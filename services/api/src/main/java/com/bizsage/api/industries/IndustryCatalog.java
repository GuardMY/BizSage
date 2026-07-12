package com.bizsage.api.industries;

import java.util.List;

public final class IndustryCatalog {
  private IndustryCatalog() {}

  public record Definition(String id, String name) {}

  public static final List<Definition> PRESET = List.of(
      new Definition("general", "通用企业"),
      new Definition("catering", "餐饮"),
      new Definition("retail", "零售"),
      new Definition("manufacturing", "制造"),
      new Definition("wholesale", "批发"),
      new Definition("ecommerce", "电商"),
      new Definition("logistics", "物流"),
      new Definition("professional-services", "专业服务"),
      new Definition("education", "教育培训"),
      new Definition("healthcare", "医疗健康"),
      new Definition("construction", "建筑工程"));

  public static boolean isPreset(String industryId) {
    return PRESET.stream().anyMatch(item -> item.id().equals(industryId));
  }
}
