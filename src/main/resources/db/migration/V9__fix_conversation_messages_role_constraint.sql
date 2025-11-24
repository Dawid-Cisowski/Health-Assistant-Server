-- Fix conversation_messages role constraint to accept uppercase values (USER, ASSISTANT)
-- This migration is needed because V8 was initially deployed with lowercase values

-- Drop the old constraint
ALTER TABLE conversation_messages DROP CONSTRAINT IF EXISTS conversation_messages_role_check;

-- Add new constraint with uppercase values to match Java enum
ALTER TABLE conversation_messages ADD CONSTRAINT conversation_messages_role_check CHECK (role IN ('USER', 'ASSISTANT'));
