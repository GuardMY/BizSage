CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL,
  phone_encrypted VARCHAR(255) NULL,
  identity_encrypted VARCHAR(255) NULL,
  membership_level VARCHAR(32) NOT NULL DEFAULT 'FREE',
  consultation_preferences VARCHAR(255) NOT NULL DEFAULT '',
  preferred_locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
  region_id VARCHAR(64) NOT NULL DEFAULT 'default-region',
  industry_id VARCHAR(64) NOT NULL DEFAULT 'default-industry',
  source_id VARCHAR(64) NOT NULL DEFAULT 'system',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  owner_username VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'user',
  agent_mode VARCHAR(32) NOT NULL DEFAULT 'DIAGNOSIS',
  workflow_stage VARCHAR(32) NOT NULL DEFAULT 'INTRO',
  profile_completeness DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  primary_issue_tags TEXT NULL,
  recommended_question_ids TEXT NULL,
  closed_by VARCHAR(64) NULL,
  closed_reason VARCHAR(255) NULL,
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_conversations_user (user_id)
);

CREATE TABLE IF NOT EXISTS messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  sender VARCHAR(32) NOT NULL,
  message_type VARCHAR(32) NOT NULL DEFAULT 'CHAT',
  content TEXT NOT NULL,
  sources_json JSON NULL,
  confidence VARCHAR(16) NULL,
  timeliness VARCHAR(255) NULL,
  self_check_status VARCHAR(32) NULL,
  is_active_context BOOLEAN NOT NULL DEFAULT TRUE,
  summary_group_id BIGINT NULL,
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'conversation',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_messages_conversation (conversation_id)
);

CREATE TABLE IF NOT EXISTS conversation_summaries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  summary_text TEXT NOT NULL,
  covered_message_start_id BIGINT NOT NULL,
  covered_message_end_id BIGINT NOT NULL,
  summary_version INT NOT NULL DEFAULT 1,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  source_id VARCHAR(64) NOT NULL DEFAULT 'summary',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_conversation_summaries_conversation (conversation_id)
);

CREATE TABLE IF NOT EXISTS user_memory_profiles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  memory_category VARCHAR(32) NOT NULL,
  memory_key VARCHAR(64) NOT NULL,
  memory_value TEXT NOT NULL,
  value_type VARCHAR(16) NOT NULL DEFAULT 'STRING',
  confidence DECIMAL(8,4) NOT NULL DEFAULT 0.8000,
  source_conversation_id BIGINT NULL,
  source_message_id BIGINT NULL,
  last_used_at DATETIME NULL,
  expires_at DATETIME NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_memory_profiles_user (user_id),
  UNIQUE KEY uk_user_memory_profiles_natural (user_id, memory_category, memory_key, status),
  INDEX idx_user_memory_profiles_lookup (user_id, memory_category, memory_key, status)
);

CREATE TABLE IF NOT EXISTS user_memory_embeddings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_memory_profile_id BIGINT NULL,
  user_id BIGINT NOT NULL,
  memory_text TEXT NOT NULL,
  qdrant_point_id VARCHAR(128) NULL,
  embedding_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  source_conversation_id BIGINT NULL,
  source_message_id BIGINT NULL,
  last_synced_at DATETIME NULL,
  expires_at DATETIME NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_memory_embeddings_user (user_id, status)
);

CREATE TABLE IF NOT EXISTS intelligence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  url VARCHAR(1024) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  confidence DECIMAL(8,4) NOT NULL DEFAULT 0.6000,
  link_id VARCHAR(64) NOT NULL,
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  source_id VARCHAR(64) NOT NULL,
  weight DECIMAL(8,4) NOT NULL DEFAULT 0.6000,
  content_hash VARCHAR(64) NOT NULL,
  sim_hash BIGINT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_intelligence_status (status),
  INDEX idx_intelligence_scope (industry_id, region_id),
  UNIQUE KEY uk_intelligence_hash (content_hash)
);

