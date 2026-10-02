-- 프롬프트에 "작업 디렉터리(워크스페이스) 밖은 건드리지 말 것"을 덧붙인다 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 배경: 워크트리 격리는 '작업 디렉터리'를 나눌 뿐, CLI 자체는 기계 어디든 읽고 쓸 수 있다.
-- OS 수준 샌드박스(별도 계정·컨테이너)는 아직 없으므로, 최소한 프롬프트로 범위를 못 박는다.
--
-- 프롬프트는 **DB 데이터**다(코드가 아니다). 기존 문장은 그대로 두고 끝에만 덧붙이며, 이미 들어 있으면
-- 다시 넣지 않는다(문장 일부를 표식으로 본다). 대상은 AgentDock 프로젝트와 그 프로젝트의 그룹 프롬프트다.

-- 1) 마스터 프롬프트(프로젝트)
UPDATE project
SET master_prompt = master_prompt
    || CASE WHEN position('밖의 파일은 읽지도 쓰지도 않는다' IN master_prompt) = 0
            THEN E'\n작업 디렉터리(워크스페이스) 밖의 파일은 읽지도 쓰지도 않는다. 밖에 있는 것이 필요하면 추측해서 열지 말고 사람에게 물어 위치를 확인한다.'
            ELSE '' END
WHERE name = 'AgentDock'
  AND position('밖의 파일은 읽지도 쓰지도 않는다' IN master_prompt) = 0;

-- 2) 그룹 프롬프트(그 프로젝트의 그룹 전부)
UPDATE agent_group
SET prompt = prompt
    || CASE WHEN position('밖의 파일은 읽지도 쓰지도 않는다' IN prompt) = 0
            THEN E'\n작업 디렉터리(워크스페이스) 밖의 파일은 읽지도 쓰지도 않는다. 밖에 있는 것이 필요하면 추측해서 열지 말고 사람에게 물어 위치를 확인한다.'
            ELSE '' END
WHERE project_id IN (SELECT id FROM project WHERE name = 'AgentDock')
  AND position('밖의 파일은 읽지도 쓰지도 않는다' IN prompt) = 0;
