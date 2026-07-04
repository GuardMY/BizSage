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

    String response = mvc.perform(post("/api/conversations")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"门店现金流诊断\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("OK"))
      .andExpect(jsonPath("$.data.title").value("门店现金流诊断"))
      .andExpect(jsonPath("$.data.status").value("ACTIVE"))
      .andReturn()
      .getResponse()
      .getContentAsString();

    long conversationId = objectMapper.readTree(response).at("/data/id").asLong();

    mvc.perform(get("/api/conversations").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[?(@.title == '门店现金流诊断')]").isNotEmpty());

    mvc.perform(post("/api/conversations/" + conversationId + "/archive").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
  }

  @Test
  void operatorCanCreateAndApproveIntelligence() throws Exception {
    String token = login("operator");

    String response = mvc.perform(post("/api/intelligence")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "title":"本地餐饮平台佣金调整",
            "content":"本地餐饮平台佣金出现调整，需要结合商圈与客单结构评估。",
            "url":"https://example.com/news/1",
            "industryId":"general",
            "regionId":"cn-default",
            "linkId":"channel",
            "sourceId":"manual-local"
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("PENDING"))
      .andReturn()
      .getResponse()
      .getContentAsString();

    long id = objectMapper.readTree(response).at("/data/id").asLong();

    mvc.perform(post("/api/intelligence/" + id + "/approve").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("APPROVED"));

    mvc.perform(get("/api/intelligence").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[?(@.title == '本地餐饮平台佣金调整')]").isNotEmpty());
  }

  @Test
  void operatorCanImportKnowledgeMetadata() throws Exception {
    String token = login("operator");

    mvc.perform(post("/api/knowledge/import")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "title":"库存周转诊断",
            "content":"库存周转天数过高会压占现金流。",
            "industryId":"general",
            "regionId":"cn-default",
            "linkId":"warehouse",
            "sourceId":"manual-baseline"
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.title").value("库存周转诊断"));
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
}
