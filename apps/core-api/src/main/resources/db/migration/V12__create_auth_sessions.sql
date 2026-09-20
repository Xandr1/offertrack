CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    last_refreshed_at TIMESTAMPTZ NOT NULL,
    inactivity_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(32),
    CHECK (created_at <= last_refreshed_at
           AND last_refreshed_at < inactivity_expires_at
           AND inactivity_expires_at <= absolute_expires_at),
    CHECK ((revoked_at IS NULL AND revocation_reason IS NULL)
           OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL
               AND revocation_reason IN ('logout', 'logout_all', 'password_reset',
                   'oauth_account_claim', 'refresh_replay', 'session_limit')))
);

CREATE INDEX auth_sessions_active_user_idx
    ON auth_sessions(user_id, last_refreshed_at, created_at, id)
    WHERE revoked_at IS NULL;
CREATE INDEX auth_sessions_cleanup_idx
    ON auth_sessions(LEAST(revoked_at, inactivity_expires_at, absolute_expires_at), id);

CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    token_hash BYTEA NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > issued_at),
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX auth_refresh_tokens_session_idx ON auth_refresh_tokens(session_id, id);
CREATE UNIQUE INDEX auth_refresh_tokens_current_idx ON auth_refresh_tokens(session_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
