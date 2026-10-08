# 프론트 데이터/API 배선 정리 (2026-10-08)

디자인 기반 화면 구현에서 **데이터 계층**을 맡아 `apps/frontend` 화면이 실제 백엔드 API·전역 스토어와 연결되도록
정리·배선한 결과다. 화면 자체(JSX·CSS)는 프론트 화면 담당(18)이 맡고, 이 문서는 **엔드포인트·스토어 훅·타입**
연결 목록, 목업/모의 데이터 대체 내역, 화면에 필요한데 **없는 API** 목록을 남긴다.

## 공통 구조

- 모든 화면 데이터는 전역 컨텍스트 **`src/store/AgentDockStore.tsx`(`useAgentDockStore`)** 를 통한다. 화면이 `fetch` 를 직접 부르지 않는다.
- REST 클라이언트는 **`src/lib/api.ts`**, 화면용 도메인 타입은 **`src/types.ts`**(api.ts 응답형을 스토어가 매핑).
- **실시간 갱신은 SSE 우선**(CONVENTIONS): 스토어가 `GET /events/stream` 을 한 번 구독해 바뀐 조각만 재조회한다. 폴링은 30초 안전망(끊김 시 5초)뿐이고 탭이 숨겨지면 멈춘다.
- 실행 로그는 `GET /executions/{id}/stream` 을 화면(터미널 창·노드 미리보기)이 직접 구독한다.
- 전역 상태(첫 로드·오류·연결 끊김)는 **`components/AppStatus.tsx`** 가 화면 위에 한 번 띄운다(이번에 배선).
## 화면별 배선

### Dashboard `/`
- 스토어: `projects` `groups` `agents` `executions`
- 엔드포인트: `GET /projects` · `GET /agents` · `GET /groups?projectId` · `GET /tasks?projectId&limit` · `GET /executions/{id}/tree`(태스크 slice 로 읽음)
- 타입: `Project` `AgentGroup` `Agent` `Execution`(`metrics`)
- 비고: 사용량은 화면이 들고 있는 실행만 합산(전체 집계 API 없음 → "없는 API" 3).

### 프로젝트 목록 `/projects`
- 스토어: `projects` `workspaces` `agents` `tasks` `createProject` `loading`
- 엔드포인트: `GET /projects` · `GET /workspaces` · `GET /agents` · `GET /tasks?projectId&limit` · `POST /projects`
- 타입: `Project` `ProjectWorkspaceInfo` `Workspace` `Agent` `Task`
- 상태: 첫 로드 "불러오는 중…" — 빈 목록 "등록된 프로젝트가 없습니다."(이번에 로딩/빈 구분 추가).

### 프로젝트 상세 `/projects/:id`
- 스토어: `projects` `workspaces` `agents` `groups` `tasks` `executions` `loading` `reload` `watchExecutionTree`
- 엔드포인트: `GET /groups?projectId` · `GET /tasks?projectId&limit` · `GET /executions/{id}/tree` · `PUT /projects/{id}/layout` · `DELETE /projects/{id}/layout` · `POST /projects/{id}/commands`
- 타입: `Project` `Agent` `AgentGroup` `Task` `Execution` `ChatMessage` `ChatTarget`
- 상태: 로딩 중에는 "찾을 수 없습니다" 대신 "불러오는 중…"(첫 로드 deep-link 에 잘못 뜨던 문제 수정).
- 하위 화면별 엔드포인트
  - 에이전트/그룹: `GET/POST /agents` · `PUT/DELETE /agents/{id}` · `GET/POST /groups` · `PUT/DELETE /groups/{id}` · `POST/DELETE /groups/{id}/members[/{agentId}]`
  - 명령(ChatPanel): `POST /projects/{id}/commands` · `GET /workspaces/{id}/files` · `POST /workspaces/{id}/attachments` · 스토어 `sendCommand` `chatHasMore` `loadMoreChats` `uploadAttachments`
  - 실행 트리: `GET /executions/{id}/tree` · `POST /executions/{id}/cancel` · `POST /executions/{id}/merge` · `DELETE /executions/{id}/worktree` · `POST /executions/{id}/answer`
  - 요청(RequestsPanel·WaitingInputPopup): 스토어 `executions`(waitingInputs) · `answerExecution` `retryMerge` · `POST /executions/{id}/cancel`
  - 설정(ProjectSettingsModal·ProjectWorkspaces): `PUT/DELETE /projects/{id}` · `PUT /projects/{id}/master` · `POST/DELETE /projects/{id}/workspaces[/{workspaceId}]` · `POST /workspaces` · `GET /workspaces/{id}/runtimes` · `POST /workspaces/{id}/runtimes/{providerId}/check`
### 에이전트 터미널 `/terminal/:agentId`
- 스토어: `agents` `projects` `workspaces` `tasks` `executions` `loading`
- 엔드포인트: `GET /executions/{id}/stream`(SSE 직접 구독) · `POST /executions/{id}/cancel`
- 타입: `Execution` `Task` — 상태는 `lib/terminal.ts`(live/done/idle), 작업 디렉터리는 `worktreePath` 우선.

