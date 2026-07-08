CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL,
  phone_encrypted VARCHAR(255) NULL,
  identity_encrypted VARCHAR(255) NULL,
  membership_level VARCHAR(32) NOT NULL DEFAULT 'FREE',
  consultation_preferences VARCHAR(255) NOT NULL DEFAULT '',
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
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_snapshots_scope (snapshot_type, industry_id, region_id)
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
VALUES
  ('餐饮门店现金流基础诊断', '餐饮门店诊断应优先核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期。若缺少经营数据，应提示信息不足并引导补充。', 'seed://v1/restaurant-cashflow', 0.9, 'sales-payment', 'cn-default', 'general', 'seed-baseline', 1.0),
  ('实体供应链库存风险', '库存周转天数、呆滞库存占比和上游账期会共同影响现金流风险。库存积压会压占资金，并倒逼渠道低价清货。', 'seed://v1/inventory-risk', 0.85, 'warehouse', 'cn-default', 'general', 'seed-baseline', 0.85)
ON DUPLICATE KEY UPDATE title = VALUES(title);

UPDATE knowledge_items
SET
  title = '餐饮门店现金流基础诊断',
  content = '餐饮门店诊断应优先核对客单价、翻台率、食材损耗率、平台佣金、租金占营收比例和现金回款周期。若缺少经营数据，应提示信息不足并引导补充。'
WHERE source_url = 'seed://v1/restaurant-cashflow';

UPDATE knowledge_items
SET
  title = '实体供应链库存风险',
  content = '库存周转天数、滞销库存占比和上游账期会共同影响现金流风险。库存积压会压占资金，并倒逼渠道低价清货。'
WHERE source_url = 'seed://v1/inventory-risk';
