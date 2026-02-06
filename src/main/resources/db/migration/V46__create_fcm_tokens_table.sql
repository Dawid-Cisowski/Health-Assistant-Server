CREATE TABLE fcm_tokens (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(128) NOT NULL UNIQUE,
    token TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_fcm_tokens_active ON fcm_tokens (active) WHERE active = TRUE;
