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
      new Definition("construction", "建筑工程"),
      new Definition("real-estate", "房地产"),
      new Definition("tourism-hotel", "旅游与酒店"),
      new Definition("agriculture", "农业"),
      new Definition("food-processing", "食品加工"),
      new Definition("textile", "服装纺织"),
      new Definition("automotive", "汽车及零部件"),
      new Definition("home-building", "家居建材"),
      new Definition("beauty", "美容美发"),
      new Definition("fitness", "健身与运动"),
      new Definition("culture-media", "文化传媒"),
      new Definition("software-internet", "软件与互联网"),
      new Definition("financial-services", "金融服务"),
      new Definition("legal-services", "法律服务"),
      new Definition("human-resources", "人力资源"),
      new Definition("advertising-marketing", "广告营销"),
      new Definition("property-management", "物业管理"),
      new Definition("housekeeping", "家政服务"),
      new Definition("auto-service", "汽车维修与服务"),
      new Definition("pharma-biotech", "医药与生物科技"),
      new Definition("energy-environment", "能源与环保"),
      new Definition("insurance", "保险"),
      new Definition("banking-payment", "银行与支付"),
      new Definition("securities-investment", "证券与投资"),
      new Definition("telecom", "通信服务"),
      new Definition("electronics", "电子信息"),
      new Definition("semiconductor", "半导体"),
      new Definition("chemical", "化工"),
      new Definition("new-energy", "新能源"),
      new Definition("mining", "矿业"),
      new Definition("supply-chain", "供应链服务"),
      new Definition("import-export", "进出口贸易"),
      new Definition("aviation", "航空"),
      new Definition("shipping", "航运"),
      new Definition("express-delivery", "快递"),
      new Definition("printing-packaging", "印刷包装"),
      new Definition("jewelry", "珠宝首饰"),
      new Definition("maternal-child", "母婴服务"),
      new Definition("pet-services", "宠物服务"),
      new Definition("elderly-care", "养老服务"),
      new Definition("funeral-services", "殡葬服务"));

  public static boolean isPreset(String industryId) {
    return PRESET.stream().anyMatch(item -> item.id().equals(industryId));
  }
}
