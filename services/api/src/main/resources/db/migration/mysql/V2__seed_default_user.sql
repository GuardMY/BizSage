INSERT INTO users (
  username,
  password_hash,
  role
)
SELECT
  'user',
  '$2a$10$hOkQN5T5EiORuZb9yOyrX.0BuRHn729A5WgOMlLq4XKLRTOUQpV6q',
  'USER'
WHERE NOT EXISTS (
  SELECT 1
  FROM users
  WHERE username = 'user'
);
