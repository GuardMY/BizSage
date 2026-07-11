SET @sql := IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversations'
      AND column_name = 'agent_mode'
  ),
  'SELECT 1',
  'ALTER TABLE conversations ADD COLUMN agent_mode VARCHAR(32) NOT NULL DEFAULT ''DIAGNOSIS'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversations'
      AND column_name = 'workflow_stage'
  ),
  'SELECT 1',
  'ALTER TABLE conversations ADD COLUMN workflow_stage VARCHAR(32) NOT NULL DEFAULT ''INTRO'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversations'
      AND column_name = 'profile_completeness'
  ),
  'SELECT 1',
  'ALTER TABLE conversations ADD COLUMN profile_completeness DECIMAL(5,2) NOT NULL DEFAULT 0.00'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversations'
      AND column_name = 'primary_issue_tags'
  ),
  'SELECT 1',
  'ALTER TABLE conversations ADD COLUMN primary_issue_tags TEXT NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversations'
      AND column_name = 'recommended_question_ids'
  ),
  'SELECT 1',
  'ALTER TABLE conversations ADD COLUMN recommended_question_ids TEXT NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversations'
      AND column_name = 'closed_by'
  ),
  'SELECT 1',
  'ALTER TABLE conversations ADD COLUMN closed_by VARCHAR(64) NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'conversations'
      AND column_name = 'closed_reason'
  ),
  'SELECT 1',
  'ALTER TABLE conversations ADD COLUMN closed_reason VARCHAR(255) NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
       '先了解原材料成本结构和采购周期', 0.92, 'seed', 'raw-materials'
WHERE NOT EXISTS (
  SELECT 1 FROM question_pools
  WHERE industry_id = 'general' AND region_id = 'cn-default'
    AND agent_mode = 'LEARNING' AND question_key = 'raw-materials-next'
);

INSERT INTO question_pools
  (industry_id, region_id, agent_mode, question_key, category, question_text, top_level_score, source_type, source_ref)
SELECT 'general', 'cn-default', 'DIAGNOSIS', 'baseline-industry', 'profile',
       '先确认你属于哪个行业，以及是线上还是线下业务', 0.95, 'seed', 'diagnosis-profile'
WHERE NOT EXISTS (
  SELECT 1 FROM question_pools
  WHERE industry_id = 'general' AND region_id = 'cn-default'
    AND agent_mode = 'DIAGNOSIS' AND question_key = 'baseline-industry'
);

INSERT INTO question_pools
  (industry_id, region_id, agent_mode, question_key, category, question_text, top_level_score, source_type, source_ref)
SELECT 'general', 'cn-default', 'DIAGNOSIS', 'baseline-scale', 'profile',
       '大概的门店、仓库和团队规模是多少', 0.93, 'seed', 'diagnosis-profile'
WHERE NOT EXISTS (
  SELECT 1 FROM question_pools
  WHERE industry_id = 'general' AND region_id = 'cn-default'
    AND agent_mode = 'DIAGNOSIS' AND question_key = 'baseline-scale'
);
