package com.bizsage.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GovernanceApiTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  JdbcTemplate jdbcTemplate;

  private String operatorToken;
  private String userToken;

  @BeforeEach
  void setUp() throws Exception {
    operatorToken = loginAs("operator");
    userToken = loginAs("user");
  }

  // ── Snapshot tests ────────────────────────────────────────────

  @Test
  void snapshotLifecycleCreatesListsAndRetrieves() throws Exception {
    // Generate a daily snapshot (manual trigger)
    String generateResult = mvc.perform(post("/api/admin/snapshots/generate")
            .param("type", "DAILY")
            .param("region", "cn-default")
            .param("industry", "general")
            .header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.snapshotType").value("DAILY"))
        .andExpect(jsonPath("$.data.regionId").value("cn-default"))
        .andReturn().getResponse().getContentAsString();
    assertNotNull(generateResult);

    // List snapshots
    mvc.perform(get("/api/admin/snapshots")
            .param("type", "DAILY")
            .param("region", "cn-default")
            .param("industry", "general")
            .header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));
  }

  @Test
  void snapshotCompareReturnsDiff() throws Exception {
    // Generate two snapshots
    var r1 = mvc.perform(post("/api/admin/snapshots/generate")
            .param("type", "DAILY")
            .param("region", "cn-default")
            .param("industry", "general")
            .header("Authorization", "Bearer " + operatorToken))
        .andReturn().getResponse().getContentAsString();
    long id1 = objectMapper.readTree(r1).get("data").get("id").asLong();

    var r2 = mvc.perform(post("/api/admin/snapshots/generate")
            .param("type", "DAILY")
            .param("region", "cn-default")
            .param("industry", "general")
            .header("Authorization", "Bearer " + operatorToken))
        .andReturn().getResponse().getContentAsString();
    long id2 = objectMapper.readTree(r2).get("data").get("id").asLong();

    // Compare
    mvc.perform(get("/api/admin/snapshots/" + id1 + "/diff")
            .param("compare", String.valueOf(id2))
            .header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.leftSnapshotId").value(id1))
        .andExpect(jsonPath("$.data.rightSnapshotId").value(id2));
  }

  @Test
  void snapshotEndpointsRequireOperatorRole() throws Exception {
    mvc.perform(get("/api/admin/snapshots")
            .param("type", "DAILY")
            .param("region", "cn-default")
            .param("industry", "general")
            .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  // ── Conflict resolution tests ─────────────────────────────────

  @Test
  void conflictEndpointsListResolutions() throws Exception {
    mvc.perform(get("/api/admin/governance/conflicts")
            .param("region", "cn-default")
            .param("industry", "general")
            .header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").exists());
  }

  @Test
  void falseLedgerListsBlockedContent() throws Exception {
    mvc.perform(get("/api/admin/governance/false-ledger")
            .param("region", "cn-default")
            .param("industry", "general")
            .header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").exists());
  }

  @Test
  void conflictEndpointsRequireOperatorRole() throws Exception {
    mvc.perform(get("/api/admin/governance/conflicts")
            .param("region", "cn-default")
            .param("industry", "general")
            .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  // ── Data isolation tests ──────────────────────────────────────

  @Test
  void freeUserCannotAccessAdminSnapshots() throws Exception {
    mvc.perform(get("/api/admin/snapshots")
            .param("type", "DAILY")
            .param("region", "cn-default")
            .param("industry", "general")
            .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  // ── Cleanup tests ─────────────────────────────────────────────

  @Test
  void snapshotCleanupRemovesNothingForFutureExpiry() throws Exception {
    mvc.perform(post("/api/admin/snapshots/cleanup")
            .header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(0));
  }

  // ── Helpers ───────────────────────────────────────────────────

  private String loginAs(String username) throws Exception {
    var body = objectMapper.writeValueAsString(
        java.util.Map.of("username", username, "password", "password"));
    var result = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(result).get("data").get("token").asText();
  }
}
