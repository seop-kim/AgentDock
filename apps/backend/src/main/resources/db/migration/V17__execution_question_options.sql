-- `ask` 계약의 보기(options)를 화면에 버튼으로 그릴 수 있게 저장한다 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 판단 실행이 사람에게 물을 때 계약에 선택 항목을 함께 줄 수 있다:
--   {"action":"ask","question":"…","options":["…","…"]}
-- 지금까지는 이 보기를 실행 로그(SYSTEM)로만 흘려보내 화면에서 다시 그릴 수 없었다. 질문(question)과 함께
-- 실행 행에 남겨, 입력 대기 배너·캔버스 말풍선이 보기를 버튼으로 보여 주고 그대로 답할 수 있게 한다.
ALTER TABLE execution ADD COLUMN IF NOT EXISTS question_options JSONB;
