SET @users_preferred_locale_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'preferred_locale'
);

SET @users_preferred_locale_sql = IF(
  @users_preferred_locale_exists = 0,
  'ALTER TABLE users ADD COLUMN preferred_locale VARCHAR(16) NOT NULL DEFAULT ''zh-CN'' AFTER consultation_preferences',
  'SELECT 1'
);

PREPARE users_preferred_locale_stmt FROM @users_preferred_locale_sql;
EXECUTE users_preferred_locale_stmt;
DEALLOCATE PREPARE users_preferred_locale_stmt;
