-- 런타임 CLI 설치 지원 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
-- 1) 확인 결과에 "CLI 자체가 없음"을 표시해 화면에서 설치를 안내할 수 있게 한다.
ALTER TABLE workspace_runtime_status ADD COLUMN IF NOT EXISTS cli_missing BOOLEAN NOT NULL DEFAULT false;

-- 2) 런타임별 설치 계획을 capabilities 에 시드/변환한다.
--    설치 계획 = installRequire(먼저 있어야 하는 실행 파일) + installPrerequisite(없을 때 먼저 설치) + install(본 설치)
--    npm 패키지는 실제 존재 확인: @anthropic-ai/claude-code / @openai/codex / @google/gemini-cli / command-code
--    필수 도구(npm/Node)가 없으면 nvm 으로 먼저 설치한다(이 머신에서 nvm 1.2.2 확인).
--    설치 단계는 셸로 감싸 실행하므로 .cmd/.ps1 실행 파일과 PATH 를 그대로 쓸 수 있다.
--    대상: 설치 계획이 아예 없는 행, 또는 예전 형식(문자열)으로 저장된 행. 사용자가 직접 편집한 배열 값은 건드리지 않는다.

UPDATE ai_provider
SET capabilities = (capabilities - 'install') || '{
  "installRequire": "npm",
  "installPrerequisite": ["nvm install lts", "nvm use lts"],
  "install": ["npm install -g @anthropic-ai/claude-code"]
}'::jsonb
WHERE key = 'CLAUDE_CODE'
  AND (NOT (capabilities ? 'install') OR jsonb_typeof(capabilities -> 'install') = 'string');

UPDATE ai_provider
SET capabilities = (capabilities - 'install') || '{
  "installRequire": "npm",
  "installPrerequisite": ["nvm install lts", "nvm use lts"],
  "install": ["npm install -g @openai/codex"]
}'::jsonb
WHERE key = 'CODEX'
  AND (NOT (capabilities ? 'install') OR jsonb_typeof(capabilities -> 'install') = 'string');

UPDATE ai_provider
SET capabilities = (capabilities - 'install') || '{
  "installRequire": "npm",
  "installPrerequisite": ["nvm install lts", "nvm use lts"],
  "install": ["npm install -g command-code"]
}'::jsonb
WHERE key = 'COMMAND_CODE'
  AND (NOT (capabilities ? 'install') OR jsonb_typeof(capabilities -> 'install') = 'string');

UPDATE ai_provider
SET capabilities = (capabilities - 'install') || '{
  "installRequire": "npm",
  "installPrerequisite": ["nvm install lts", "nvm use lts"],
  "install": ["npm install -g @google/gemini-cli"]
}'::jsonb
WHERE key = 'GEMINI'
  AND (NOT (capabilities ? 'install') OR jsonb_typeof(capabilities -> 'install') = 'string');
