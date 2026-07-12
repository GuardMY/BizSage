package com.bizsage.api;

import com.bizsage.api.worker.AiWorkerClient;
import com.bizsage.api.worker.DiagnoseRequest;
import com.bizsage.api.worker.DiagnoseResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class V2GrayReleaseApiTest {
  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper objectMapper;

  @MockBean
  AiWorkerClient aiWorkerClient;

  @BeforeEach
  void setUpWorkerMock() {
    when(aiWorkerClient.diagnose(any(DiagnoseRequest.class)))
        .thenAnswer(invocation -> new DiagnoseResponse(
            "V2 operating diagnosis report for gray release. Evidence is filtered by user entitlement.",
            List.of(new DiagnoseResponse.DiagnoseSource(
                "seed-restaurant-cashflow",
                "Restaurant cashflow baseline",
                "seed://v1/restaurant-cashflow",
                "seed-baseline",
                0.9,
                null,
                "FREE")),
            "MEDIUM",
            "fresh",
            "PASSED",
            "Disclaimer: This report is for operational analysis only and is not legal, financial, or investment advice.",
            List.of(),
            "gray-release",
            "gray-release-chain",
            List.<String>of(),
            java.util.Map.<String, String>of(),
            List.<java.util.Map<String, Object>>of(),
            null,
            List.<String>of(),
            null,
            List.<String>of(),
            null,
            List.<java.util.Map<String, Object>>of()));
  }

  @Test
  void loginIncludesV2ProfileFieldsForSeedPaidUser() throws Exception {
    String response = mvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"seed_paid\",\"password\":\"password\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.membershipLevel").value("SEED_PAID"))
      .andExpect(jsonPath("$.data.consultationPreferences").value("cashflow,inventory"))
      .andExpect(jsonPath("$.data.preferredLocale").value("zh-CN"))
      .andReturn()
      .getResponse()
      .getContentAsString();

    JsonNode data = objectMapper.readTree(response).at("/data");
    assertThat(data.at("/regionId").asText()).isEqualTo("cn-default");
    assertThat(data.at("/industryId").asText()).isEqualTo("general");
  }

  @Test
  void userCanPersistPreferredLocale() throws Exception {
    String token = login("seed_paid");

    mvc.perform(put("/api/users/me/locale")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"preferredLocale\":\"en\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.preferredLocale").value("en"));

    mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.preferredLocale").value("en"));

    mvc.perform(put("/api/users/me/locale")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"preferredLocale\":\"zh-CN\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.preferredLocale").value("zh-CN"));
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
