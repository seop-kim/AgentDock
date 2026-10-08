-- AgentDock 프로젝트에 '디자인 팀'과 그 에이전트 3명을 만든다 (멱등: 이미 있으면 아무것도 하지 않는다)
--
-- 기존 팀과 같은 규칙을 따른다:
--  - 런타임은 Command Code(ai_provider.key = 'COMMAND_CODE'), 모델은 deepseek/deepseek-v4-flash
--  - 권한 프로필은 '개발'(읽기·쓰기 + 터미널 + git)
--  - 노드 좌표는 비워 둔다(자동 배치)
-- 프롬프트에는 **팀 고유 내용만** 넣는다 — 계약·ask·순환 금지·워크스페이스 규칙은 프로젝트 마스터
-- 프롬프트에 이미 있고, 실행될 때 마스터 → 그룹 → 에이전트 순서로 겹쳐 적용된다.

-- 1) 역할 '디자인'
INSERT INTO agent_role (name)
SELECT '디자인'
WHERE NOT EXISTS (SELECT 1 FROM agent_role WHERE name = '디자인');

-- 2) 팀(그룹). 프롬프트 = 이 팀이 일하는 방식
INSERT INTO agent_group (project_id, name, description, prompt)
SELECT p.id,
       '디자인 팀',
       '화면의 생김새와 쓰임새를 맡는 팀',
       E'디자인 팀은 화면의 생김새와 쓰임새를 맡는다. 기획 팀의 요구사항과 설계 팀의 화면 흐름을 받아, 프론트가 그대로 만들 수 있는 수준까지 디자인을 확정한다.\n\n'
       '- 산출물은 언제나 (1) 화면별 구성 요소와 배치, (2) 상태(기본·비어 있음·로딩·오류)별 표현, (3) 색·글자·간격·반경 같은 **토큰 값**, (4) 사용 흐름을 순서대로 적은 목록이다. 그림 설명이 아니라 값과 순서로 적는다.\n'
       '- 새로 만들기 전에 **기존 화면을 먼저 본다**: 목업 `apps/mockup` 과 실제 화면 `apps/frontend`. 기존과 어긋나는 디자인은 이유를 적고 사람에게 확인받는다.\n'
       '- 토큰은 `apps/frontend/src/styles/tokens.css` 의 이름을 그대로 쓴다(예: `--color-primary`, `--spacing-md`). 없는 색·간격을 임의로 만들지 않는다.\n'
       '- 다크/라이트 테마를 **함께** 본다. 한쪽만 보고 값을 정하지 않는다.\n'
       '- 접근성 값을 남긴다: 글자 대비, 터치 영역 크기, 키보드 이동 순서.\n'
       '- 추측이 필요한 갈림길(취향·브랜드 결정)은 계약 ask 로 사람에게 묻는다.'
FROM project p
WHERE p.name = 'AgentDock'
  AND NOT EXISTS (
    SELECT 1 FROM agent_group g WHERE g.project_id = p.id AND g.name = '디자인 팀'
  );

-- 3) 에이전트 3명
INSERT INTO agent (
    name, role_id, permission_profile_id, provider_id, model, project_id, persona, placed
)
SELECT v.name, r.id, pp.id, pr.id, 'deepseek/deepseek-v4-flash', p.id, v.persona, true
FROM (VALUES
    ('디자인 리더',
     '디자인 팀 리더. 기획·설계 결과를 받아 작업을 나누고 팀원 결과를 하나의 디자인 결론으로 모은다. 취향이 갈리는 결정은 추측하지 않고 사람에게 묻는다.'),
    ('UI 디자인 담당',
     '화면 단위 디자인 담당. 화면마다 요소·배치와 상태별(기본·비어 있음·로딩·오류) 표현을 토큰 값과 함께 구체적으로 적는다. 만들기 전에 기존 화면을 먼저 확인한다.'),
    ('디자인 시스템 담당',
     '디자인 토큰과 공용 컴포넌트 담당. 색·글자·간격·반경 값을 tokens.css 이름으로 정리하고, 다크/라이트 대비와 접근성 값을 함께 남긴다.')
) AS v(name, persona)
CROSS JOIN project p
JOIN agent_role r ON r.name = '디자인'
JOIN permission_profile pp ON pp.name = '개발'
JOIN ai_provider pr ON pr.key = 'COMMAND_CODE'
WHERE p.name = 'AgentDock'
  AND NOT EXISTS (
    SELECT 1 FROM agent a WHERE a.project_id = p.id AND a.name = v.name
  );

-- 4) 팀 멤버
INSERT INTO agent_group_member (group_id, agent_id)
SELECT g.id, a.id
FROM agent_group g
JOIN project p ON p.id = g.project_id AND p.name = 'AgentDock'
JOIN agent a
  ON a.project_id = g.project_id
 AND a.name IN ('디자인 리더', 'UI 디자인 담당', '디자인 시스템 담당')
WHERE g.name = '디자인 팀'
  AND NOT EXISTS (
    SELECT 1 FROM agent_group_member m WHERE m.group_id = g.id AND m.agent_id = a.id
  );

-- 5) 리더 지정
UPDATE agent_group g
SET leader_agent_id = a.id
FROM agent a, project p
WHERE g.name = '디자인 팀'
  AND p.id = g.project_id
  AND p.name = 'AgentDock'
  AND a.project_id = g.project_id
  AND a.name = '디자인 리더'
  AND g.leader_agent_id IS DISTINCT FROM a.id;
