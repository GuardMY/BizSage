package com.bizsage.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthAndRbacTest {
  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper objectMapper;

  @Test
  void loginReturnsUnifiedEnvelopeWithToken() throws Exception {
    String body = """
      {"username":"user","password":"password"}
      """;

    String response = mvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("OK"))
      .andExpect(jsonPath("$.requestId").exists())
      .andExpect(jsonPath("$.data.token").isNotEmpty())
      .andReturn()
      .getResponse()
      .getContentAsString();

    JsonNode json = objectMapper.readTree(response);
    assertThat(json.at("/data/role").asText()).isEqualTo("USER");
    assertThat(json.at("/data/username").asText()).isEqualTo("user");
  }

  @Test
  void authenticatedUserCanReadOwnProfile() throws Exception {
    String token = login("user");

    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("OK"))
      .andExpect(jsonPath("$.data.username").value("user"))
      .andExpect(jsonPath("$.data.membershipLevel").value("FREE"));
  }

  @Test
  void invalidTokenReturnsUnauthorizedEnvelope() throws Exception {
    mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid.token"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
      .andExpect(jsonPath("$.message").value("authentication required"))
      .andExpect(jsonPath("$.requestId").isNotEmpty());
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
