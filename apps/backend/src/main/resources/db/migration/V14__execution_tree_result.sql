-- 실행 트리 결과를 메인 저장소로 되돌린 결과를 기록한다 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 트리(루트 실행)가 끝나면 그 워크트리의 변경을 트리 브랜치에 커밋 하나로 남기고, 메인 저장소가 깨끗하면
-- 자동으로 병합한다. 병합하지 못하면(더럽거나 충돌) 사유와 함께 수동 병합 대상으로 남긴다.
-- 이 값은 되돌린 결과이므로 **루트 실행에만** 기록한다(트리 결과는 루트의 것이다).
ALTER TABLE execution ADD COLUMN IF NOT EXISTS result_commit VARCHAR(64);
ALTER TABLE execution ADD COLUMN IF NOT EXISTS merge_status VARCHAR(20);
ALTER TABLE execution ADD COLUMN IF NOT EXISTS merge_detail TEXT;
ALTER TABLE execution ADD COLUMN IF NOT EXISTS changed_files JSONB;
