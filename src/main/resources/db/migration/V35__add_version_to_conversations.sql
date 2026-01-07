ALTER TABLE conversations ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE conversation_messages ADD COLUMN version BIGINT DEFAULT 0;
