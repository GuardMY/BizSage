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

  @Test
  void knowledgeLifecycleSupportsSecondReviewerPublishRollbackAndKnowledgeSync() throws Exception {
    String operatorToken = token("operator");
    String adminToken = token("admin");

    String firstDraft = mvc.perform(post("/api/admin/knowledge/drafts")
            .header("Authorization", "Bearer " + operatorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Supplier prepayment pressure guide",
                  "slug": "supplier-prepayment-pressure-guide",
                  "industryId": "general",
                  "regionId": "cn-default",
                  "linkId": "sales-payment",
                  "summary": "Baseline playbook for rising supplier prepayment requirements.",
                  "content": "Version 1 content for supplier prepayment pressure.",
                  "sourceUrl": "https://example.com/prepayment-v1",
                  "confidence": 0.83,
                  "changeNotes": "Initial draft"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andReturn().getResponse().getContentAsString();

    JsonNode firstDraftJson = objectMapper.readTree(firstDraft);
    long nodeId = firstDraftJson.at("/data/nodeId").asLong();
    long version1Id = firstDraftJson.at("/data/draftVersionId").asLong();

    mvc.perform(post("/api/admin/knowledge/nodes/" + nodeId + "/versions/" + version1Id + "/submit-review")
            .header("Authorization", "Bearer " + operatorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"notes\":\"Ready for review\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_REVIEW"));

    mvc.perform(post("/api/admin/knowledge/nodes/" + nodeId + "/versions/" + version1Id + "/approve")
            .header("Authorization", "Bearer " + operatorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"notes\":\"self approval should fail\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("reviewer must be different from author"));

    mvc.perform(post("/api/admin/knowledge/nodes/" + nodeId + "/versions/" + version1Id + "/approve")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"notes\":\"Reviewed by admin\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"));

    mvc.perform(post("/api/admin/knowledge/nodes/" + nodeId + "/versions/" + version1Id + "/publish")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"notes\":\"Publish version 1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.data.publishedVersionId").value(version1Id));

    String secondDraft = mvc.perform(post("/api/admin/knowledge/drafts")
            .header("Authorization", "Bearer " + operatorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "nodeId": %d,
                  "title": "Supplier prepayment pressure guide",
                  "slug": "supplier-prepayment-pressure-guide",
                  "industryId": "general",
                  "regionId": "cn-default",
                  "linkId": "sales-payment",
                  "summary": "Expanded playbook with mitigation steps.",
                  "content": "Version 2 content with mitigation steps.",
                  "sourceUrl": "https://example.com/prepayment-v2",
                  "confidence": 0.9,
                  "changeNotes": "Expanded mitigation guidance"
                }
                """.formatted(nodeId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DRAFT"))
        .andReturn().getResponse().getContentAsString();

    long version2Id = objectMapper.readTree(secondDraft).at("/data/draftVersionId").asLong();

    mvc.perform(post("/api/admin/knowledge/nodes/" + nodeId + "/versions/" + version2Id + "/submit-review")
            .header("Authorization", "Bearer " + operatorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"notes\":\"Second draft ready\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("IN_REVIEW"));

    mvc.perform(post("/api/admin/knowledge/nodes/" + nodeId + "/versions/" + version2Id + "/approve")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"notes\":\"Approved version 2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("APPROVED"));

    mvc.perform(post("/api/admin/knowledge/nodes/" + nodeId + "/versions/" + version2Id + "/publish")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"notes\":\"Publish version 2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.publishedVersionId").value(version2Id));

    mvc.perform(get("/api/admin/knowledge/diff")
            .header("Authorization", "Bearer " + adminToken)
            .param("leftVersionId", String.valueOf(version1Id))
            .param("rightVersionId", String.valueOf(version2Id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.leftContent").value("Version 1 content for supplier prepayment pressure."))
        .andExpect(jsonPath("$.data.rightContent").value("Version 2 content with mitigation steps."));

    mvc.perform(post("/api/admin/knowledge/nodes/" + nodeId + "/rollback")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(("{\"targetVersionId\":" + version1Id + ",\"notes\":\"Rollback to baseline\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.publishedVersionId").value(version1Id));

    String syncedContent = jdbcTemplate.queryForObject(
        "select content from knowledge_items where source_id = ?",
        String.class,
        "admin-node-" + nodeId);
    assertThat(syncedContent).isEqualTo("Version 1 content for supplier prepayment pressure.");

    Integer publicationCount = jdbcTemplate.queryForObject(
        "select count(*) from admin_knowledge_publications where node_id = ?",
        Integer.class,
        nodeId);
    assertThat(publicationCount).isGreaterThanOrEqualTo(3);
  }

  @Test
  void collectionLifecycleSupportsSourceKeywordRunAndPersistence() throws Exception {
    String operatorToken = token("operator");

    String createdSource = mvc.perform(post("/api/admin/collection/sources")
            .header("Authorization", "Bearer " + operatorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Hangzhou rent watch",
                  "sourceType": "PUBLIC_PAGE",
                  "status": "ENABLED",
                  "intervalMinutes": 45,
                  "maxRetries": 1,
                  "failureThreshold": 3,
                  "cooldownMinutes": 15,
                  "regionId": "cn-default",
                  "industryId": "general",
                  "linkId": "sales-payment",
                  "sourceId": "admin-hangzhou-page",
                  "payloadJson": "{\"url\":\"https://example.com/hangzhou-rent\",\"html\":\"<html><title>Hangzhou Rent Watch</title><body>Rent pressure is rising across Hangzhou shopping districts.</body></html>\"}"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.source.name").value("Hangzhou rent watch"))
        .andReturn().getResponse().getContentAsString();

    long sourceConfigId = objectMapper.readTree(createdSource).at("/data/source/sourceConfigId").asLong();

    mvc.perform(post("/api/admin/collection/keywords")
            .header("Authorization", "Bearer " + operatorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(("{\"sourceConfigId\":" + sourceConfigId + ",\"keyword\":\"rent\",\"matchMode\":\"INCLUDE\",\"status\":\"ACTIVE\",\"notes\":\"Keep rent signals\"}")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.keyword").value("rent"));

    String runResult = mvc.perform(post("/api/admin/collection/sources/" + sourceConfigId + "/run")
            .header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.recordsCollected").value(1))
        .andExpect(jsonPath("$.data.recordsPersisted").value(1))
        .andReturn().getResponse().getContentAsString();

    long runId = objectMapper.readTree(runResult).at("/data/runId").asLong();

    mvc.perform(get("/api/admin/collection/sources/" + sourceConfigId)
            .header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.keywords[0].keyword").value("rent"))
        .andExpect(jsonPath("$.data.recentRuns[0].runId").value(runId));

    mvc.perform(get("/api/admin/collection/jobs")
            .header("Authorization", "Bearer " + operatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].sourceConfigId").exists());

    int rawRecordCount = jdbcTemplate.queryForObject(
        "select count(*) from raw_records where source_id = ?",
        Integer.class,
        "admin-hangzhou-page");
    assertThat(rawRecordCount).isEqualTo(1);

    int runAuditCount = jdbcTemplate.queryForObject(
        "select count(*) from audit_logs where action in ('ADMIN_COLLECTION_CREATE_SOURCE','ADMIN_COLLECTION_CREATE_KEYWORD','ADMIN_COLLECTION_RUN_SOURCE') and target_id = ?",
        Integer.class,
        String.valueOf(sourceConfigId));
    assertThat(runAuditCount).isGreaterThanOrEqualTo(2);
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
