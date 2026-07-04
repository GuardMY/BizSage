package com.bizsage.api.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DiagnosisService {
  private final ObjectMapper objectMapper;

  public DiagnosisService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String diagnose(String question) {
    Map<String, Object> payload = Map.of(
        "answer", "针对「" + question + "」，V1 诊断建议先围绕已检索证据核对关键经营变量。证据显示：餐饮门店应核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期。",
        "sources", List.of(Map.of(
            "id", "seed-restaurant-cashflow",
            "title", "餐饮门店现金流基础诊断",
            "sourceUrl", "seed://v1/restaurant-cashflow",
            "sourceId", "seed-baseline",
            "confidence", 0.9)),
        "confidence", "MEDIUM",
        "timeliness", "基于V1静态基线知识和已入库情报生成。",
        "disclaimer", "免责声明：本诊断仅用于经营分析参考，不构成投资、法律或财务建议。");
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("diagnosis serialization failed", exception);
    }
  }
}
