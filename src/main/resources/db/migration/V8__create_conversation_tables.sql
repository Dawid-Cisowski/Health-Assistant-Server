-- Create conversations table for AI assistant conversation tracking
CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on device_id for fast lookup of user conversations
CREATE INDEX idx_conversations_device_id ON conversations(device_id);

-- Create index on updated_at for finding recent conversations
CREATE INDEX idx_conversations_updated_at ON conversations(updated_at DESC);

-- Create conversation_messages table for storing message history
CREATE TABLE conversation_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant')),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create composite index for efficient history retrieval (most recent first)
CREATE INDEX idx_conversation_messages_conversation_created
    ON conversation_messages(conversation_id, created_at DESC);

-- Add comment for documentation
COMMENT ON TABLE conversations IS 'Stores AI assistant conversation sessions per device';
COMMENT ON TABLE conversation_messages IS 'Stores individual messages within conversations (user and assistant)';
COMMENT ON COLUMN conversation_messages.role IS 'Message sender: user or assistant';
