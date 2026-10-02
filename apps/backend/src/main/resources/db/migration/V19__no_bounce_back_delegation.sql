-- 프롬프트에 "위로 되돌려 맡기지 말 것"을 덧붙인다 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 실측(2026-10-02): 자식 실행이 자기 리더에게 되돌려 맡기려다 순환 위임으로 거부되고 실패했다.
-- 순환 차단은 백엔드가 하지만, 시도 자체가 실행 하나를 통째로 날린다 — 프롬프트에서 막는다.
--
-- 프롬프트는 **DB 데이터**다(코드가 아니다). 기존 문장은 그대로 두고 끝에만 덧붙이며, 이미 들어 있으면
-- 다시 넣지 않는다(문장 일부를 표식으로 본다). 대상은 AgentDock 프로젝트와 그 프로젝트의 그룹 프롬프트다.

-- 1) 마스터 프롬프트(프로젝트)
UPDATE project
SET master_prompt = master_prompt
    || CASE WHEN position('되돌려 맡기지 않는다' IN master_prompt) = 0
            THEN E'\n위임은 다른 팀·다른 에이전트에게만 한다. 자기 자신이나 나에게 일을 맡긴 상대(리더·부모)에게 되돌려 맡기지 않는다 — 순환 위임은 거부된다. 스스로 판단할 수 없으면 추측하지 말고 ask 로 사람에게 묻는다.'
            ELSE '' END
WHERE name = 'AgentDock'
  AND position('되돌려 맡기지 않는다' IN master_prompt) = 0;

-- 2) 그룹 프롬프트(그 프로젝트의 그룹 전부)
UPDATE agent_group
SET prompt = prompt
    || CASE WHEN position('되돌려 맡기지 않는다' IN prompt) = 0
            THEN E'\n위임은 다른 팀·다른 에이전트에게만 한다. 자기 자신이나 나에게 일을 맡긴 상대(리더·부모)에게 되돌려 맡기지 않는다 — 순환 위임은 거부된다. 스스로 판단할 수 없으면 추측하지 말고 ask 로 사람에게 묻는다.'
            ELSE '' END
WHERE project_id IN (SELECT id FROM project WHERE name = 'AgentDock')
  AND position('되돌려 맡기지 않는다' IN prompt) = 0;
