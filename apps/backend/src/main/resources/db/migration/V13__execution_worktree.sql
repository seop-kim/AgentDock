-- 실행 트리를 git worktree 로 격리한다 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 명령 하나(루트 실행)마다 워크스페이스 저장소의 worktree 를 하나 만들어 그 트리 전체가 그 안에서 돈다.
-- 자식은 부모의 편집을 봐야 하므로 부모와 같은 worktree 를 쓰고, 다른 명령끼리만 서로 격리된다.
-- worktree 는 저장소 밖(워크스페이스의 형제 폴더 `<폴더>-wt/<루트실행id>`)에 두고 브랜치는 `agentdock/exec-<루트실행id>` 다.
-- 자동 병합·자동 삭제는 하지 않는다(사람이 판단한다) — 그래서 경로와 브랜치를 실행에 기록해 둔다.
ALTER TABLE execution ADD COLUMN IF NOT EXISTS worktree_path VARCHAR(1024);
ALTER TABLE execution ADD COLUMN IF NOT EXISTS worktree_branch VARCHAR(200);
