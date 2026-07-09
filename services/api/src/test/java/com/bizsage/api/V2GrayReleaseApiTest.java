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
            "Generated from V2 gray-release knowledge and approved intelligence snapshots.",
            "PASSED",
            "Disclaimer: This report is for operational analysis only and is not legal, financial, or investment advice.",
            List.of(),
            null,
            null,
            List.of(),
            java.util.Map.of()));
  }

  @Test
  void loginIncludesV2ProfileFieldsForSeedPaidUser() throws Exception {
    String response = mvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"seed_paid\",\"password\":\"password\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.membershipLevel").value("SEED_PAID"))
      .andExpect(jsonPath("$.data.consultationPreferences").value("cashflow,inventory"))
      .andReturn()
      .getResponse()
      .getContentAsString();

    JsonNode data = objectMapper.readTree(response).at("/data");
    assertThat(data.at("/regionId").asText()).isEqualTo("cn-default");
    assertThat(data.at("/industryId").asText()).isEqualTo("general");
  }

  @Test
  void paidIntelligenceIsStoredSeparatelyAndHiddenFromFreeUsers() throws Exception {
    String operatorToken = login("operator");
    String paidToken = login("seed_paid");
    String freeToken = login("user");

    long paidId = createPaidIntelligence(operatorToken, "Seed paid rent benchmark", "rent");

    mvc.perform(post("/api/paid-intelligence/" + paidId + "/approve")
        .header("Authorization", "Bearer " + operatorToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("APPROVED"));

    mvc.perform(get("/api/paid-intelligence").header("Authorization", "Bearer " + freeToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data").isEmpty());

    mvc.perform(get("/api/paid-intelligence").header("Authorization", "Bearer " + paidToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[?(@.title == 'Seed paid rent benchmark')]").isNotEmpty());
  }

  @Test
  void diagnosisReportExcludesPaidEvidenceForFreeUsersAndIncludesItForPaidUsers() throws Exception {
    String operatorToken = login("operator");
    String paidToken = login("seed_paid");
    String freeToken = login("user");

    long paidId = createPaidIntelligence(operatorToken, "Paid margin warning", "margin");
    mvc.perform(post("/api/paid-intelligence/" + paidId + "/approve")
        .header("Authorization", "Bearer " + operatorToken))
      .andExpect(status().isOk());

    mvc.perform(get("/api/reports/diagnosis")
        .header("Authorization", "Bearer " + freeToken)
        .param("question", "cashflow"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.format").value("PDF"))
      .andExpect(jsonPath("$.data.sources[?(@.entitlement == 'PAID')]").isEmpty())
      .andExpect(jsonPath("$.data.selfCheckStatus").value("PASSED"))
      .andExpect(jsonPath("$.data.disclaimer").isNotEmpty());

    mvc.perform(get("/api/reports/diagnosis")
        .header("Authorization", "Bearer " + paidToken)
        .param("question", "cashflow"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.sources[?(@.entitlement == 'PAID')]").isNotEmpty());
  }

  private long createPaidIntelligence(String operatorToken, String title, String linkId) throws Exception {
    String response = mvc.perform(post("/api/paid-intelligence")
        .header("Authorization", "Bearer " + operatorToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(String.format("""
          {
            "title":"%s",
            "content":"Paid cohort intelligence must stay isolated.",
            "url":"https://example.com/paid/%s",
            "industryId":"general",
            "regionId":"cn-default",
            "linkId":"%s",
            "sourceId":"paid-seed"
          }
          """, title, linkId, linkId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("PENDING"))
      .andReturn()
      .getResponse()
      .getContentAsString();
    return objectMapper.readTree(response).at("/data/id").asLong();
  }

  @Test
  void operatorCanReadV2OperationsSurfaces() throws Exception {
    String operatorToken = login("operator");
    String userToken = login("user");

    mvc.perform(get("/api/ops/metrics").header("Authorization", "Bearer " + userToken))
      .andExpect(status().isForbidden());

    mvc.perform(get("/api/ops/metrics").header("Authorization", "Bearer " + operatorToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.cacheHitRateTarget").value(0.7))
      .andExpect(jsonPath("$.data.grayCohort").value("internal-operators-and-seed-paid-users"));

    mvc.perform(get("/api/ops/review-work-orders").header("Authorization", "Bearer " + operatorToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].reason").value("SUSPICIOUS_CONFLICT"));

    mvc.perform(get("/api/ops/audit-logs").header("Authorization", "Bearer " + operatorToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].action").value("V2_M0_SCOPE_LOCK"));
  }

  @Test
  void operatorCanListKnowledgeInGrayReleaseContext() throws Exception {
    String operatorToken = login("operator");

    mvc.perform(post("/api/knowledge/import")
        .header("Authorization", "Bearer " + operatorToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "title":"Gray release knowledge baseline",
            "content":"Knowledge listing stays available during V2 rollout.",
            "industryId":"general",
            "regionId":"cn-default",
            "linkId":"gray-release",
            "sourceId":"seed-runtime"
          }
          """))
      .andExpect(status().isOk());

    mvc.perform(get("/api/knowledge").header("Authorization", "Bearer " + operatorToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("OK"))
      .andExpect(jsonPath("$.data[?(@.title == 'Gray release knowledge baseline')]").isNotEmpty());
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
