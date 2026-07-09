CREATE TABLE IF NOT EXISTS admin_risk_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_type VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  threshold_value DECIMAL(8,4) NULL,
  scope_json JSON NULL,
  risk_level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  change_mode VARCHAR(16) NOT NULL DEFAULT 'IMMEDIATE',
  version INT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sla_data_points (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  window_start DATETIME NOT NULL,
  window_end DATETIME NOT NULL,
  total_requests INT NOT NULL DEFAULT 0,
  error_requests INT NOT NULL DEFAULT 0,
  latency_p50_ms DOUBLE NULL,
  latency_p95_ms DOUBLE NULL,
  latency_p99_ms DOUBLE NULL,
  uptime_flag TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_sla_window_start (window_start)
);

INSERT INTO alert_events (alert_level, component, message, status, owner, region_id, industry_id)
SELECT 'P1', 'collector', 'Crawler success rate dropped below review threshold.', 'OPEN', 'operator', 'cn-default', 'general'
WHERE NOT EXISTS (
  SELECT 1
  FROM alert_events
  WHERE component = 'collector'
    AND message = 'Crawler success rate dropped below review threshold.'
    AND status = 'OPEN'
);

INSERT INTO intelligence
  (title, content, url, status, confidence, link_id, region_id, industry_id, source_id, weight, content_hash)
SELECT 'Regional cash-flow pressure signal',
       'Multiple local operators report longer account receivable cycles and higher rent pressure.',
       'seed://admin/intelligence/cashflow',
       'PENDING',
       0.6200,
       'sales-payment',
       'cn-default',
       'general',
       'seed-admin',
       0.6200,
       'admin-seed-cashflow'
WHERE NOT EXISTS (
  SELECT 1
  FROM intelligence
  WHERE content_hash = 'admin-seed-cashflow'
);

INSERT INTO admin_intelligence_reviews
  (intelligence_id, review_status, reason, evidence_json, region_id, industry_id)
SELECT COALESCE((SELECT MIN(id) FROM intelligence), 1),
       'PENDING',
       'Needs source and severity review',
       JSON_ARRAY(),
       'cn-default',
       'general'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_intelligence_reviews
);

INSERT INTO admin_tickets
  (ticket_type, severity, target_type, target_id, title, description, status, owner, next_action, region_id, industry_id)
SELECT 'conflict',
       'P1',
       'intelligence',
       1,
       'Review old/new intelligence conflict',
       'A newly collected local signal may conflict with an older baseline rule.',
       'NEW',
       'operator',
       'Confirm whether this is short-term fluctuation or permanent rule change.',
       'cn-default',
       'general'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_tickets
  WHERE ticket_type = 'conflict'
    AND title = 'Review old/new intelligence conflict'
);

INSERT INTO admin_human_intelligence
  (city, industry_id, link_id, content, source_type, collector, event_time, confidence, entitlement, status, region_id)
SELECT 'Shanghai',
       'general',
       'sales-payment',
       'Local store operators report increased supplier prepayment pressure this month.',
       'local_visit',
       'operator',
       '2026-07',
       0.7200,
       'PAID',
       'PENDING_REVIEW',
       'cn-default'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_human_intelligence
  WHERE city = 'Shanghai'
    AND link_id = 'sales-payment'
    AND source_type = 'local_visit'
);

INSERT INTO audit_logs (actor, action, target_type, target_id, result)
SELECT 'system', 'ADMIN_V3_BOOTSTRAP', 'admin', 'seed', 'SUCCESS'
WHERE NOT EXISTS (
  SELECT 1
  FROM audit_logs
  WHERE action = 'ADMIN_V3_BOOTSTRAP'
    AND target_id = 'seed'
);

INSERT INTO admin_knowledge_nodes
  (id, title, slug, industry_id, region_id, link_id, status, published_version_id, source_id, weight)
SELECT 1,
       'Store rent pressure playbook',
       'store-rent-pressure-playbook',
       'general',
       'cn-default',
       'sales-payment',
       'PUBLISHED',
       1,
       'admin-knowledge',
       1.0000
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_knowledge_nodes
  WHERE id = 1
);

INSERT INTO admin_knowledge_versions
  (id, node_id, version_number, title, summary, content, source_url, review_status,
   author, reviewer, review_notes, change_notes, confidence, created_by_action)
SELECT 1,
       1,
       1,
       'Store rent pressure playbook',
       'Baseline guidance for evaluating rising rent pressure against cash-flow tolerance.',
       'Track rent-to-revenue ratio, landlord payment cadence, and whether temporary promotions are masking structural cost pressure.',
       'seed://admin/knowledge/rent-pressure',
       'APPROVED',
       'system',
       'operator',
       'Seed approved',
       'Bootstrap baseline',
       0.8800,
       'SEED'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_knowledge_versions
  WHERE id = 1
);

INSERT INTO admin_knowledge_publications (node_id, version_id, action, actor, notes)
SELECT 1, 1, 'PUBLISH', 'system', 'Bootstrap publication'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_knowledge_publications
  WHERE node_id = 1
    AND version_id = 1
    AND action = 'PUBLISH'
);

INSERT INTO knowledge_items
  (title, content, source_url, confidence, link_id, region_id, industry_id, source_id, weight)
SELECT 'Store rent pressure playbook',
       'Track rent-to-revenue ratio, landlord payment cadence, and whether temporary promotions are masking structural cost pressure.',
       'seed://admin/knowledge/rent-pressure',
       0.8800,
       'sales-payment',
       'cn-default',
       'general',
       'admin-node-1',
       0.8800
