-- 디자인 팀에 'UX 담당'을 추가한다 (멱등: 이미 있으면 아무것도 하지 않는다)
--
-- V21 에서 리더·UI·디자인 시스템만 넣고 **사용 흐름과 정보 구조를 보는 역할**이 빠졌다.
-- 기존 팀과 같은 규칙: 런타임 Command Code, 모델 deepseek/deepseek-v4-flash, 권한 프로필 '개발', 좌표는 자동 배치.

-- 1) 에이전트
INSERT INTO agent (
    name, role_id, permission_profile_id, provider_id, model, project_id, persona, placed
)
SELECT 'UX 담당', r.id, pp.id, pr.id, 'deepseek/deepseek-v4-flash', p.id,
       'UX 담당. 사용자가 실제로 하는 순서로 화면을 본다: 처음 들어와 첫 성공까지의 최단 경로와 막히는 지점을 적는다. '
       || '정보 구조(무엇이 어디에 있는지), 문구(무엇이라고 말할지), 실수했을 때 되돌리는 방법, 키보드만으로 끝내는 흐름을 값으로 남긴다. '
       || 'UI 담당의 화면 결과와 맞춰 보고, 어긋나면 어느 쪽을 고칠지 제안한다.',
       true
FROM project p
JOIN agent_role r ON r.name = '디자인'
JOIN permission_profile pp ON pp.name = '개발'
JOIN ai_provider pr ON pr.key = 'COMMAND_CODE'
WHERE p.name = 'AgentDock'
  AND NOT EXISTS (
    SELECT 1 FROM agent a WHERE a.project_id = p.id AND a.name = 'UX 담당'
  );

-- 2) 팀 멤버
INSERT INTO agent_group_member (group_id, agent_id)
SELECT g.id, a.id
FROM agent_group g
JOIN project p ON p.id = g.project_id AND p.name = 'AgentDock'
JOIN agent a ON a.project_id = g.project_id AND a.name = 'UX 담당'
WHERE g.name = '디자인 팀'
  AND NOT EXISTS (
    SELECT 1 FROM agent_group_member m WHERE m.group_id = g.id AND m.agent_id = a.id
  );

-- 3) 팀 프롬프트에 UX 한 줄을 덧붙인다(이미 있으면 넣지 않는다)
UPDATE agent_group g
SET prompt = g.prompt
    || E'\n- UX 담당은 사용자가 실제로 하는 순서(진입 → 첫 성공 → 되돌리기)로 먼저 보고, 막히는 지점과 정보 구조·문구를 함께 남긴다. UI 담당의 결과와 맞춰 본다.'
FROM project p
WHERE g.name = '디자인 팀'
  AND p.id = g.project_id
  AND p.name = 'AgentDock'
  AND position('UX 담당은 사용자가 실제로 하는 순서' IN g.prompt) = 0;
