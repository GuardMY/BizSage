-- Normalize existing user memory duplicates before enforcing natural-key uniqueness.
-- Keep one row per (user_id, memory_category, memory_key, status), preferring the
-- most recently updated record and then the highest id as a deterministic tie-breaker.

DELETE ump
FROM user_memory_profiles ump
JOIN user_memory_profiles newer
  ON newer.user_id = ump.user_id
 AND newer.memory_category = ump.memory_category
 AND newer.memory_key = ump.memory_key
 AND newer.status = ump.status
 AND (
      newer.update_time > ump.update_time
      OR (newer.update_time = ump.update_time AND newer.id > ump.id)
 )
WHERE newer.id IS NOT NULL;

SET @user_memory_profiles_natural_unique_exists = (
  SELECT COUNT(*)
  FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user_memory_profiles'
      AND non_unique = 0
    GROUP BY index_name
    HAVING COUNT(*) = 4
       AND GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') = 'user_id,memory_category,memory_key,status'
  ) existing_unique_indexes
);

SET @uk_user_memory_profiles_natural_sql = IF(
  @user_memory_profiles_natural_unique_exists = 0,
  'ALTER TABLE user_memory_profiles ADD CONSTRAINT uk_user_memory_profiles_natural UNIQUE (user_id, memory_category, memory_key, status)',
  'SELECT 1'
);

PREPARE uk_user_memory_profiles_natural_stmt FROM @uk_user_memory_profiles_natural_sql;
EXECUTE uk_user_memory_profiles_natural_stmt;
DEALLOCATE PREPARE uk_user_memory_profiles_natural_stmt;
