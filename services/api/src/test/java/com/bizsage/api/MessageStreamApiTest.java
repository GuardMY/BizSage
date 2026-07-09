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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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

  private DiagnoseResponse fakeResponse(String answer) {
    return new DiagnoseResponse(
        answer,
        List.of(new DiagnoseResponse.DiagnoseSource(
            "seed-restaurant-cashflow",
            "Cashflow baseline",
            "seed://v1/restaurant-cashflow",
            "seed-baseline",
            0.9,
            null,
            "FREE")),
        "MEDIUM",
        "Based on static baseline knowledge.",
        "PASSED",
        "Disclaimer: for business analysis only.",
        List.of(
            Map.of("category", "PREFERENCE", "key", "response_style", "value", "CONCLUSION_FIRST", "confidence", 0.95, "structured", true),
            Map.of("category", "BUSINESS_FACT", "key", "channel_mix", "value", "DELIVERY_PLATFORM_HEAVY", "confidence", 0.90, "structured", true)),
        null,
        null,
        List.of(),
        Map.of());
  }

  @BeforeEach
  void setUpWorkerMock() {
    when(aiWorkerClient.diagnose(any(DiagnoseRequest.class)))
        .thenAnswer(invocation -> {
          DiagnoseRequest req = invocation.getArgument(0);
          String answer = "For " + req.question()
              + ", review revenue, table turns, ingredient loss, platform fees, rent ratio, and collection timing first. "
              + "Cashflow baseline evidence was retrieved.";
          return fakeResponse(answer);
        });
  }

  @Test
  void conversationMessageStreamReturnsSseDiagnosis() throws Exception {
    String token = login("user");
    long conversationId = createConversation(token);

    MvcResult initial = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"cashflow diagnosis\"}"))
      .andExpect(request().asyncStarted())
      .andReturn();

    String body = awaitStreamBody(initial);

    assertThat(body).contains("event: diagnosis");
    assertThat(body.split("event: diagnosis", -1).length).isGreaterThan(2);
    assertThat(body).contains("cashflow diagnosis");
    assertThat(body).contains("Cashflow baseline");
    assertThat(body).contains("Disclaimer");
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
            update user_memory_profiles
               set memory_value = 'CONCLUSION_FIRST', confidence = 0.95
             where user_id = ? and memory_category = 'PREFERENCE'
               and memory_key = 'response_style' and status = 'ACTIVE'
            """,
        userId);
    jdbcTemplate.update(
        """
            insert into user_memory_profiles
              (user_id, memory_category, memory_key, memory_value, status, confidence)
            select ?, 'BUSINESS_FACT', 'channel_mix', 'DELIVERY_PLATFORM_HEAVY', 'ACTIVE', 0.90
             where not exists (
               select 1 from user_memory_profiles
                where user_id = ? and memory_category = 'BUSINESS_FACT'
                  and memory_key = 'channel_mix' and status = 'ACTIVE'
             )
            """,
        userId,
        userId);
    jdbcTemplate.update(
        """
            update user_memory_profiles
               set memory_value = 'DELIVERY_PLATFORM_HEAVY', confidence = 0.90
             where user_id = ? and memory_category = 'BUSINESS_FACT'
               and memory_key = 'channel_mix' and status = 'ACTIVE'
            """,
        userId);

    long conversationId = createConversation(token);

    MvcResult firstPass = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"focus on cashflow and inventory\"}"))
      .andExpect(request().asyncStarted())
      .andReturn();
    awaitStreamBody(firstPass);

    when(aiWorkerClient.diagnose(any(DiagnoseRequest.class)))
        .thenAnswer(invocation -> {
          DiagnoseRequest req = invocation.getArgument(0);
          String answer = "We discussed the operating context already. Preference: CONCLUSION_FIRST. "
              + "Channel mix: DELIVERY_PLATFORM_HEAVY. Continue with " + req.question() + ".";
          return fakeResponse(answer);
        });

    MvcResult followUpResult = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"continue with the prior business context\"}"))
      .andExpect(request().asyncStarted())
      .andReturn();

    String followUp = awaitStreamBody(followUpResult);

    Integer messageCount = jdbcTemplate.queryForObject(
        "select count(*) from messages where conversation_id = ?",
        Integer.class,
        conversationId);

    assertThat(messageCount).isEqualTo(4);
    assertThat(followUp).contains("CONCLUSION_FIRST");
    assertThat(followUp).contains("DELIVERY_PLATFORM_HEAVY");
    assertThat(followUp).contains("prior business context");
  }

  @Test
  void conversationMessageStreamCreatesSummaryForLongThreads() throws Exception {
    String token = login("user");
    long conversationId = createConversation(token);

    for (int index = 0; index < 4; index++) {
      MvcResult iteration = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"question\":\"round-" + index + "\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();
      awaitStreamBody(iteration);
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
  void conversationMessageStreamKeepsRollingSummaryHistoryInSingleActiveRecord() throws Exception {
    String token = login("user");
    long conversationId = createConversation(token);

    for (int index = 0; index < 6; index++) {
      MvcResult iteration = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"question\":\"round-" + index + "\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();
      awaitStreamBody(iteration);
    }

    Integer activeSummaryCount = jdbcTemplate.queryForObject(
        "select count(*) from conversation_summaries where conversation_id = ? and active = true",
        Integer.class,
        conversationId);
    Integer totalSummaryCount = jdbcTemplate.queryForObject(
        "select count(*) from conversation_summaries where conversation_id = ?",
        Integer.class,
        conversationId);
    String latestSummary = jdbcTemplate.queryForObject(
        """
            select summary_text
            from conversation_summaries
            where conversation_id = ? and active = true
            """,
        String.class,
        conversationId);

    assertThat(activeSummaryCount).isEqualTo(1);
    assertThat(totalSummaryCount).isGreaterThan(1);
    assertThat(latestSummary).contains("USER:round-0");
    assertThat(latestSummary).contains("USER:round-1");
  }

  @Test
  void conversationMessageStreamKeepsUnstructuredMemoryInMysqlWithoutCreatingVectorEmbeddings() throws Exception {
    when(aiWorkerClient.diagnose(any(DiagnoseRequest.class)))
        .thenReturn(new DiagnoseResponse(
            "Narrative memory stored in MySQL only.",
            List.of(),
            "MEDIUM",
            "Based on static baseline knowledge.",
            "PASSED",
            "Disclaimer: for business analysis only.",
            List.of(
                Map.of(
                    "category", "PAIN_POINT",
                    "key", "cashflow_story",
                    "value", "The user repeatedly described supplier prepayment pressure.",
                    "confidence", 0.88,
                    "structured", false)),
            null,
            null,
            List.of(),
            Map.of()));

    String token = login("user");
    long conversationId = createConversation(token);

    MvcResult result = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"suppliers keep asking for prepayment\"}"))
      .andExpect(request().asyncStarted())
      .andReturn();
    awaitStreamBody(result);

    Integer profileCount = jdbcTemplate.queryForObject(
        "select count(*) from user_memory_profiles where memory_key = 'cashflow_story'",
        Integer.class);
    Integer embeddingCount = jdbcTemplate.queryForObject(
        "select count(*) from user_memory_embeddings",
        Integer.class);

    assertThat(profileCount).isEqualTo(1);
    assertThat(embeddingCount).isEqualTo(0);
  }

  @Test
  void conversationMessageStreamRefreshesExistingMemoryInsteadOfCreatingDuplicateActiveRows() throws Exception {
    when(aiWorkerClient.diagnose(any(DiagnoseRequest.class)))
        .thenReturn(new DiagnoseResponse(
            "Updated preference memory.",
            List.of(),
            "MEDIUM",
            "Based on static baseline knowledge.",
            "PASSED",
            "Disclaimer: for business analysis only.",
            List.of(
                Map.of(
                    "category", "PREFERENCE",
                    "key", "response_style",
                    "value", "CONCLUSION_FIRST",
                    "confidence", 0.81,
                    "structured", true)),
            null,
            null,
            List.of(),
            Map.of()));

    String token = login("user");
    Long userId = jdbcTemplate.queryForObject(
        "select id from users where username = ?",
        Long.class,
        "user");
    jdbcTemplate.update(
        """
            update user_memory_profiles
               set memory_value = 'CONCISE', confidence = 0.65
             where user_id = ? and memory_category = 'PREFERENCE'
               and memory_key = 'response_style' and status = 'ACTIVE'
            """,
        userId);
    long conversationId = createConversation(token);

    MvcResult result = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"remember that I want the answer conclusion-first\"}"))
      .andExpect(request().asyncStarted())
      .andReturn();
    awaitStreamBody(result);

    Integer profileCount = jdbcTemplate.queryForObject(
        """
            select count(*)
            from user_memory_profiles
            where user_id = ? and memory_category = 'PREFERENCE'
              and memory_key = 'response_style' and status = 'ACTIVE'
            """,
        Integer.class,
        userId);
    String memoryValue = jdbcTemplate.queryForObject(
        """
            select memory_value
            from user_memory_profiles
            where user_id = ? and memory_category = 'PREFERENCE'
              and memory_key = 'response_style' and status = 'ACTIVE'
            """,
        String.class,
        userId);

    assertThat(profileCount).isEqualTo(1);
    assertThat(memoryValue).isEqualTo("CONCLUSION_FIRST");
  }

  @Test
  void conversationMessageStreamReturnsErrorWhenWorkerFails() throws Exception {
    when(aiWorkerClient.diagnose(any(DiagnoseRequest.class)))
        .thenThrow(new AiWorkerException("AI worker is not reachable"));

    String token = login("user");
    long conversationId = createConversation(token);

    MvcResult initial = mvc.perform(post("/api/conversations/" + conversationId + "/messages/stream")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"question\":\"test prompt\"}"))
      .andExpect(request().asyncStarted())
      .andReturn();

    String body = awaitStreamBody(initial);

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
        .content("{\"title\":\"Diagnosis\"}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    return objectMapper.readTree(created).at("/data/id").asLong();
  }

  private String awaitStreamBody(MvcResult result) throws Exception {
    result.getAsyncResult();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return result.getResponse().getContentAsString();
  }
}