CREATE TABLE IF NOT EXISTS paid_intelligence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  url VARCHAR(1024) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  confidence DECIMAL(8,4) NOT NULL DEFAULT 0.7500,
  entitlement VARCHAR(32) NOT NULL DEFAULT 'PAID',
  link_id VARCHAR(64) NOT NULL,
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  source_id VARCHAR(64) NOT NULL,
  weight DECIMAL(8,4) NOT NULL DEFAULT 0.9000,
  content_hash VARCHAR(64) NULL,
  sim_hash BIGINT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_paid_intelligence_status (status),
  INDEX idx_paid_intelligence_scope (industry_id, region_id, entitlement)
);

CREATE TABLE IF NOT EXISTS intelligence_snapshots (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_type VARCHAR(16) NOT NULL,
  scope_key VARCHAR(255) NOT NULL,
  payload_json JSON NOT NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'snapshot',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  retention_days INT NOT NULL DEFAULT 30,
  record_count INT NOT NULL DEFAULT 0,
  parent_snapshot_id BIGINT NULL,
  expires_at DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_snapshots_scope (snapshot_type, industry_id, region_id)
);

CREATE TABLE IF NOT EXISTS false_information_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  original_intelligence_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  content_hash VARCHAR(64) NULL,
  url VARCHAR(1024) NULL,
  conflict_reason VARCHAR(64) NOT NULL,
  matched_rumor_keyword VARCHAR(255) NULL,
  source_id VARCHAR(64) NOT NULL,
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  link_id VARCHAR(64) NOT NULL,
  archived_by VARCHAR(64) NOT NULL DEFAULT 'system',
  archive_reason VARCHAR(512) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_false_ledger_reason (conflict_reason),
  INDEX idx_false_ledger_scope (industry_id, region_id),
  INDEX idx_false_ledger_hash (content_hash)
);

CREATE TABLE IF NOT EXISTS conflict_resolutions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  incoming_intelligence_id BIGINT NULL,
  existing_intelligence_id BIGINT NULL,
  conflict_branch VARCHAR(32) NOT NULL,
  sim_hash_distance INT NOT NULL DEFAULT 0,
  incoming_weight DECIMAL(8,4) NOT NULL DEFAULT 0.6000,
  existing_weight DECIMAL(8,4) NOT NULL DEFAULT 0.6000,
  routing_action VARCHAR(64) NOT NULL,
  review_ticket_id BIGINT NULL,
  resolved_by VARCHAR(64) NOT NULL DEFAULT 'system',
  notes VARCHAR(512) NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'conflict-engine',
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  link_id VARCHAR(64) NOT NULL DEFAULT 'unknown',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_conflict_branch (conflict_branch),
  INDEX idx_conflict_scope (industry_id, region_id),
  INDEX idx_conflict_incoming (incoming_intelligence_id)
);

CREATE TABLE IF NOT EXISTS review_work_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  reason VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  owner VARCHAR(64) NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'review',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_review_status (status, reason)
);

CREATE TABLE IF NOT EXISTS alert_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  alert_level VARCHAR(16) NOT NULL,
  component VARCHAR(64) NOT NULL,
  message VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  owner VARCHAR(64) NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'alert',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL DEFAULT 'global',
  industry_id VARCHAR(64) NOT NULL DEFAULT 'global',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_alert_status (status, alert_level)
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor VARCHAR(64) NOT NULL,
  action VARCHAR(128) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  result VARCHAR(32) NOT NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'audit',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL DEFAULT 'global',
  industry_id VARCHAR(64) NOT NULL DEFAULT 'global',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_audit_action (action, actor)
);


