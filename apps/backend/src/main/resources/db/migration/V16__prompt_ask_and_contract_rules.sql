-- AgentDock 프로젝트의 프롬프트에 계약 규칙을 덧붙인다 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 1) 사람에게 묻기(ask): 혼자 정할 수 없는 갈림길은 추측하지 말고 계약 ask 로 사람에게 묻는다.
-- 2) 계약으로만 위임: CLI 자체의 서브에이전트·메시지 도구(Task, SendMessage 등)를 쓰면 우리가 실행 트리로
--    추적할 수 없다. 위임은 반드시 계약(delegate)으로만 한다.
--
-- 프롬프트는 **DB 데이터**다(코드가 아니다). 기존 문장은 그대로 두고 끝에만 덧붙이며, 이미 들어 있으면
-- 다시 넣지 않는다(문장 일부를 표식으로 본다). 대상은 AgentDock 프로젝트와 그 프로젝트의 그룹 프롬프트다.

-- 1) 마스터 프롬프트(프로젝트)
UPDATE project
SET master_prompt = master_prompt
    || CASE WHEN position('혼자 정할 수 없는 갈림길' IN master_prompt) = 0
            THEN E'\n혼자 정할 수 없는 갈림길(범위·취향·되돌리기 어려운 변경)은 ask 로 사람에게 묻고, 답을 받은 뒤 이어서 진행한다. 추측으로 정하지 않는다.'
            ELSE '' END
    || CASE WHEN position('다른 에이전트에게 일을 맡길 때는' IN master_prompt) = 0
            THEN E'\n다른 에이전트에게 일을 맡길 때는 반드시 계약(delegate)으로만 한다. CLI 자체의 서브에이전트·메시지 도구(Task, SendMessage 등)는 쓰지 않는다.'
            ELSE '' END
WHERE name = 'AgentDock'
  AND (position('혼자 정할 수 없는 갈림길' IN master_prompt) = 0
       OR position('다른 에이전트에게 일을 맡길 때는' IN master_prompt) = 0);

-- 2) 그룹 프롬프트(그 프로젝트의 그룹 전부)
UPDATE agent_group
SET prompt = prompt
    || CASE WHEN position('혼자 정할 수 없는 갈림길' IN prompt) = 0
            THEN E'\n혼자 정할 수 없는 갈림길(범위·취향·되돌리기 어려운 변경)은 ask 로 사람에게 묻고, 답을 받은 뒤 이어서 진행한다. 추측으로 정하지 않는다.'
            ELSE '' END
    || CASE WHEN position('다른 에이전트에게 일을 맡길 때는' IN prompt) = 0
            THEN E'\n다른 에이전트에게 일을 맡길 때는 반드시 계약(delegate)으로만 한다. CLI 자체의 서브에이전트·메시지 도구(Task, SendMessage 등)는 쓰지 않는다.'
            ELSE '' END
WHERE project_id IN (SELECT id FROM project WHERE name = 'AgentDock')
  AND (position('혼자 정할 수 없는 갈림길' IN prompt) = 0
       OR position('다른 에이전트에게 일을 맡길 때는' IN prompt) = 0);
