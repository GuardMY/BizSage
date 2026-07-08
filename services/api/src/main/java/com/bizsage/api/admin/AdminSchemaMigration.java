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