package com.bizsage.api.admin;

import jakarta.annotation.PostConstruct;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminSchemaMigration {
  private final JdbcTemplate jdbcTemplate;
  private DatabaseDialect dialect;

  public AdminSchemaMigration(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @PostConstruct
  void migrate() {
    dialect = detectDialect();
    ensureAdminIntelligenceReviews();
    ensureAdminTickets();
    ensureAdminHumanIntelligence();
    ensureAdminKnowledgeNodes();
    ensureAdminKnowledgeVersions();
    ensureAdminKnowledgePublications();
    ensureAdminCollectionSources();
    ensureAdminCollectionKeywords();
    ensureAdminCollectionJobRuns();
    ensureAdminRiskRules();
    ensureAdminCollectionComplianceFields();
    ensureFalseInformationLedger();
    ensureConflictResolutions();
    ensureSnapshotRetentionColumns();
    seedAdminData();
  }

  private void ensureAdminIntelligenceReviews() {
    if (tableExists("admin_intelligence_reviews")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_intelligence_reviews (
          id %s primary key,
          intelligence_id bigint not null,
          review_status varchar(32) not null default 'PENDING',
          verdict varchar(32),
          reviewer varchar(64),
          reason varchar(512),
          evidence_json %s,
          source_id varchar(64) not null default 'admin-review',
          weight decimal(8,4) not null default 1.0000,
          region_id varchar(64) not null default 'global',
          industry_id varchar(64) not null default 'global',
          create_time timestamp not null default current_timestamp,
          update_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition(), dialect.textDefinition()));
  }

  private void ensureAdminTickets() {
    if (tableExists("admin_tickets")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_tickets (
          id %s primary key,
          ticket_type varchar(64) not null,
          severity varchar(16) not null,
          target_type varchar(64) not null,
          target_id bigint not null,
          title varchar(255) not null,
          description %s,
          status varchar(32) not null default 'NEW',
          owner varchar(64),
          next_action varchar(255),
          source_id varchar(64) not null default 'admin-ticket',
          weight decimal(8,4) not null default 1.0000,
          region_id varchar(64) not null default 'global',
          industry_id varchar(64) not null default 'global',
          create_time timestamp not null default current_timestamp,
          update_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition(), dialect.textDefinition()));
  }

  private void ensureAdminHumanIntelligence() {
    if (tableExists("admin_human_intelligence")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_human_intelligence (
          id %s primary key,
          city varchar(64) not null,
          industry_id varchar(64) not null,
          link_id varchar(64) not null,
          content %s not null,
          source_type varchar(64) not null,
          collector varchar(64) not null,
          event_time varchar(64),
          confidence decimal(8,4) not null default 0.7000,
          entitlement varchar(32) not null default 'FREE',
          status varchar(32) not null default 'PENDING_REVIEW',
          reviewer varchar(64),
          review_notes varchar(512),
          source_id varchar(64) not null default 'human-intel',
          weight decimal(8,4) not null default 1.0000,
          region_id varchar(64) not null default 'cn-default',
          create_time timestamp not null default current_timestamp,
          update_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition(), dialect.textDefinition()));
  }

  private void ensureAdminKnowledgeNodes() {
    if (tableExists("admin_knowledge_nodes")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_knowledge_nodes (
          id %s primary key,
          title varchar(255) not null,
          slug varchar(255) not null,
          industry_id varchar(64) not null default 'general',
          region_id varchar(64) not null default 'cn-default',
          link_id varchar(64) not null default 'general',
          status varchar(32) not null default 'DRAFT',
          draft_version_id bigint,
          review_version_id bigint,
          published_version_id bigint,
          source_id varchar(64) not null default 'admin-knowledge',
          weight decimal(8,4) not null default 1.0000,
          create_time timestamp not null default current_timestamp,
          update_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition()));
  }

  private void ensureAdminKnowledgeVersions() {
    if (tableExists("admin_knowledge_versions")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_knowledge_versions (
          id %s primary key,
          node_id bigint not null,
          version_number int not null,
          title varchar(255) not null,
          summary varchar(1024),
          content %s not null,
          source_url varchar(1024),
          review_status varchar(32) not null default 'DRAFT',
          author varchar(64) not null,
          reviewer varchar(64),
          review_notes varchar(512),
          change_notes varchar(512),
          confidence decimal(8,4) not null default 0.8500,
          created_by_action varchar(64) not null default 'SAVE_DRAFT',
          create_time timestamp not null default current_timestamp,
          update_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition(), dialect.textDefinition()));
  }

  private void ensureAdminKnowledgePublications() {
    if (tableExists("admin_knowledge_publications")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_knowledge_publications (
          id %s primary key,
          node_id bigint not null,
          version_id bigint not null,
          action varchar(32) not null,
          actor varchar(64) not null,
          notes varchar(512),
          create_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition()));
  }

  private void ensureAdminCollectionSources() {
    if (tableExists("admin_collection_sources")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_collection_sources (
          id %s primary key,
          name varchar(255) not null,
          source_type varchar(64) not null,
          status varchar(32) not null default 'ENABLED',
          interval_minutes int not null default 30,
          max_retries int not null default 1,
          failure_threshold int not null default 3,
          cooldown_minutes int not null default 30,
          circuit_state varchar(32) not null default 'CLOSED',
          failure_count int not null default 0,
          region_id varchar(64) not null default 'cn-default',
          industry_id varchar(64) not null default 'general',
          link_id varchar(64) not null default 'collection',
          source_id varchar(64) not null default 'admin-collector',
          payload_json %s,
          next_run_time timestamp,
          last_run_time timestamp,
          last_status varchar(32) not null default 'IDLE',
          last_error varchar(1024),
          create_time timestamp not null default current_timestamp,
          update_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition(), dialect.textDefinition()));
  }

  private void ensureAdminCollectionKeywords() {
    if (tableExists("admin_collection_keywords")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_collection_keywords (
          id %s primary key,
          source_config_id bigint not null,
          keyword varchar(255) not null,
          match_mode varchar(32) not null default 'INCLUDE',
          status varchar(32) not null default 'ACTIVE',
          notes varchar(512),
          create_time timestamp not null default current_timestamp,
          update_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition()));
  }

  private void ensureAdminCollectionJobRuns() {
    if (tableExists("admin_collection_job_runs")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_collection_job_runs (
          id %s primary key,
          job_id bigint not null,
          source_config_id bigint not null,
          source_type varchar(64) not null,
          trigger_type varchar(32) not null,
          status varchar(32) not null default 'RUNNING',
          records_collected int not null default 0,
          records_filtered int not null default 0,
          records_persisted int not null default 0,
          error_message varchar(1024),
          start_time timestamp not null default current_timestamp,
          finish_time timestamp
        )
        """.formatted(dialect.idDefinition()));
  }

  private void ensureAdminRiskRules() {
    if (tableExists("admin_risk_rules")) {
      return;
    }
    jdbcTemplate.execute("""
        create table admin_risk_rules (
          id %s primary key,
          rule_type varchar(64) not null,
          name varchar(255) not null,
          description %s,
          enabled boolean not null default true,
          threshold_value decimal(8,4),
          scope_json %s,
          risk_level varchar(16) not null default 'MEDIUM',
          change_mode varchar(16) not null default 'IMMEDIATE',
          version int not null default 1,
          create_time timestamp not null default current_timestamp,
          update_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition(), dialect.textDefinition(), dialect.textDefinition()));
  }

  private void ensureAdminCollectionComplianceFields() {
    if (!columnExists("admin_collection_sources", "compliance_notes")) {
      jdbcTemplate.execute("alter table admin_collection_sources add column compliance_notes " + dialect.textDefinition());
    }
    if (!columnExists("admin_collection_sources", "proxy_config")) {
      jdbcTemplate.execute("alter table admin_collection_sources add column proxy_config " + dialect.textDefinition());
    }
  }

  private boolean columnExists(String tableName, String columnName) {
    return jdbcTemplate.execute((java.sql.Connection connection) -> {
      java.sql.DatabaseMetaData metadata = connection.getMetaData();
      try (java.sql.ResultSet columns = metadata.getColumns(connection.getCatalog(), null,
          normalize(tableName), normalize(columnName))) {
        return columns.next();
      }
    });
  }

  private void ensureFalseInformationLedger() {
    if (tableExists("false_information_ledger")) {
      return;
    }
    jdbcTemplate.execute("""
        create table false_information_ledger (
          id %s primary key,
          original_intelligence_id bigint,
          title varchar(255) not null,
          content %s not null,
          content_hash varchar(64),
          url varchar(1024),
          conflict_reason varchar(64) not null,
          matched_rumor_keyword varchar(255),
          source_id varchar(64) not null,
          region_id varchar(64) not null,
          industry_id varchar(64) not null,
          link_id varchar(64) not null,
          archived_by varchar(64) not null default 'system',
          archive_reason varchar(512),
          create_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition(), dialect.textDefinition()));
  }

  private void ensureConflictResolutions() {
    if (tableExists("conflict_resolutions")) {
      return;
    }
    jdbcTemplate.execute("""
        create table conflict_resolutions (
          id %s primary key,
          incoming_intelligence_id bigint,
          existing_intelligence_id bigint,
          conflict_branch varchar(32) not null,
          sim_hash_distance int not null default 0,
          incoming_weight decimal(8,4) not null default 0.6000,
          existing_weight decimal(8,4) not null default 0.6000,
          routing_action varchar(64) not null,
          review_ticket_id bigint,
          resolved_by varchar(64) not null default 'system',
          notes varchar(512),
          source_id varchar(64) not null default 'conflict-engine',
          region_id varchar(64) not null,
          industry_id varchar(64) not null,
          link_id varchar(64) not null default 'unknown',
          create_time timestamp not null default current_timestamp
        )
        """.formatted(dialect.idDefinition()));
  }

  private void ensureSnapshotRetentionColumns() {
    if (!columnExists("intelligence_snapshots", "retention_days")) {
      jdbcTemplate.execute("alter table intelligence_snapshots add column retention_days int not null default 30");
    }
    if (!columnExists("intelligence_snapshots", "record_count")) {
      jdbcTemplate.execute("alter table intelligence_snapshots add column record_count int not null default 0");
    }
    if (!columnExists("intelligence_snapshots", "parent_snapshot_id")) {
      jdbcTemplate.execute("alter table intelligence_snapshots add column parent_snapshot_id bigint");
    }
    if (!columnExists("intelligence_snapshots", "expires_at")) {
      jdbcTemplate.execute("alter table intelligence_snapshots add column expires_at timestamp");
    }
  }

  private void seedAdminData() {
    if (count("alert_events") == 0) {
      jdbcTemplate.update("""
          insert into alert_events (alert_level, component, message, status, owner, region_id, industry_id)
          values ('P1', 'collector', 'Crawler success rate dropped below review threshold.', 'OPEN', 'operator', 'cn-default', 'general')
          """);
    }
    if (count("intelligence") == 0) {
      jdbcTemplate.update("""
          insert into intelligence
            (title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight, content_hash)
          values (?, ?, ?, 'PENDING', 0.6200, 'sales-payment', 'cn-default', 'general', 'seed-admin', 0.6200, ?)
          """,
          "Regional cash-flow pressure signal",
          "Multiple local operators report longer account receivable cycles and higher rent pressure.",
          "seed://admin/intelligence/cashflow",
          "admin-seed-cashflow");
    }
    if (count("admin_intelligence_reviews") == 0) {
      Long intelligenceId = jdbcTemplate.queryForObject("select min(id) from intelligence", Long.class);
      jdbcTemplate.update("""
          insert into admin_intelligence_reviews
            (intelligence_id, review_status, reason, evidence_json, region_id, industry_id)
          values (?, 'PENDING', 'Needs source and severity review', '[]', 'cn-default', 'general')
          """, intelligenceId == null ? 1L : intelligenceId);
    }
    if (count("admin_tickets") == 0) {
      jdbcTemplate.update("""
          insert into admin_tickets
            (ticket_type, severity, target_type, target_id, title, description, status, owner, next_action, region_id, industry_id)
          values ('conflict', 'P1', 'intelligence', 1, 'Review old/new intelligence conflict',
                  'A newly collected local signal may conflict with an older baseline rule.', 'NEW', 'operator',
                  'Confirm whether this is short-term fluctuation or permanent rule change.', 'cn-default', 'general')
          """);
    }
    if (count("admin_human_intelligence") == 0) {
      jdbcTemplate.update("""
          insert into admin_human_intelligence
            (city, industry_id, link_id, content, source_type, collector, event_time, confidence, entitlement, status, region_id)
          values ('Shanghai', 'general', 'sales-payment',
                  'Local store operators report increased supplier prepayment pressure this month.',
                  'local_visit', 'operator', '2026-07', 0.7200, 'PAID', 'PENDING_REVIEW', 'cn-default')
          """);
    }
    if (count("audit_logs") == 0) {
      jdbcTemplate.update("""
          insert into audit_logs (actor, action, target_type, target_id, result)
          values ('system', 'ADMIN_V3_BOOTSTRAP', 'admin', 'seed', 'SUCCESS')
          """);
    }
    if (count("admin_knowledge_nodes") == 0) {
      jdbcTemplate.update("""
          insert into admin_knowledge_nodes
            (id, title, slug, industry_id, region_id, link_id, status, published_version_id, source_id, weight)
          values (1, 'Store rent pressure playbook', 'store-rent-pressure-playbook',
                  'general', 'cn-default', 'sales-payment', 'PUBLISHED', 1, 'admin-knowledge', 1.0000)
          """);
    }
    if (count("admin_knowledge_versions") == 0) {
      jdbcTemplate.update("""
          insert into admin_knowledge_versions
            (id, node_id, version_number, title, summary, content, source_url, review_status,
             author, reviewer, review_notes, change_notes, confidence, created_by_action)
          values
            (1, 1, 1, 'Store rent pressure playbook',
             'Baseline guidance for evaluating rising rent pressure against cash-flow tolerance.',
             'Track rent-to-revenue ratio, landlord payment cadence, and whether temporary promotions are masking structural cost pressure.',
             'seed://admin/knowledge/rent-pressure',
             'APPROVED', 'system', 'operator', 'Seed approved', 'Bootstrap baseline', 0.8800, 'SEED')
          """);
    }
    if (count("admin_knowledge_publications") == 0) {
      jdbcTemplate.update("""
          insert into admin_knowledge_publications (node_id, version_id, action, actor, notes)
          values (1, 1, 'PUBLISH', 'system', 'Bootstrap publication')
          """);
    }
    if (tableExists("knowledge_items")) {
      Integer knowledgeSyncCount = jdbcTemplate.queryForObject(
          "select count(*) from knowledge_items where source_id = 'admin-node-1'",
          Integer.class);
      if (knowledgeSyncCount == null || knowledgeSyncCount == 0) {
        jdbcTemplate.update("""
            insert into knowledge_items
              (title, content, source_url, confidence, link_id, region_id, industry_id, source_id, weight)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "Store rent pressure playbook",
            "Track rent-to-revenue ratio, landlord payment cadence, and whether temporary promotions are masking structural cost pressure.",
            "seed://admin/knowledge/rent-pressure",
            0.8800,
            "sales-payment",
            "cn-default",
            "general",
            "admin-node-1",
            0.8800);
      }
    }
    if (count("admin_collection_sources") == 0) {
      jdbcTemplate.update("""
          insert into admin_collection_sources
            (id, name, source_type, status, interval_minutes, max_retries, failure_threshold, cooldown_minutes,
             circuit_state, failure_count, region_id, industry_id, link_id, source_id, payload_json, next_run_time, last_status)
          values
            (1, 'Shanghai rent pressure page', 'PUBLIC_PAGE', 'ENABLED', 60, 1, 3, 30,
             'CLOSED', 0, 'cn-default', 'general', 'sales-payment', 'admin-collector-public',
             '{"url":"https://example.com/shanghai-rent","html":"<html><title>Shanghai Rent Pressure</title><body>Landlords in Shanghai request shorter concession periods and faster payment cycles.</body></html>"}',
             current_timestamp, 'IDLE'),
            (2, 'Supplier prepayment mock API', 'MOCK_API', 'ENABLED', 120, 1, 3, 30,
             'CLOSED', 0, 'cn-default', 'general', 'supply-chain', 'admin-collector-mock',
             '{"items":[{"title":"Supplier prepayment pressure","content":"Several suppliers now request larger advance payments.","url":"https://example.com/mock/prepayment","confidence":0.82,"weight":0.82,"industry_id":"general","region_id":"cn-default","link_id":"supply-chain"}]}',
             current_timestamp, 'IDLE')
          """);
    }
    if (count("admin_risk_rules") == 0) {
      jdbcTemplate.update("""
          insert into admin_risk_rules (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
          values
            ('rumor_detection', 'Rumor keyword filter', 'Detect and flag content matching rumor patterns.', true, 0.75, '{"industries":["all"],"regions":["all"]}', 'HIGH', 'IMMEDIATE'),
            ('conflict_judgment', 'New/old conflict detection', 'Flag intelligence that contradicts existing approved records.', true, 0.60, '{"industries":["all"],"regions":["all"]}', 'MEDIUM', 'GRAY'),
            ('gray_content', 'Gray content sensitivity', 'Suppress borderline content from public APIs.', false, 0.50, '{"industries":["general"],"regions":["cn-default"]}', 'MEDIUM', 'IMMEDIATE'),
            ('ai_self_check', 'AI output self-check threshold', 'Minimum confidence for AI-generated answers to pass self-check.', true, 0.80, '{"industries":["all"],"regions":["all"]}', 'HIGH', 'IMMEDIATE'),
            ('api_abuse', 'API rate-limit protection', 'Detect and throttle abusive API request patterns.', true, 0.90, '{"industries":["all"],"regions":["all"]}', 'CRITICAL', 'IMMEDIATE'),
            ('paid_protection', 'Paid content watermark', 'Ensure paid intelligence is not served to free-tier users.', true, 1.00, '{"industries":["all"],"regions":["all"]}', 'CRITICAL', 'IMMEDIATE')
          """);
    }
    if (count("admin_collection_keywords") == 0) {
      jdbcTemplate.update("""
          insert into admin_collection_keywords (source_config_id, keyword, match_mode, status, notes)
          values
            (1, 'rent', 'INCLUDE', 'ACTIVE', 'Track rent pressure signals'),
            (1, 'rumor', 'EXCLUDE', 'ACTIVE', 'Filter rumor-style content'),
            (2, 'prepayment', 'INCLUDE', 'ACTIVE', 'Focus on supplier prepayment signals')
          """);
    }
  }

  private int count(String tableName) {
    if (!tableExists(tableName)) {
      return 0;
    }
    Integer count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class);
    return count == null ? 0 : count;
  }

  private boolean tableExists(String tableName) {
    return jdbcTemplate.execute((java.sql.Connection connection) -> {
      DatabaseMetaData metadata = connection.getMetaData();
      try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, null, new String[] {"TABLE"})) {
        while (tables.next()) {
          if (tableName.equalsIgnoreCase(normalize(tables.getString("TABLE_NAME")))) {
            return true;
          }
        }
        return false;
      }
    });
  }

  private DatabaseDialect detectDialect() {
    return jdbcTemplate.execute((java.sql.Connection connection) -> {
      String productName = connection.getMetaData().getDatabaseProductName();
      if (productName != null && productName.toLowerCase(Locale.ROOT).contains("mysql")) {
        return DatabaseDialect.MYSQL;
      }
      return DatabaseDialect.H2;
    });
  }

  private String normalize(String identifier) {
    if (identifier == null) {
      return "";
    }
    return identifier.toLowerCase(Locale.ROOT);
  }

  private enum DatabaseDialect {
    MYSQL {
      @Override
      String idDefinition() {
        return "bigint auto_increment";
      }

      @Override
      String textDefinition() {
        return "longtext";
      }
    },
    H2 {
      @Override
      String idDefinition() {
        return "bigint generated by default as identity";
      }

      @Override
      String textDefinition() {
        return "clob";
      }
    };

    abstract String idDefinition();

    abstract String textDefinition();
  }
}