CREATE TABLE IF NOT EXISTS admin_intelligence_reviews (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  intelligence_id BIGINT NOT NULL,
  review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  verdict VARCHAR(32) NULL,
  reviewer VARCHAR(64) NULL,
  reason VARCHAR(512) NULL,
  evidence_json JSON NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'admin-review',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL DEFAULT 'global',
  industry_id VARCHAR(64) NOT NULL DEFAULT 'global',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_admin_reviews_status (review_status, verdict),
  INDEX idx_admin_reviews_intelligence (intelligence_id)
);

CREATE TABLE IF NOT EXISTS admin_tickets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_type VARCHAR(64) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'NEW',
  owner VARCHAR(64) NULL,
  next_action VARCHAR(255) NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'admin-ticket',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL DEFAULT 'global',
  industry_id VARCHAR(64) NOT NULL DEFAULT 'global',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_admin_tickets_status (status, severity),
  INDEX idx_admin_tickets_target (target_type, target_id)
);

CREATE TABLE IF NOT EXISTS admin_human_intelligence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  city VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  link_id VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  collector VARCHAR(64) NOT NULL,
  event_time VARCHAR(64) NULL,
  confidence DECIMAL(8,4) NOT NULL DEFAULT 0.7000,
  entitlement VARCHAR(32) NOT NULL DEFAULT 'FREE',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
  reviewer VARCHAR(64) NULL,
  review_notes VARCHAR(512) NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'human-intel',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL DEFAULT 'cn-default',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_admin_human_status (status, entitlement),
  INDEX idx_admin_human_scope (industry_id, region_id)
);

CREATE TABLE IF NOT EXISTS admin_knowledge_nodes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL,
  industry_id VARCHAR(64) NOT NULL DEFAULT 'general',
  region_id VARCHAR(64) NOT NULL DEFAULT 'cn-default',
  link_id VARCHAR(64) NOT NULL DEFAULT 'general',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  draft_version_id BIGINT NULL,
  review_version_id BIGINT NULL,
  published_version_id BIGINT NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'admin-knowledge',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_admin_knowledge_slug_scope (slug, industry_id, region_id, link_id),
  INDEX idx_admin_knowledge_scope (industry_id, region_id, link_id, status)
);

CREATE TABLE IF NOT EXISTS admin_knowledge_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  node_id BIGINT NOT NULL,
  version_number INT NOT NULL,
  title VARCHAR(255) NOT NULL,
  summary VARCHAR(1024) NULL,
  content TEXT NOT NULL,
  source_url VARCHAR(1024) NULL,
  review_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  author VARCHAR(64) NOT NULL,
  reviewer VARCHAR(64) NULL,
  review_notes VARCHAR(512) NULL,
  change_notes VARCHAR(512) NULL,
  confidence DECIMAL(8,4) NOT NULL DEFAULT 0.8500,
  created_by_action VARCHAR(64) NOT NULL DEFAULT 'SAVE_DRAFT',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_admin_knowledge_version (node_id, version_number),
  INDEX idx_admin_knowledge_review (node_id, review_status, version_number)
);

CREATE TABLE IF NOT EXISTS admin_knowledge_publications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  node_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  actor VARCHAR(64) NOT NULL,
  notes VARCHAR(512) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_admin_knowledge_publication_node (node_id, create_time),
  INDEX idx_admin_knowledge_publication_version (version_id, action)
);

CREATE TABLE IF NOT EXISTS admin_collection_sources (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  interval_minutes INT NOT NULL DEFAULT 30,
  max_retries INT NOT NULL DEFAULT 1,
  failure_threshold INT NOT NULL DEFAULT 3,
  cooldown_minutes INT NOT NULL DEFAULT 30,
  circuit_state VARCHAR(32) NOT NULL DEFAULT 'CLOSED',
  failure_count INT NOT NULL DEFAULT 0,
  region_id VARCHAR(64) NOT NULL DEFAULT 'cn-default',
  industry_id VARCHAR(64) NOT NULL DEFAULT 'general',
  link_id VARCHAR(64) NOT NULL DEFAULT 'collection',
  source_id VARCHAR(64) NOT NULL DEFAULT 'admin-collector',
  payload_json JSON NULL,
  compliance_notes JSON NULL,
  proxy_config JSON NULL,
  next_run_time DATETIME NULL,
  last_run_time DATETIME NULL,
  last_status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
  last_error VARCHAR(1024) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_admin_collection_source_status (status, next_run_time),
  INDEX idx_admin_collection_source_circuit (circuit_state, failure_count)
);

