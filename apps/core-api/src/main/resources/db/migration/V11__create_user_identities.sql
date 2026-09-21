CREATE TABLE user_identities (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL CHECK (provider = 'google'),
    provider_subject VARCHAR(255) COLLATE "C" NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (provider, provider_subject),
    UNIQUE (user_id, provider),
    CHECK (char_length(provider_subject) BETWEEN 1 AND 255
           AND octet_length(provider_subject) = char_length(provider_subject)
           AND provider_subject !~ '^[[:space:]]*$')
);
