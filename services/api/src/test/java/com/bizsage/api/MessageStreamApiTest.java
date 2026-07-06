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
            Map.of("category", "BUSINESS_FACT", "key", "channel_mix", "value", "DELIVERY_PLATFORM_HEAVY", "confidence", 0.90, "structured", true)));
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
            insert into user_memory_profiles
              (user_id, memory_category, memory_key, memory_value, status, confidence)
            values (?, 'PREFERENCE', 'response_style', 'CONCLUSION_FIRST', 'ACTIVE', 0.95)
            """,
        userId);
    jdbcTemplate.update(
        """
            insert into user_memory_profiles
              (user_id, memory_category, memory_key, memory_value, status, confidence)
            values (?, 'BUSINESS_FACT', 'channel_mix', 'DELIVERY_PLATFORM_HEAVY', 'ACTIVE', 0.90)
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
