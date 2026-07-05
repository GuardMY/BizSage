package com.bizsage.api;

import com.bizsage.api.worker.AiWorkerClient;
import com.bizsage.api.worker.AiWorkerException;
import com.bizsage.api.worker.DiagnoseRequest;
import com.bizsage.api.worker.DiagnoseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MessageStreamApiTest {
  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @MockBean
  AiWorkerClient aiWorkerClient;

  private DiagnoseResponse fakeResponse(String answer, String question) {
    return new DiagnoseResponse(
        answer,
        List.of(new DiagnoseResponse.DiagnoseSource(
            "seed-restaurant-cashflow",
            "餐饮门店现金流基础诊断",
            "seed://v1/restaurant-cashflow",
            "seed-baseline",
            0.9,
            null,
            "FREE")),
        "MEDIUM",
        "基于V1静态基线知识和已入库情报生成。",
        "PASSED",
        "免责声明：本诊断仅用于经营分析参考，不构成投资、法律或财务建议。",
        List.of(
            Map.of("category", "PREFERENCE", "key", "response_style", "value", "先给结论再给证据", "confidence", 0.95, "structured", true),
            Map.of("category", "BUSINESS_FACT", "key", "channel_mix", "value", "主要依赖外卖平台", "confidence", 0.90, "structured", true)));
  }

  @BeforeEach
  void setUpWorkerMock() {
    // Default: worker returns a successful diagnosis
    when(aiWorkerClient.diagnose(any(DiagnoseRequest.class)))
        .thenAnswer(invocation -> {
          DiagnoseRequest req = invocation.getArgument(0);
          String answer = "针对「" + req.question() + "」，建议先核对客单价、翻台率、"
              + "食材损耗率、平台佣金、租金占营收比例和现金回款周期。"
              + "餐饮门店现金流基础诊断参考已检索证据。";
          return fakeResponse(answer, req.question());
        });
  }

  @Test
  void conversationMessageStreamReturnsSseDiagnosis() throws Exception {
    String token = login("user");
    long conversationId = createConversation(token);

    String body = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"餐饮门店现金流怎么诊断\"}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    assertThat(body).contains("event: diagnosis");
    assertThat(body).contains("餐饮门店现金流怎么诊断");
    assertThat(body).contains("餐饮门店现金流基础诊断");
    assertThat(body).contains("免责声明");
  }

  @Test
  void conversationMessageStreamPersistsMessagesAndRecallsLongTermMemory() throws Exception {
    String token = login("user");
    Long userId = jdbcTemplate.queryForObject(
        "select id from users where username = ?",
        Long.class,
        "user");
    jdbcTemplate.update(
        """
            insert into user_memory_profiles
              (user_id, memory_category, memory_key, memory_value, status, confidence)
            values (?, 'PREFERENCE', 'response_style', '先给结论再给证据', 'ACTIVE', 0.95)
            """,
        userId);
    jdbcTemplate.update(
        """
            insert into user_memory_profiles
              (user_id, memory_category, memory_key, memory_value, status, confidence)
            values (?, 'BUSINESS_FACT', 'channel_mix', '主要依赖外卖平台', 'ACTIVE', 0.90)
            """,
        userId);

    long conversationId = createConversation(token);

    mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"我最近主要看现金流和库存\"}"))
      .andExpect(status().isOk());

    // Mock the second call to include memory-aware context
    when(aiWorkerClient.diagnose(any(DiagnoseRequest.class)))
        .thenAnswer(invocation -> {
          DiagnoseRequest req = invocation.getArgument(0);
          String answer = "上轮讨论了经营背景。用户偏好：先给结论再给证据。"
              + "渠道：主要依赖外卖平台。针对「" + req.question() + "」，继续分析。";
          return fakeResponse(answer, req.question());
        });

    String followUp = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"继续结合上轮和我的经营背景给建议\"}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    Integer messageCount = jdbcTemplate.queryForObject(
        "select count(*) from messages where conversation_id = ?",
        Integer.class,
        conversationId);

    assertThat(messageCount).isEqualTo(4);
    assertThat(followUp).contains("上轮");
    assertThat(followUp).contains("主要依赖外卖平台");
    assertThat(followUp).contains("先给结论再给证据");
  }

  @Test
  void conversationMessageStreamCreatesSummaryForLongThreads() throws Exception {
    String token = login("user");
    long conversationId = createConversation(token);

    for (int index = 0; index < 4; index++) {
      mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"question\":\"第" + index + "轮问题\"}"))
        .andExpect(status().isOk());
    }

    Integer summaryCount = jdbcTemplate.queryForObject(
        "select count(*) from conversation_summaries where conversation_id = ? and active = true",
        Integer.class,
        conversationId);
    Integer inactiveMessages = jdbcTemplate.queryForObject(
        "select count(*) from messages where conversation_id = ? and is_active_context = false",
        Integer.class,
        conversationId);

    assertThat(summaryCount).isGreaterThan(0);
    assertThat(inactiveMessages).isGreaterThan(0);
  }

  @Test
  void conversationMessageStreamReturnsErrorWhenWorkerFails() throws Exception {
    // Override mock to throw
    when(aiWorkerClient.diagnose(any(DiagnoseRequest.class)))
        .thenThrow(new AiWorkerException("AI worker is not reachable"));

    String token = login("user");
    long conversationId = createConversation(token);

    String body = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"测试问题\"}"))
      .andExpect(status().isOk())  // SSE returns 200 even for errors
      .andReturn()
      .getResponse()
      .getContentAsString();

    // Should receive error event, not diagnosis
    assertThat(body).contains("event: error");
    assertThat(body).contains("WORKER_UNREACHABLE");
    assertThat(body).doesNotContain("event: diagnosis");
  }

  private String login(String username) throws Exception {
    String response = mvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    return objectMapper.readTree(response).at("/data/token").asText();
  }

  private long createConversation(String token) throws Exception {
    String created = mvc.perform(post("/api/conversations")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"诊断\"}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    return objectMapper.readTree(created).at("/data/id").asLong();
  }
}
