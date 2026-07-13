package com.bizsage.api.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationDiagnosisMemoryStore {
  private final ConversationDiagnosisMemoryMapper mapper;

  public ConversationDiagnosisMemoryStore(ConversationDiagnosisMemoryMapper mapper) {
    this.mapper = mapper;
  }

  public List<ConversationDiagnosisMemory> activeForConversation(long conversationId) {
    try {
      return mapper.selectList(new LambdaQueryWrapper<ConversationDiagnosisMemory>()
          .eq(ConversationDiagnosisMemory::getConversationId, conversationId)
          .eq(ConversationDiagnosisMemory::getStatus, "ACTIVE")
          .orderByDesc(ConversationDiagnosisMemory::getConfidence));
    } catch (RuntimeException ignored) {
      return List.of();
    }
  }

  @Transactional
  public void saveAll(long conversationId, long sourceMessageId, List<Map<String, Object>> candidates) {
    if (candidates == null) return;
    try {
      for (Map<String, Object> candidate : candidates) {
      String category = stringValue(candidate.get("category"), "BUSINESS_FACT");
      String key = stringValue(candidate.get("key"), "unknown");
      String value = stringValue(candidate.get("value"), "");
      if (value.isBlank()) continue;
      double confidence = numberValue(candidate.get("confidence"), 0.5);
      boolean structured = Boolean.TRUE.equals(candidate.get("structured"));
      int updated = mapper.update(null, new LambdaUpdateWrapper<ConversationDiagnosisMemory>()
          .eq(ConversationDiagnosisMemory::getConversationId, conversationId)
          .eq(ConversationDiagnosisMemory::getCategory, category)
          .eq(ConversationDiagnosisMemory::getMemoryKey, key)
          .eq(ConversationDiagnosisMemory::getStatus, "ACTIVE")
          .set(ConversationDiagnosisMemory::getMemoryValue, value)
          .set(ConversationDiagnosisMemory::getConfidence, confidence)
          .set(ConversationDiagnosisMemory::getStructured, structured)
          .set(ConversationDiagnosisMemory::getSourceMessageId, sourceMessageId));
      if (updated == 0) {
        ConversationDiagnosisMemory memory = new ConversationDiagnosisMemory();
        memory.setConversationId(conversationId);
        memory.setCategory(category);
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        memory.setConfidence(confidence);
        memory.setStructured(structured);
        memory.setSourceMessageId(sourceMessageId);
        memory.setStatus("ACTIVE");
        mapper.insert(memory);
      }
      }
    } catch (RuntimeException ignored) {
      // Older test databases may not have V2 migration enabled; diagnosis remains usable.
    }
  }

  public List<Map<String, Object>> asMaps(long conversationId) {
    return activeForConversation(conversationId).stream().map(memory -> {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("category", memory.getCategory());
      map.put("key", memory.getMemoryKey());
      map.put("value", memory.getMemoryValue());
      map.put("confidence", memory.getConfidence());
      map.put("structured", memory.getStructured());
      map.put("source", "mysql:conversation_diagnosis_memories");
      return map;
    }).toList();
  }

  private static String stringValue(Object value, String fallback) {
    return value instanceof String text && !text.isBlank() ? text : fallback;
  }

  private static double numberValue(Object value, double fallback) {
    return value instanceof Number number ? Math.max(0, Math.min(1, number.doubleValue())) : fallback;
  }
}
