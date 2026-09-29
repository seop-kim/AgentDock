-- Provider 논리 삭제
ALTER TABLE ai_provider ADD COLUMN deleted_at TIMESTAMPTZ;

-- key 유일성은 삭제되지 않은 행에만 적용한다(삭제한 key 를 다시 등록할 수 있게)
ALTER TABLE ai_provider DROP CONSTRAINT ai_provider_key_key;
CREATE UNIQUE INDEX ai_provider_key_active ON ai_provider (key) WHERE deleted_at IS NULL;

-- 4종 Provider 시드: 같은 key 의 행이 하나도 없을 때만 넣는다
INSERT INTO ai_provider (key, name)
SELECT v.key, v.name
FROM (VALUES
    ('CLAUDE_CODE', 'Claude Code'),
    ('CODEX', 'Codex'),
    ('COMMAND_CODE', 'Command Code'),
    ('GEMINI', 'Gemini')
) AS v(key, name)
WHERE NOT EXISTS (SELECT 1 FROM ai_provider p WHERE p.key = v.key);

-- Provider 당 Connection 1개: 없는 Provider 에만 만든다
INSERT INTO ai_connection (provider_id)
SELECT p.id
FROM ai_provider p
WHERE p.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM ai_connection c WHERE c.provider_id = p.id);
