-- 판단 실행이 사람에게 묻고(ask) 답을 받아 이어서 돈다 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 판단 실행(마스터·그룹 리더)의 계약에 세 번째 행동이 생겼다:
--   {"action":"ask","question":"…","options":["…"]}
-- 그러면 그 실행은 끝나지 않고 WAITING_INPUT 으로 멈춘다. 질문은 execution.question 에 남기고,
-- 사람이 답을 넣으면(POST /executions/{id}/answer) execution.answer 에 남긴 뒤 같은 실행의 판단 루프를
-- 이어서 돈다(질문·답이 다음 스텝 프롬프트에 붙는다).
--
-- 상태 컬럼(execution.status)은 VARCHAR(32) 이고 제약이 없어 WAITING_INPUT 을 그대로 담을 수 있다.
ALTER TABLE execution ADD COLUMN IF NOT EXISTS question TEXT;
ALTER TABLE execution ADD COLUMN IF NOT EXISTS answer TEXT;
