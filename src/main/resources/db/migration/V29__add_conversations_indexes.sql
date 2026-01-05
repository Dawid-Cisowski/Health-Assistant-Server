CREATE INDEX IF NOT EXISTS idx_conversations_updated_at ON conversations(updated_at);
CREATE INDEX IF NOT EXISTS idx_conversations_device_id ON conversations(device_id);
CREATE INDEX IF NOT EXISTS idx_conversation_messages_conversation_id_created_at ON conversation_messages(conversation_id, created_at DESC);
