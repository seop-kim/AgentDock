ALTER TABLE ai_connection ADD COLUMN last_checked_at TIMESTAMPTZ;
ALTER TABLE ai_connection ADD COLUMN last_error TEXT;