CREATE TABLE IF NOT EXISTS admin_collection_keywords (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_config_id BIGINT NOT NULL,
  keyword VARCHAR(255) NOT NULL,
  match_mode VARCHAR(32) NOT NULL DEFAULT 'INCLUDE',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  notes VARCHAR(512) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_admin_collection_keyword_source (source_config_id, status, match_mode)
);

CREATE TABLE IF NOT EXISTS admin_collection_job_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  source_config_id BIGINT NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
  records_collected INT NOT NULL DEFAULT 0,
  records_filtered INT NOT NULL DEFAULT 0,
  records_persisted INT NOT NULL DEFAULT 0,
  error_message VARCHAR(1024) NULL,
  start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finish_time DATETIME NULL,
  INDEX idx_admin_collection_run_source (source_config_id, status, start_time),
  INDEX idx_admin_collection_run_job (job_id)
);

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

CREATE TABLE IF NOT EXISTS report_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  report_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'READY',
  self_check_status VARCHAR(32) NOT NULL DEFAULT 'PASSED',
  sources_json JSON NOT NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'report',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_report_user (user_id, report_type)
);

CREATE TABLE IF NOT EXISTS collection_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  queue_depth INT NOT NULL DEFAULT 0,
  retry_count INT NOT NULL DEFAULT 0,
  circuit_state VARCHAR(32) NOT NULL DEFAULT 'CLOSED',
  source_id VARCHAR(64) NOT NULL DEFAULT 'collector',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL DEFAULT 'global',
  industry_id VARCHAR(64) NOT NULL DEFAULT 'global',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_collection_status (status, source_type)
);

CREATE TABLE IF NOT EXISTS dead_letter_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NULL,
  reason VARCHAR(64) NOT NULL,
  error_message VARCHAR(1024) NOT NULL,
  payload_json JSON NULL,
  source_id VARCHAR(64) NOT NULL DEFAULT 'collector',
  weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  region_id VARCHAR(64) NOT NULL DEFAULT 'global',
  industry_id VARCHAR(64) NOT NULL DEFAULT 'global',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_dead_letter_reason (reason)
);

CREATE TABLE IF NOT EXISTS knowledge_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  source_url VARCHAR(1024) NULL,
  confidence DECIMAL(8,4) NOT NULL DEFAULT 0.8500,
  link_id VARCHAR(64) NOT NULL,
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  source_id VARCHAR(64) NOT NULL,
  weight DECIMAL(8,4) NOT NULL DEFAULT 0.8500,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FULLTEXT KEY ft_knowledge (title, content),
  INDEX idx_knowledge_scope (industry_id, region_id)
);

CREATE TABLE IF NOT EXISTS raw_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  url VARCHAR(1024) NULL,
  confidence DECIMAL(8,4) NOT NULL,
  link_id VARCHAR(64) NOT NULL,
  region_id VARCHAR(64) NOT NULL,
  industry_id VARCHAR(64) NOT NULL,
  source_id VARCHAR(64) NOT NULL,
  weight DECIMAL(8,4) NOT NULL,
  metadata_json JSON NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_raw_records_source (source_type),
  INDEX idx_raw_records_scope (industry_id, region_id)
);

