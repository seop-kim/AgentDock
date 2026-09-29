# 구조 재편 — 워크스페이스 중심 연결 + 프로젝트 소속 에이전트 (단계별)

브랜치(Stage 1): `feat/workspace-runtime`. 2026-09-29 Stage 1 구현·검증 완료.

## 목표 모델 (사용자 확정)

| 구분 | 내용 |
| --- | --- |
| 에이전트 설정(전역) | AI 런타임(claude/codex/gemini/command code)을 **켜고 끄는 곳**. 로그인은 런타임 단위 버튼(웹 패널 유지) |
| 워크스페이스 | **폴더 경로** 등록. 그 폴더에서 런타임이 실제로 실행되는지 확인해 **폴더별 상태** 표기 |
| 프로젝트 | 워크스페이스를 **N개** 할당(기본 1개). 프로젝트 안에서 에이전트를 만들고(**고유 페르소나**), 그룹화 |
| 프로젝트 상세 | 명령 채팅창. 대상 미지정 → **자동 라우팅** → Task/Execution |
| Agents 메뉴 | 전 프로젝트 에이전트 목록(읽기) |
| Tasks 메뉴 | 전 프로젝트 진행/종료 작업 목록 |
| 삭제 | "Agent 연결 설정" 화면과 Provider 단위 Connection 개념 |

## 확정 사항

- 단계별 PR (각 PR 은 그 자체로 dev 에서 동작해야 한다)
- on/off 단위 = **AI 런타임**
- 워크스페이스 상태 = **그 폴더를 작업 디렉터리로 CLI 실제 실행**(probe cwd)
- 채팅 = 자동 라우팅
- 가정: 실데이터 없음 → 마이그레이션 누적 대신 재편(V6~)

## Stage 1 — 스키마·도메인 (완료)

### 스키마

- `V6__runtime_enabled_and_workspace_status.sql`
  - `ai_provider.enabled BOOLEAN NOT NULL DEFAULT false` (시드: CLAUDE_CODE=true)
  - `workspace_runtime_status(id, workspace_id, provider_id, status, last_checked_at, last_error, UNIQUE(workspace_id, provider_id))`
- `V7__project_workspaces_and_agent_project.sql`
  - `project_workspace(id, project_id, workspace_id, is_default, UNIQUE(project_id, workspace_id))` + 기본 1개 부분 유니크 인덱스
  - 기존 `project.workspace_id` → 기본 워크스페이스로 이관 후 컬럼 제거
  - `agent.project_id BIGINT NOT NULL` + `agent.persona TEXT`
- `V8__drop_ai_connection.sql`
  - `agent.connection_id` 제거, `ai_connection` 테이블 제거

### 백엔드

- `AiProvider.enabled` + `PUT /ai-providers/{id}/enabled` (on/off)
- `WorkspaceRuntimeService` + `GET /workspaces/{id}/runtimes`, `POST /workspaces/{id}/runtimes/{providerId}/check`
  - `AiConnectionProbe` → **`AiRuntimeProbe`**(이름 변경) + `check(String cwd)` 로 확장, `ClaudeCodeProbe` 는 `ProcessBuilder.directory(cwd)`
- 로그인 세션을 런타임 기준으로 이동: `POST /ai-providers/{id}/login`, `GET /ai-providers/login-sessions/{sessionId}/stream`, `POST .../input`, `DELETE ...`
- 프로젝트↔워크스페이스: `POST /projects/{id}/workspaces`(`{workspaceId, isDefault?}`), `DELETE /projects/{id}/workspaces/{workspaceId}`, `ProjectResponse.workspaces[]`
- 에이전트: `CreateAgentRequest` 에 `projectId`(필수)·`persona` 추가, `connectionId` 제거. 목록은 프로젝트 정보 포함
- 실행 가드(`AgentAvailability`): **런타임 살아있음 + enabled + 프로젝트 기본 워크스페이스에서 CONNECTED** → 아니면 409 (`PROVIDER_DELETED`/`RUNTIME_DISABLED`/`CONNECTION_NOT_CONNECTED`)
- 실행: `ExecutionService` 가 기본 워크스페이스를 해석하고 `AgentExecutionRequest.persona` 를 런타임에 전달
- `ClaudeCodeRuntime.buildArgs`: persona → `--append-system-prompt`, mode → `--permission-mode`, model → `--model`

### 프론트

- `api.ts`: `AiProvider.enabled`, `WorkspaceRuntime`, `Project.workspaces[]`, `Agent.project/persona` 반영. 연결 관련 API 제거, 런타임 on/off·폴더 확인·프로젝트 워크스페이스 할당 API 추가
- `Providers`(에이전트 설정): 런타임 카드에 **ON/OFF 토글** + 런타임 단위 로그인 패널. 연결/확인 UI 제거
- `Workspaces`: 워크스페이스마다 **런타임 상태 표** + "이 폴더에서 확인"
- `Projects`: 프로젝트에 **워크스페이스 다중 할당**(기본 지정/해제)
- `Agents`: **프로젝트 선택 + 페르소나** 입력, 켜둔 런타임만 선택, 사용 불가 사유 3종 배지

### 검증 (실제 CLI)

- 폴더(스크래치패드)에서 확인 → `CONNECTED`, 다른 폴더는 `DISCONNECTED` 로 따로 기록
- 프로젝트에 워크스페이스 2개 할당 → 첫 할당이 기본
- 에이전트 생성(프로젝트+페르소나) → `available=true` → 실행 `SUCCEEDED(exit 0)` → **로그에 페르소나 지시 결과(`BANANA`)가 나와 `--append-system-prompt` 전달 확인**
- 런타임을 끄면 실행이 **409** (`Runtime is turned off`)
- JUnit 45건 통과, 프론트 tsc+build 통과

## Stage 2 — 화면 재배치 (예정)

- "에이전트 설정" 메뉴 정리(라우트/파일 정리), Agents 는 읽기 목록 + 프로젝트 상세에서 생성
- `/model` 캐시 기반 **모델/권한 옵션 목록 UI**(라벨·설명·disabled, 새로고침)

## Stage 3 — 프로젝트 상세 채팅 (예정)

- 프로젝트 상세에 채팅 입력 → 자동 라우팅(그룹 리더 → 그룹 없는 에이전트) → Task/Execution + SSE
- 라우팅 규칙(에이전트 여러 개일 때)은 이 단계에서 확정

## Stage 4 — Tasks 통합 (예정)

- 전 프로젝트 작업 목록(진행/종료), 프로젝트·에이전트·시간 필터

## 미결/주의

- `agent` 테이블에 행이 있으면 V7(`project_id NOT NULL`)이 실패한다 → 이관 전에 정리 필요(현재는 빈 테이블 가정)
- `task.workspace_id` 는 Stage 3 에서 도입(지금은 프로젝트 기본 워크스페이스로 해석)
- 다른 런타임(CODEX/GEMINI/COMMAND_CODE) probe/로그인은 CLI 설치 후
