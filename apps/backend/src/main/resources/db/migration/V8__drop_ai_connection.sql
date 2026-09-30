-- Provider 단위 Connection 개념을 제거한다. 런타임 연결 상태는 이제 워크스페이스(폴더)별 workspace_runtime_status 가 담당한다.
ALTER TABLE agent DROP CONSTRAINT IF EXISTS agent_connection_id_fkey;
ALTER TABLE agent DROP COLUMN IF EXISTS connection_id;
DROP TABLE IF EXISTS ai_connection;