INSERT INTO users (username, password_hash, role, region_id, industry_id)
VALUES
  ('admin', '$2b$10$lM/MgLN.Bm0JQShxjN1Pz.FLsdpdHEGkLpAOXUFnWa1M2lvBRBwpW', 'SUPER_ADMIN', 'cn-default', 'general'),
  ('operator', '$2b$10$lM/MgLN.Bm0JQShxjN1Pz.FLsdpdHEGkLpAOXUFnWa1M2lvBRBwpW', 'OPERATOR', 'cn-default', 'general'),
  ('user', '$2b$10$lM/MgLN.Bm0JQShxjN1Pz.FLsdpdHEGkLpAOXUFnWa1M2lvBRBwpW', 'USER', 'cn-default', 'general'),
  ('seed_paid', '$2b$10$lM/MgLN.Bm0JQShxjN1Pz.FLsdpdHEGkLpAOXUFnWa1M2lvBRBwpW', 'USER', 'cn-default', 'general')
ON DUPLICATE KEY UPDATE username = VALUES(username);

UPDATE users SET membership_level = 'INTERNAL', consultation_preferences = 'operations'
WHERE username = 'admin';
UPDATE users SET membership_level = 'INTERNAL', consultation_preferences = 'review,alerts'
WHERE username = 'operator';
UPDATE users SET membership_level = 'FREE', consultation_preferences = 'cashflow'
WHERE username = 'user';
UPDATE users SET membership_level = 'SEED_PAID', consultation_preferences = 'cashflow,inventory'
WHERE username = 'seed_paid';

INSERT INTO knowledge_items (title, content, source_url, confidence, link_id, region_id, industry_id, source_id, weight)
SELECT 'Restaurant cash-flow baseline diagnosis',
       'Review average order value, table turnover, food waste rate, platform commission, rent ratio, and receivable cycle before drawing conclusions.',
       'seed://v1/restaurant-cashflow',
       0.9000,
       'sales-payment',
       'cn-default',
       'general',
       'seed-baseline',
       1.0000
WHERE NOT EXISTS (
  SELECT 1
  FROM knowledge_items
  WHERE source_url = 'seed://v1/restaurant-cashflow'
);

INSERT INTO knowledge_items (title, content, source_url, confidence, link_id, region_id, industry_id, source_id, weight)
SELECT 'Physical supply-chain inventory risk',
       'Watch inventory days, slow-moving stock ratio, and supplier payment terms because they combine to amplify cash-flow pressure.',
       'seed://v1/inventory-risk',
       0.8500,
       'warehouse',
       'cn-default',
       'general',
       'seed-baseline',
       0.8500
