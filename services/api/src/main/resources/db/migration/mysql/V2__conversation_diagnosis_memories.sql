CREATE TABLE IF NOT EXISTS conversation_diagnosis_memories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  memory_category VARCHAR(64) NOT NULL,
  memory_key VARCHAR(128) NOT NULL,
  memory_value TEXT NOT NULL,
  confidence DECIMAL(8,4) NOT NULL DEFAULT 0.5000,
  structured BOOLEAN NOT NULL DEFAULT TRUE,
  source_message_id BIGINT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_conversation_diagnosis_memory (conversation_id, memory_category, memory_key, status),
  INDEX idx_conversation_diagnosis_memory_conversation (conversation_id)
);