### 설정 `/settings`
- `agents`(AgentConnectionSettings·RuntimeCard·CliPanel·CommandPanel·AddRuntimeModal): 스토어 `providers` `loading` `availableProviderKeys`, `GET/POST/DELETE /ai-providers` · `PUT /ai-providers/{id}/enabled` · `PUT /ai-providers/{id}/capabilities` · `GET /ai-providers/{id}/cli` · `POST /ai-providers/{id}/login` · `POST /ai-providers/{id}/install` · `GET /ai-providers/command-sessions/{id}/stream`(SSE) · `POST .../input` · `DELETE /ai-providers/command-sessions/{id}`. 타입 `AiProvider` `Capabilities` `CliStatus`. 상태: 로딩/빈 구분 추가.
- `theme`(ThemeSettings): UI 설정만(localStorage `agentdock-theme`). API 없음.

### 현실성 점검 `/reality-check`, Sidebar
- API 없음. RealityCheck 는 `RISKS`/`VERIFIED` 정적 데이터(설계 문서 화면), Sidebar 는 메뉴 + localStorage(테마·사이드바 펼침).
## 이번 작업의 타입·배선 변경

- `src/types.ts`: 변경 없음(이미 `api.ts` 응답을 도메인 타입으로 매핑하는 형태).
- 새 파일 `src/components/AppStatus.tsx` + `AppStatus.module.css`: 스토어의 `loading`/`error`/`streamConnected`/`reload` 를 화면에 노출(첫 로드·요청 실패·SSE 끊김). 이전에는 이 세 값이 **아무 화면도 읽지 않는 죽은 상태**였다.
- `src/App.tsx`: `<AppStatus />` 를 토스트·확인 창과 함께 한 번 그린다.
- `src/pages/Projects/ProjectDetail.tsx` · `Projects.tsx` · `pages/Settings/AgentConnectionSettings.tsx`: 스토어 `loading` 으로 로딩 상태를 표시하고, 그때는 빈 상태 문구를 숨긴다.
- `src/pages/Projects/ExecutionTree.tsx`: 실행 트리 창의 낡은 안내문("지표는 모의 … 화면에서는 빠르게 재생")을 실제 값 설명으로 교체(같은 카드의 `ExecutionSummaryCard` 는 이미 "지표는 CLI 가 돌려준 실제 값입니다" 라고 적혀 있어 서로 모순이었다).

## 목업/모의 데이터 대체 내역

- 프론트에 **목업 스토어·시드 데이터는 없다**. `src/store/seed.ts` 에 남은 것은 표시용 기본값뿐이다 — `DEFAULT_PROVIDER_NAMES`(런타임 추가 시 이름), `DEFAULT_CAPABILITIES`(런타임 등록 시 초기 모델/모드/설치 명령). 데이터 자체는 전부 `GET /ai-providers` 등 백엔드에서 온다.
- `src/lib/windowSync.ts`: 목업의 `BroadcastChannel` 스냅샷 다리를 제거하고, 새 창이 `GET /executions/{id}/stream` 을 **직접** 구독한다.
- `src/lib/agentAvailability.ts`: 시그니처만 목업 그대로 두고, 판정은 백엔드 파생값 `available`/`unavailableReason` 을 그대로 쓴다.
- 실행 상태·지표·트리·워크트리·계약은 모두 백엔드 응답(실측 CLI 값)이다. 위 ExecutionTree 안내문만 아직 "모의"라고 적혀 있던 잔재였다(이번에 수정).
- `RealityCheck` 화면의 "목업은 …", "값은 모의" 문구는 **설계 문서 성격의 콘텐츠**라 남겼다(라이브 데이터가 아님).
## 화면에 필요한데 없는 API (추측하지 않고 목록으로 남김)

아래는 화면(18)과 맞춰야 할 것들이라 여기서 임의로 만들지 않았다. 필요하면 프론트 화면(18)과 합의해 백엔드에 추가한다.

1. **채팅 기록의 첨부 복원** — `GET /tasks/{id}/attachments` 또는 `TaskResponse` 에 `attachments` 포함.
   지금 `TaskResponse` 에 첨부 정보가 없어 재조회 후 말풍선의 첨부가 사라진다(스토어 `rebuildChats` 가 `attachments: []` 로 채운다). DB `attachment.task_id` 에는 연결돼 있어 조회만 추가하면 된다.
2. **프로젝트 단위 "내가 처리할 요청" 조회** — `GET /executions?projectId=&status=WAITING_INPUT` 또는 `GET /projects/{id}/requests`.
   요청 창(RequestsPanel)은 열린 프로젝트의 최신 5개 트리만 읽으므로, 오래된/지난 트리에서 물어본 질문과 수동 병합(`MANUAL`)은 목록에 뜨지 않는다(CONVENTIONS 의 HANDOVER 에도 같은 한계를 적어 두었다).
3. **프로젝트/전역 실행 목록** — `GET /executions?projectId=`.
   Dashboard 사용량은 지금 화면이 들고 있는 실행만 합산해 **전체가 아니다**. 실행 목록(또는 프로젝트별 집계) API 가 있으면 정확히 맞출 수 있다.

## 검증

- `npm run build --workspace=apps/frontend` (`tsc --noEmit && vite build`) **통과**.
- 화면 상태: 첫 로드(AppStatus "서버에서 불러오는 중…"), 요청 실패(AppStatus 오류 + "다시 시도"), SSE 끊김(AppStatus 안내, 5초 안전망 폴링), 목록/설정 빈 상태, 프로젝트 상세 로딩 상태를 반영했다.