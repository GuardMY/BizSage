package com.bizsage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BusinessWorkflowApiTest {
  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper objectMapper;

  @Test
  void userCanCreateAndArchiveConversation() throws Exception {
    String token = login("user");

    long conversationId = createConversation(token, "Cashflow diagnosis");

    mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[?(@.title == 'Cashflow diagnosis')]").isNotEmpty());

    mvc.perform(post("/api/conversations/" + conversationId + "/archive").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
  }

  @Test
  void archivedConversationCanBeSoftDeletedAndDisappearsFromList() throws Exception {
    String token = login("user");
    long conversationId = createConversation(token, "Archive and delete");

    mvc.perform(post("/api/conversations/" + conversationId + "/archive").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

    mvc.perform(post("/api/conversations/" + conversationId + "/delete").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("DELETED"));

    mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[?(@.id == " + conversationId + ")]").isEmpty());
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

  private long createConversation(String token, String title) throws Exception {
    String response = mvc.perform(post("/api/conversations")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"" + title + "\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("OK"))
      .andExpect(jsonPath("$.data.title").value(title))
      .andExpect(jsonPath("$.data.status").value("ACTIVE"))
      .andReturn()
      .getResponse()
      .getContentAsString();

    return objectMapper.readTree(response).at("/data/id").asLong();
  }
}
