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

CREATE TABLE IF NOT EXISTS question_pools (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  industry_id VARCHAR(64) NOT NULL,
  region_id VARCHAR(64) NOT NULL,
  agent_mode VARCHAR(32) NOT NULL,
  question_key VARCHAR(128) NOT NULL,
  category VARCHAR(64) NULL,
  question_text VARCHAR(1024) NOT NULL,
  top_level_score DOUBLE NOT NULL DEFAULT 0.5000,
  usage_count BIGINT NOT NULL DEFAULT 0,
  rating_avg DOUBLE NOT NULL DEFAULT 0.0000,
  rating_count BIGINT NOT NULL DEFAULT 0,
  last_used_at DATETIME NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  source_type VARCHAR(64) NOT NULL DEFAULT 'seed',
  source_ref VARCHAR(255) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_question_pools_scope_key (industry_id, region_id, agent_mode, question_key),
  INDEX idx_question_pools_lookup (industry_id, region_id, agent_mode, status),
  INDEX idx_question_pools_rank (top_level_score, usage_count, rating_avg, last_used_at)
);