WHERE NOT EXISTS (
  SELECT 1
  FROM knowledge_items
  WHERE source_url = 'seed://v1/inventory-risk'
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
VALUES
  ('Regional cash-flow pressure signal',
   'Multiple local operators report longer account receivable cycles and higher rent pressure.',
   'seed://admin/intelligence/cashflow',
   'PENDING',
   0.6200,
   'sales-payment',
   'cn-default',
   'general',
   'seed-admin',
   0.6200,
   'admin-seed-cashflow')
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO admin_intelligence_reviews (intelligence_id, review_status, reason, evidence_json, region_id, industry_id)
SELECT COALESCE((SELECT MIN(id) FROM intelligence WHERE content_hash = 'admin-seed-cashflow'), 1),
       'PENDING',
       'Needs source and severity review',
       JSON_ARRAY(),
       'cn-default',
       'general'
WHERE NOT EXISTS (
  SELECT 1
  FROM admin_intelligence_reviews
);

INSERT INTO admin_tickets (ticket_type, severity, target_type, target_id, title, description, status, owner, next_action, region_id, industry_id)
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

INSERT INTO admin_human_intelligence (city, industry_id, link_id, content, source_type, collector, event_time, confidence, entitlement, status, region_id)
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

INSERT INTO admin_knowledge_nodes (id, title, slug, industry_id, region_id, link_id, status, published_version_id, source_id, weight)
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
       JSON_OBJECT('url', 'https://example.com/shanghai-rent', 'html', '<html><title>Shanghai Rent Pressure</title><body>Landlords in Shanghai request shorter concession periods and faster payment cycles.</body></html>'),
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
         'items',
         JSON_ARRAY(
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

INSERT INTO knowledge_items (title, content, source_url, confidence, link_id, region_id, industry_id, source_id, weight)
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

INSERT INTO audit_logs (actor, action, target_type, target_id, result)
SELECT 'system', 'ADMIN_V3_BOOTSTRAP', 'admin', 'seed', 'SUCCESS'
WHERE NOT EXISTS (
  SELECT 1
  FROM audit_logs
  WHERE action = 'ADMIN_V3_BOOTSTRAP'
    AND target_id = 'seed'
);

CREATE TABLE IF NOT EXISTS question_pools (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  industry_id VARCHAR(64) NOT NULL,
  region_id VARCHAR(64) NOT NULL DEFAULT 'cn-default',
  agent_mode VARCHAR(32) NOT NULL,
  question_key VARCHAR(128) NOT NULL,
  category VARCHAR(64) NOT NULL,
  question_text VARCHAR(512) NOT NULL,
  top_level_score DECIMAL(8,4) NOT NULL DEFAULT 0.5000,
  usage_count BIGINT NOT NULL DEFAULT 0,
  rating_avg DECIMAL(8,4) NOT NULL DEFAULT 0.0000,
  rating_count BIGINT NOT NULL DEFAULT 0,
  last_used_at TIMESTAMP NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  source_type VARCHAR(32) NOT NULL DEFAULT 'seed',
  source_ref VARCHAR(128) NOT NULL DEFAULT '',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_question_pools_scope (industry_id, region_id, agent_mode, question_key)
);

INSERT INTO question_pools
  (industry_id, region_id, agent_mode, question_key, category, question_text, top_level_score, source_type, source_ref)
SELECT 'general', 'cn-default', 'LEARNING', 'raw-materials-next', 'node',
       '先了解原材料成本结构和采购周期。', 0.92, 'seed', 'raw-materials'
WHERE NOT EXISTS (
  SELECT 1 FROM question_pools
  WHERE industry_id = 'general' AND region_id = 'cn-default'
    AND agent_mode = 'LEARNING' AND question_key = 'raw-materials-next'
);

INSERT INTO question_pools
  (industry_id, region_id, agent_mode, question_key, category, question_text, top_level_score, source_type, source_ref)
SELECT 'general', 'cn-default', 'DIAGNOSIS', 'baseline-industry', 'profile',
       '先确认你属于哪个行业，以及是线上还是线下业务。', 0.95, 'seed', 'diagnosis-profile'
WHERE NOT EXISTS (
  SELECT 1 FROM question_pools
  WHERE industry_id = 'general' AND region_id = 'cn-default'
    AND agent_mode = 'DIAGNOSIS' AND question_key = 'baseline-industry'
);

INSERT INTO question_pools
  (industry_id, region_id, agent_mode, question_key, category, question_text, top_level_score, source_type, source_ref)
SELECT 'general', 'cn-default', 'DIAGNOSIS', 'baseline-scale', 'profile',
       '大概的门店、仓库和团队规模是多少？', 0.93, 'seed', 'diagnosis-profile'
WHERE NOT EXISTS (
  SELECT 1 FROM question_pools
  WHERE industry_id = 'general' AND region_id = 'cn-default'
    AND agent_mode = 'DIAGNOSIS' AND question_key = 'baseline-scale'
);

INSERT INTO admin_risk_rules (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
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

INSERT INTO admin_risk_rules (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
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

INSERT INTO admin_risk_rules (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
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

INSERT INTO admin_risk_rules (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
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

INSERT INTO admin_risk_rules (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
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

INSERT INTO admin_risk_rules (rule_type, name, description, enabled, threshold_value, scope_json, risk_level, change_mode)
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
