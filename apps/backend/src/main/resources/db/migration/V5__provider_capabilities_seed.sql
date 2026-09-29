-- Provider 별 지원 모델/모드 목록을 데이터로 시드한다(화면에서 편집하며 코드에 목록을 두지 않는다).
-- 이미 값이 있는 행(사용자가 편집한 행)은 건드리지 않는다.
UPDATE ai_provider
SET capabilities = '{"models": ["opus", "sonnet", "fable"], "modes": ["acceptEdits", "auto", "bypassPermissions", "manual", "dontAsk", "plan"], "notes": "models 는 `claude --help` 에 나온 alias 예시이며 CLI 버전에 따라 바뀐다. 화면에서 편집한다."}'::jsonb
WHERE key = 'CLAUDE_CODE' AND capabilities = '{}'::jsonb;

UPDATE ai_provider
SET capabilities = '{"models": [], "modes": [], "notes": "CLI 가 이 머신에 없어 미확인. 화면에서 입력한다."}'::jsonb
WHERE key IN ('CODEX', 'COMMAND_CODE', 'GEMINI') AND capabilities = '{}'::jsonb;