WHERE NOT EXISTS (
  SELECT 1
  FROM knowledge_items
  WHERE source_id = 'admin-node-1'
);

INSERT INTO admin_collection_sources
  (id, name, source_type, status, interval_minutes, max_retries, failure_threshold, cooldown_minutes,
   circuit_state, failure_count, region_id, industry_id, link_id, source_id, payload_json, next_run_time, last_status)
SELECT 1,
       'Shanghai rent pressure page',
       'PUBLIC_PAGE',
       'ENABLED',
       60,
       1,
       3,
       30,
       'CLOSED',
       0,
       'cn-default',
       'general',
       'sales-payment',
       'admin-collector-public',
       JSON_OBJECT(
         'url', 'https://example.com/shanghai-rent',
         'html', '<html><title>Shanghai Rent Pressure</title><body>Landlords in Shanghai request shorter concession periods and faster payment cycles.</body></html>'
       ),
       CURRENT_TIMESTAMP,
       'IDLE'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_collection_sources
  WHERE id = 1
);

INSERT INTO admin_collection_sources
  (id, name, source_type, status, interval_minutes, max_retries, failure_threshold, cooldown_minutes,
   circuit_state, failure_count, region_id, industry_id, link_id, source_id, payload_json, next_run_time, last_status)
SELECT 2,
       'Supplier prepayment mock API',
       'MOCK_API',
       'ENABLED',
       120,
       1,
       3,
       30,
       'CLOSED',
       0,
       'cn-default',
       'general',
       'supply-chain',
       'admin-collector-mock',
       JSON_OBJECT(
         'items', JSON_ARRAY(
           JSON_OBJECT(
             'title', 'Supplier prepayment pressure',
             'content', 'Several suppliers now request larger advance payments.',
             'url', 'https://example.com/mock/prepayment',
             'confidence', 0.82,
             'weight', 0.82,
             'industry_id', 'general',
             'region_id', 'cn-default',
             'link_id', 'supply-chain'
           )
         )
       ),
       CURRENT_TIMESTAMP,
       'IDLE'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_collection_sources
  WHERE id = 2
);

INSERT INTO admin_risk_rules
  (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
SELECT 'rumor_detection',
       'Rumor keyword filter',
       'Detect and flag content matching rumor patterns.',
       TRUE,
       0.75,
       JSON_OBJECT('industries', JSON_ARRAY('all'), 'regions', JSON_ARRAY('all')),
       'HIGH',
       'IMMEDIATE'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_risk_rules
  WHERE rule_type = 'rumor_detection'
);

INSERT INTO admin_risk_rules
  (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
SELECT 'conflict_judgment',
       'New/old conflict detection',
       'Flag intelligence that contradicts existing approved records.',
       TRUE,
       0.60,
       JSON_OBJECT('industries', JSON_ARRAY('all'), 'regions', JSON_ARRAY('all')),
       'MEDIUM',
       'GRAY'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_risk_rules
  WHERE rule_type = 'conflict_judgment'
);

INSERT INTO admin_risk_rules
  (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
SELECT 'gray_content',
       'Gray content sensitivity',
       'Suppress borderline content from public APIs.',
       FALSE,
       0.50,
       JSON_OBJECT('industries', JSON_ARRAY('general'), 'regions', JSON_ARRAY('cn-default')),
       'MEDIUM',
       'IMMEDIATE'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_risk_rules
  WHERE rule_type = 'gray_content'
);

INSERT INTO admin_risk_rules
  (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
SELECT 'ai_self_check',
       'AI output self-check threshold',
       'Minimum confidence for AI-generated answers to pass self-check.',
       TRUE,
       0.80,
       JSON_OBJECT('industries', JSON_ARRAY('all'), 'regions', JSON_ARRAY('all')),
       'HIGH',
       'IMMEDIATE'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_risk_rules
  WHERE rule_type = 'ai_self_check'
);

INSERT INTO admin_risk_rules
  (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
SELECT 'api_abuse',
       'API rate-limit protection',
       'Detect and throttle abusive API request patterns.',
       TRUE,
       0.90,
       JSON_OBJECT('industries', JSON_ARRAY('all'), 'regions', JSON_ARRAY('all')),
       'CRITICAL',
       'IMMEDIATE'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_risk_rules
  WHERE rule_type = 'api_abuse'
);

INSERT INTO admin_risk_rules
  (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
SELECT 'paid_protection',
       'Paid content watermark',
       'Ensure paid intelligence is not served to free-tier users.',
       TRUE,
       1.00,
       JSON_OBJECT('industries', JSON_ARRAY('all'), 'regions', JSON_ARRAY('all')),
       'CRITICAL',
       'IMMEDIATE'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_risk_rules
  WHERE rule_type = 'paid_protection'
);

INSERT INTO admin_collection_keywords (source_config_id, keyword, match_mode, status, notes)
SELECT 1, 'rent', 'INCLUDE', 'ACTIVE', 'Track rent pressure signals'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_collection_keywords
  WHERE source_config_id = 1
    AND keyword = 'rent'
);

INSERT INTO admin_collection_keywords (source_config_id, keyword, match_mode, status, notes)
SELECT 1, 'rumor', 'EXCLUDE', 'ACTIVE', 'Filter rumor-style content'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_collection_keywords
  WHERE source_config_id = 1
    AND keyword = 'rumor'
);

INSERT INTO admin_collection_keywords (source_config_id, keyword, match_mode, status, notes)
SELECT 2, 'prepayment', 'INCLUDE', 'ACTIVE', 'Focus on supplier prepayment signals'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_collection_keywords
  WHERE source_config_id = 2
    AND keyword = 'prepayment'
);
