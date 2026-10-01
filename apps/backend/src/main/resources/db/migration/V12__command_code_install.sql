-- Command Code 설치 명령 정정 (사용자 확인: npm i -g command-code@latest)
--
-- V9 는 네 런타임의 설치 계획을 시드했다. 그중 Command Code 의 패키지 이름이 틀려 정정한다.
-- 화면에서 사용자가 직접 고친 값은 건드리지 않는다(시드 값 그대로일 때만 바꾼다).
UPDATE ai_provider
SET capabilities = capabilities || '{"install": ["npm i -g command-code@latest"]}'::jsonb
WHERE key = 'COMMAND_CODE'
  AND capabilities -> 'install' = '["npm install -g command-code"]'::jsonb;
