-- PostgreSQL LEAST ignores NULL: unconsumed tokens become terminal at expires_at.
-- Rollback: DROP INDEX IF EXISTS user_auth_tokens_cleanup_idx;
CREATE INDEX user_auth_tokens_cleanup_idx
    ON user_auth_tokens (LEAST(consumed_at, expires_at), id);
