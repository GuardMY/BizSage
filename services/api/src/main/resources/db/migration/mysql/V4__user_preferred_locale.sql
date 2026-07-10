ALTER TABLE users
  ADD COLUMN preferred_locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN' AFTER consultation_preferences;
