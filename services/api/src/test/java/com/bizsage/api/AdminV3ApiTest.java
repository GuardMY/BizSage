package com.bizsage.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminV3ApiTest {
  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  void adminDashboardAndListsRequireOperatorRole() throws Exception {
    String userToken = token("user");
    mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());

    String operatorToken = token("operator");
    mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.metrics[?(@.key=='pendingReviews')]").exists())
        .andExpect(jsonPath("$.data.urgentAlerts").isArray())
        .andExpect(jsonPath("$.data.openTickets").isArray());

    mvc.perform(get("/api/admin/intelligence-reviews").header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].reviewStatus").value("PENDING"));
  }

  @Test
  void adminActionsUpdateStateAndWriteAuditLogs() throws Exception {
    String token = token("operator");

    mvc.perform(post("/api/admin/alerts/1/acknowledge")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"notes\":\"seen\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
        .andExpect(jsonPath("$.data.owner").value("operator"));

    mvc.perform(post("/api/admin/intelligence-reviews/1/verdict")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"verdict\":\"PASS\",\"notes\":\"source checked\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reviewStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.verdict").value("PASS"));

    mvc.perform(post("/api/admin/tickets/1/transition")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"IN_PROGRESS\",\"owner\":\"operator\",\"nextAction\":\"review evidence\",\"notes\":\"claimed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data.owner").value("operator"));

    int auditCount = jdbcTemplate.queryForObject(
        "select count(*) from audit_logs where action in ('ADMIN_ALERT_ACKNOWLEDGE','ADMIN_INTELLIGENCE_PASS','ADMIN_TICKET_TRANSITION')",
        Integer.class);
    assertThat(auditCount).isGreaterThanOrEqualTo(3);
  }

  @Test
  void humanIntelligenceCanBeCreatedReviewedAndPromotedToIntelligence() throws Exception {
    String token = token("operator");

    String created = mvc.perform(post("/api/admin/human-intelligence")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "city": "Hangzhou",
                  "industryId": "general",
                  "linkId": "channel",
                  "content": "Local channel operators report higher promotion costs.",
                  "sourceType": "local_visit",
                  "collector": "operator",
                  "eventTime": "2026-07",
                  "confidence": 0.74,
                  "entitlement": "PAID",
                  "regionId": "cn-default",
                  "sourceId": "manual-admin"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
        .andReturn().getResponse().getContentAsString();

    long id = objectMapper.readTree(created).at("/data/id").asLong();

    mvc.perform(post("/api/admin/human-intelligence/" + id + "/review")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"verdict\":\"PASS\",\"notes\":\"verified by local collector\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
        .andExpect(jsonPath("$.data.reviewer").value("operator"));

    int promoted = jdbcTemplate.queryForObject(
        "select count(*) from intelligence where url = ? and status = 'APPROVED'",
        Integer.class,
        "human://" + id);
    assertThat(promoted).isEqualTo(1);
  }

  private String token(String username) throws Exception {
    String response = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode json = objectMapper.readTree(response);
    return json.at("/data/token").asText();
  }
}
