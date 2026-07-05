package com.bizsage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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

  @Test
  void conversationMessageStreamReturnsSseDiagnosis() throws Exception {
    String token = login("user");
    long conversationId = createConversation(token);

    String body = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"椁愰ギ闂ㄥ簵鐜伴噾娴佹€庝箞璇婃柇\"}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();

    assertThat(body).contains("event: diagnosis");
    assertThat(body).contains("椁愰ギ闂ㄥ簵鐜伴噾娴佹€庝箞璇婃柇");
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
        .content("{\"title\":\"璇婃柇\"}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    return objectMapper.readTree(created).at("/data/id").asLong();
  }
}
