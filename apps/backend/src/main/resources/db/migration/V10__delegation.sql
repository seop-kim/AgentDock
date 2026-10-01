-- 위임(실행 트리)과 계약·지표 저장 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 목업에서 확정한 실행 흐름을 실제로 담는다:
--   명령 하나 = 실행 트리 하나(루트 = 명령을 받은 에이전트, 보통 마스터)
--   판단 실행(마스터·리더)은 계약(delegate | done)을 남기고, 위임 대상이 남는다
--   자식이 도는 동안 부모는 WAITING_CHILD 로 멈췄다가 끝나면 다시 RUNNING 이 된다
--   지표는 CLI `--output-format json` 이 실제로 돌려주는 값(실측 확인함)을 그대로 저장한다

-- 1) 프로젝트 마스터 에이전트 + 마스터 프롬프트 (프롬프트 계층의 맨 위)
ALTER TABLE project ADD COLUMN IF NOT EXISTS master_agent_id BIGINT REFERENCES agent(id);
ALTER TABLE project ADD COLUMN IF NOT EXISTS master_prompt TEXT NOT NULL DEFAULT '';

-- 2) 그룹 프롬프트 (마스터 프롬프트 아래, 에이전트 프롬프트 위)
ALTER TABLE agent_group ADD COLUMN IF NOT EXISTS prompt TEXT NOT NULL DEFAULT '';

-- 3) 실행 트리
ALTER TABLE execution ADD COLUMN IF NOT EXISTS parent_execution_id BIGINT REFERENCES execution(id);
ALTER TABLE execution ADD COLUMN IF NOT EXISTS root_execution_id BIGINT REFERENCES execution(id);

-- 4) 계약과 결과 (판단 실행이 낸 마지막 계약 / 작업 실행이 남긴 결과)
ALTER TABLE execution ADD COLUMN IF NOT EXISTS decision VARCHAR(20);
ALTER TABLE execution ADD COLUMN IF NOT EXISTS delegated_target_agent_id BIGINT REFERENCES agent(id);
ALTER TABLE execution ADD COLUMN IF NOT EXISTS result_text TEXT;
ALTER TABLE execution ADD COLUMN IF NOT EXISTS handoff JSONB;

-- 5) 계측값 (CLI --output-format json 의 usage / total_cost_usd / duration_ms / session_id / num_turns)
ALTER TABLE execution ADD COLUMN IF NOT EXISTS input_tokens INT;
ALTER TABLE execution ADD COLUMN IF NOT EXISTS output_tokens INT;
ALTER TABLE execution ADD COLUMN IF NOT EXISTS cache_read_tokens INT;
ALTER TABLE execution ADD COLUMN IF NOT EXISTS cache_creation_tokens INT;
ALTER TABLE execution ADD COLUMN IF NOT EXISTS cost_usd NUMERIC(12,6);
ALTER TABLE execution ADD COLUMN IF NOT EXISTS duration_ms BIGINT;
ALTER TABLE execution ADD COLUMN IF NOT EXISTS num_turns INT;
ALTER TABLE execution ADD COLUMN IF NOT EXISTS session_id VARCHAR(64);

-- 6) 트리 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_execution_root ON execution(root_execution_id);
CREATE INDEX IF NOT EXISTS idx_execution_parent ON execution(parent_execution_id);
