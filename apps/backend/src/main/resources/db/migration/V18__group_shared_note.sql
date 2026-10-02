-- 그룹 공유 노트.
--
-- 그룹 프롬프트(prompt)와 달리 **규칙**이 아니라, 그룹 안에서 일한 실행들이 알아낸 **맥락**을 적어 두는 자리다.
-- 그룹에 속한 실행이 돌 때 시스템 프롬프트에 함께 실려, 런타임이 달라도(Claude Code ↔ Command Code)
-- 같은 팀의 맥락을 보고 일할 수 있게 한다.
alter table agent_group add column if not exists shared_note text not null default '';
