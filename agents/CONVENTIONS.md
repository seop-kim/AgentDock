# AgentDock — 공용 작업 기준 문서

이 저장소에는 Claude Code, Codex, Command Code, Gemini CLI 등 여러 AI 코딩 에이전트가 함께 작업한다. 이 파일이 모든 에이전트가 공유하는 단일 기준 문서이며, 루트의 진입점 파일들은 각 툴이 자동으로 찾는 포인터일 뿐 실제 내용은 여기 있다. **어떤 에이전트든 작업을 시작하기 전에 이 파일을 먼저 읽고, 구조/컨벤션을 바꾸면 이 파일도 함께 갱신한다.**

## 진입점 파일 (루트)

각 툴이 시작 시 자동으로 읽는 파일명이 다르므로, 루트에 동일한 내용(이 파일로의 포인터)을 여러 이름으로 둔다.

| 파일 | 대상 툴 |
| --- | --- |
| `CLAUDE.md` | Claude Code |
| `AGENTS.md` | Codex, 그리고 `AGENTS.md` 규격을 지원하는 대부분의 에이전트 |
| `GEMINI.md` | Gemini CLI |

Command Code처럼 위 세 파일 중 아무것도 자동으로 읽지 않는 툴을 쓸 경우, 그 툴의 system prompt/설정에 "작업 전에 `agents/CONVENTIONS.md`를 읽어라"를 직접 지정한다.

진행 상태와 다음 작업은 [`agents/HANDOVER.md`](./HANDOVER.md) 를 본다. 이 문서는 규칙·구조의 기준이고, HANDOVER 는 "지금 어디까지 됐고 다음에 뭘 할지"의 스냅샷이다.

## 기술 스택

- Backend: **Java 25 + Spring Boot 4.1.1** + Gradle(Groovy DSL) + Spring Data JPA(Hibernate) + QueryDSL + Flyway + Lombok + PostgreSQL (`apps/backend`)
- Frontend: **Vite 5 + React 18 + React Router 6** + TypeScript + CSS Modules (`apps/frontend`, 순수 SPA)
- Local Runtime 실행: `ProcessBuilder` (shell 미경유), 브라우저가 CLI를 직접 실행하지 않음
- 실시간 로그/출력: SSE (Server-Sent Events, `SseEmitter`)

## 핵심 설계 원칙 (스펙 33장)

1. Agent(논리적 직원) != AI Runtime(실행 엔진). `AgentRuntime` 인터페이스로 분리하고 런타임 설정에 따라 구현체(ClaudeCodeRuntime, ...)를 선택한다.
2. Task/Execution 중심 설계. Agent 자체보다 실행 단위(Execution)와 결과(Log)를 추적한다.
3. 권한은 Prompt 설명만으로 강제하지 않는다. Backend(ExecutionService)에서 실제로 차단한다.
4. Agent 간 통신은 전체 대화 공유가 아니라 Context/Message/Artifact/Decision 단위로 제한한다.
5. 모든 실행은 DB에 기록해 추적 가능해야 한다 (execution, execution_log).

## 현재 도메인 구조 (Stage 1 재편 완료)

화면·개념은 다음과 같다. 상세 계획/검증 기록은 `docs/superpowers/plans/2026-09-29-workspace-runtime-restructure.md`.

| 개념 | 내용 |
| --- | --- |
| **AI 런타임** (`ai_provider`) | claude / codex / command code / gemini. **on/off**(`enabled`)만 화면에서 관리한다. 로그인은 런타임 단위(웹 로그인 패널), 모델/모드 목록은 `capabilities` 데이터 |
| **워크스페이스** (`workspace`) | 로컬 폴더 경로. **폴더별 런타임 상태**(`workspace_runtime_status`)를 그 폴더를 작업 디렉터리로 CLI 를 실제 실행해 확인한다(probe cwd) |
| **프로젝트** (`project`) | 워크스페이스를 **N개** 할당(`project_workspace`, 기본 1개). 에이전트를 프로젝트 안에서 만든다 |
| **에이전트** (`agent`) | **프로젝트 소속**(`project_id NOT NULL`). 런타임 + Role + Permission + **페르소나**(`persona`, 그 에이전트만의 성격/일하는 방식 프롬프트) + model/mode |
| **그룹** (`agent_group`) | 프로젝트 안에서 에이전트를 묶고 리더를 지정 |
| **Task / Execution** | 프로젝트 소속. 실행 디렉터리는 프로젝트의 **기본 워크스페이스** |
| **Agents 메뉴** | 전 프로젝트 에이전트 목록(읽기용) + 이 화면에서도 생성 가능(Stage 2 에서 프로젝트 상세로 이동 예정) |
| **Tasks 메뉴** | 진행/종료 작업 목록 |

**삭제된 개념**: Provider 단위 Connection(`ai_connection`)과 "Agent 연결 설정" 화면. 연결 상태는 이제 워크스페이스(폴더) 단위다.

Workflow/WorkflowStep, Shared Context, Message, Artifact, Review, Decision 은 이후 Stage(2~4)에서 확장한다.

## 목업 (`apps/mockup`) — 진행 중

구현을 멈추고 목업을 먼저 만드는 중이다(`mockup` 브랜치). 목업이 확정되면 그것이 이후 개발의 기준이 된다. 설계는 `docs/superpowers/specs/2026-09-30-mockup-prototype-design.md`, 새로 쓰는 기획서/설계서는 `docs/planning/` 에 둔다.

- 독립 Vite + React + TypeScript 앱(포트 **3040**, `npm run dev:mockup`). `apps/frontend`/`apps/backend` 는 건드리지 않는다.
- 백엔드/DB 없음. 테스트 데이터는 `src/store/seed.ts`, 상태는 `src/store/MockStore.tsx`(메모리, 새로고침하면 초기화). `localStorage` 도 쓰지 않는다.
- **CSS 는 전부 파일로 분리**한다(인라인 `style` 금지). 색은 `src/styles/tokens.css` 의 토큰(`var(--color-...)`)만 쓴다. 화면 고유 스타일은 `*.module.css`, 공용 조각은 `styles/shared.module.css`.
- **다크모드 지원**: `tokens.css` 가 라이트/다크 값을 정의한다. `html[data-theme]` 명시값 > 시스템 설정 > 라이트. 새 색이 필요하면 토큰을 라이트·다크 양쪽(그리고 `prefers-color-scheme` 블록)에 함께 추가한다.
- **메뉴는 왼쪽 사이드바**(`components/Sidebar.tsx`)에 모은다. 기본은 아이콘만 보이고, 위의 메뉴 버튼을 누르면 이름까지 펼쳐지며 다시 누르면 접힌다. 메뉴를 추가하려면 `Sidebar.tsx` 의 `MENUS` 에 항목을, `components/icons.tsx` 에 아이콘을 추가한다. 테마 전환 버튼도 사이드바 하단에 있다.
- 현재 화면: Dashboard, 설정(에이전트 설정). 나머지 화면은 순서대로 추가한다.

## Backend 모듈 (`apps/backend/src/main/java/com/agent/dock`)

- `common/` — `GlobalExceptionHandler`(400/403/404/409), `BadRequestException`/`NotFoundException`/`ForbiddenException`/`ConflictException`, `WebConfig`(CORS), `QueryDslConfig`
- `provider/` — AiProvider(**논리 삭제** `deleted_at`, **on/off** `enabled`, 부분 유니크 인덱스 `ai_provider_key_active`). `PUT /ai-providers/{id}/enabled` 토글, `capabilities` 편집(`PUT /ai-providers/{id}/capabilities`). 런타임 CLI 실행 파일 조회(`AiRuntimeCli`/`CliRegistry`/`CliStatusService`, `GET /ai-providers/{id}/cli`)와 런타임별 probe(`AiRuntimeProbe`/`ProbeRegistry`/`ClaudeCodeProbe`), 로그인 세션(`provider/login`: `AiLoginCommand`/`LoginCommandRegistry`/`LoginProcess`/`LoginSessionService`) — 로그인은 **런타임 전역**
- `agent/` — Agent CRUD(프로젝트 소속, 페르소나) + `AgentAvailability`(사용 가능 여부 파생 판정). 목록 조회는 QueryDSL fetch join(`AgentRepositoryImpl`)
- `role/`, `permission/` — Role, PermissionProfile. `PermissionService.isAllowed(profile, action)` 가 enforcement primitive
- `workspace/` — Workspace CRUD, 폴더 브라우징(`GET /workspaces/browse`), UNC 차단(`WorkspaceFs`), 드라이브 루트 워밍, **폴더별 런타임 상태**(`WorkspaceRuntimeStatus`): `GET /workspaces/{id}/runtimes`, `POST /workspaces/{id}/runtimes/{providerId}/check`(cwd = 그 폴더)
- `project/` — Project CRUD + `ProjectWorkspace`(N:N, 기본 1개): `POST /projects/{id}/workspaces`, `DELETE /projects/{id}/workspaces/{workspaceId}`, `ProjectService.defaultWorkspace(projectId)`
- `group/` — `AgentGroup`(리더/멤버) 관리 API
- `task/` — `Task` CRUD와 실행(`POST /tasks/{id}/run`). 실행 종료 이벤트(`ExecutionFinishedEvent`)로 상태 갱신
- `process/` — `ProcessService`: `ProcessBuilder` 래퍼, 실행 중 프로세스 관리/취소
- `runtime/` — `AgentRuntime`, `ClaudeCodeRuntime`, `RuntimeRegistry`. CLI 인자 조립은 `buildArgs(request)` 한 곳에서
- `execution/` — Execution 생성/조회, ExecutionLog 저장, SSE(`GET /executions/{id}/stream`), 취소, **실행 가드**

## DB 스키마

단일 진실은 **Flyway 마이그레이션**(`apps/backend/src/main/resources/db/migration/`, 현재 V1~V8)이며 컬럼명은 snake_case다. JPA는 `ddl-auto: validate` 로 일치만 검증한다.

테이블: `ai_provider`, `agent_role`, `permission_profile`, `workspace`, `workspace_runtime_status`, `project`, `project_workspace`, `agent_group`, `agent_group_member`, `agent`, `task`, `execution`, `execution_log`.

- `ai_provider.enabled`(V6): 런타임 on/off. 시드는 `CLAUDE_CODE = true`.
- `workspace_runtime_status`(V6): `(workspace_id, provider_id)` 유니크. **폴더별** 런타임 상태(`CONNECTED`/`DISCONNECTED`/`ERROR`) + `last_checked_at`/`last_error`. Provider 단위 Connection 을 대체한다.
- `project_workspace`(V7): 프로젝트↔워크스페이스 N:N. `is_default` 는 프로젝트당 1개(부분 유니크 인덱스). 기존 `project.workspace_id` 는 V7 에서 이관 후 제거됐다.
- `agent.project_id NOT NULL`, `agent.persona TEXT`(V7). `agent.connection_id` 와 `ai_connection` 테이블은 V8 에서 제거됐다.
- `agent_group` 은 SQL 예약어 `group` 회피용 이름. `task` 는 `group_id`/`agent_id` 중 정확히 하나만 갖는다(CHECK + 서비스 검증).
- `execution.workspace_id` 는 실행 디렉터리(프로젝트 기본 워크스페이스)를 기록한다. `task.workspace_id` 는 아직 없다(필요해지면 추가).
- `ai_provider.capabilities`(JSONB): `{"models": [...], "modes": [...], "notes": "..."}`. V5 시드, 화면에서 편집.

## 런타임 연결과 자격증명

- 자격증명은 **CLI 의 기존 로그인 세션을 그대로 사용**한다(스펙 6장 우선순위 1). 앱은 토큰/키를 저장하지 않는다(Connection 계열 컬럼은 V8 에서 제거).
- 실행 바이너리는 환경변수 override: `CLAUDE_CODE_BIN`(기본 `claude`). probe 와 로그인 명령이 같은 변수를 쓴다.
- **폴더별 상태 확인(probe)**: `POST /workspaces/{id}/runtimes/{providerId}/check` 가 **그 폴더를 작업 디렉터리로** CLI 를 짧은 프롬프트로 한 번 실행해(최대 30초) `status`/`last_error`/`last_checked_at` 을 저장한다. 로그인은 전역이지만 "이 폴더에서 실제로 실행되는가"는 폴더마다 다를 수 있어 이렇게 확인한다.
- probe 구현체는 런타임별 하나이고 `ProbeRegistry` 에 자동 등록된다. 현재는 `ClaudeCodeProbe`(CLAUDE_CODE)만 있다. 다른 런타임은 목록에 표시되고 확인 시 ERROR("이 런타임은 아직 확인을 지원하지 않습니다") 가 된다.
- **웹 로그인 패널**: `POST /ai-providers/{id}/login` 이 서버 고정 로그인 명령(현재 `claude auth login`)을 실행하고 `GET /ai-providers/command-sessions/{sessionId}/stream`(SSE)으로 출력을, `POST .../input` 으로 stdin 입력을 전달한다. 런타임당 활성 세션 1개(로그인/설치 별개), 유휴 5분이면 종료. 로그인 명령은 사용자 입력으로 만들지 않는다.

### CLI 설치와 실행 파일 해석

- **실행 파일 해석**(`process/Executables`): Java 는 `npm` 처럼 확장자 없이 쓰는 이름을 `npm.cmd` 로 찾지 못한다(`CreateProcess error=2`). PATH/PATHEXT 를 훑어 실제 파일을 찾아 실행한다. `ProcessService`(에이전트 실행)와 probe 에 적용하며 **셸을 경유하지 않는다**(프롬프트가 셸로 해석되지 않게). `resolve` 는 찾으면 경로·없으면 입력 그대로, `locate` 는 찾으면 경로·없으면 null(설치 여부 판단용).
- **CLI 상태 조회**(`GET /ai-providers/{id}/cli` → `{resolvedPath, version, runnable, detail}`): `Executables.locate` 로 경로를 찾고, 있으면 `<실행 파일> --version` 을 10초 제한으로 실행해 첫 줄을 읽는다(`CliStatusService`, **셸 미경유**, 저장 안 함). 런타임별 `AiRuntimeCli` 구현이 등록된 런타임만 경로/버전을 보여 주고 나머지는 "CLI 정보를 확인할 수 없습니다"가 된다.
- **CLI 확인 창**(`CliPanel`): CLI 를 쓰는 모든 자리(에이전트 설정 카드 / 워크스페이스 런타임 행 / 에이전트 목록)에서 `CLI 확인` 버튼 하나로 연다. 창에서 위 상태와 "이 폴더에서 확인"(probe, `workspaceId` 있을 때만)·로그인·설치를 함께 실행하고, 출력 스트리밍은 `CommandPanel`(SSE)을 재사용한다.
- **설치 계획(데이터)**: `capabilities` 에 `installRequire`(먼저 있어야 하는 실행 파일, 예: `npm`), `installPrerequisite`(없을 때 먼저 실행할 단계, 예: `nvm install lts`), `install`(본 설치 단계) 을 둔다. V9 가 4종 런타임에 시드하며 화면에서 편집한다(추측 금지, npm 패키지는 실존 확인 후 기재).
- **설치 세션**: `POST /ai-providers/{id}/install` → 단계를 순서대로 실행하고 출력을 같은 SSE 로 스트리밍한다. 필수 도구가 없으면 선행 단계를 먼저 실행한다. 설치 단계만 `CommandShell`(`cmd.exe /c`, Unix `sh -c`)로 감싼다 — `.cmd`/`.ps1`/`&&` 를 쓰기 위함이며, **에이전트 실행 경로는 셸을 쓰지 않는다**.
- **CLI 없음 감지**: probe 가 실행 파일을 못 찾으면 `cliMissing` 으로 표시하고(저장: `workspace_runtime_status.cli_missing`) CLI 확인 창이 설치 버튼을 띄운다.

### 모델과 모드 (`capabilities`)

- 런타임별 지원 모델/모드 목록은 **코드가 아니라 데이터**다(`ai_provider.capabilities`). 화면("에이전트 설정" 카드의 "모델/모드 편집")에서 갱신한다(정규화: trim·빈값·중복 제거, 다른 키 보존).
- CLI 는 모델 목록을 알려주는 명령을 주지 않는다(`claude models` 는 프롬프트로 처리된다). 자동 수집 불가 → 수동 편집. V5 가 `claude --help` 에 나온 alias(`opus`/`sonnet`/`fable`)와 `--permission-mode` 6종을 시드한다(추측 금지 원칙).
- `Agent.model` → `--model`, `Agent.mode` → `--permission-mode`, `Agent.persona` → **`--append-system-prompt`**(기본 시스템 프롬프트에 덧붙임). 값이 없으면 플래그를 붙이지 않는다.
- 목록에 없는 값도 막지 않는다(최종 판단은 CLI). 화면은 datalist 로 제안하고 경고만 표시한다.

## Permission Enforcement

`permissionProfile` 의 boolean 플래그로 저장한다. Execution 을 만드는 모든 경로(`POST /executions`, `POST /tasks/{id}/run`)는 `ExecutionService` 를 지나며, `PermissionService.isAllowed(profile, TERMINAL_EXECUTE)` 가 false 면 Runtime 을 호출하지 않고 **403** 으로 거부한다.

그 직후 **연결 가드**가 한 번 더 막는다. `AgentAvailability.evaluate(providerDeleted, runtimeEnabled, workspaceStatus)` 판정으로:

| 조건 | 409 사유 |
| --- | --- |
| 런타임이 논리 삭제됨 | `PROVIDER_DELETED` |
| 런타임이 꺼져 있음(`enabled=false`) | `RUNTIME_DISABLED` |
| 프로젝트 기본 워크스페이스에서 그 런타임이 `CONNECTED` 아님(확인한 적 없음 포함) | `CONNECTION_NOT_CONNECTED` |

- 작업 디렉터리는 **프로젝트의 기본 워크스페이스**이며, 할당이 없으면 409(`Project N has no workspace assigned`).
- 새 실행만 차단하고 이미 실행 중인 Execution 은 건드리지 않는다. Agent 응답에는 파생값 `available`/`unavailableReason` 이 있고 저장하지 않는다.
- 사용 불가 에이전트는 `PUT /agents/{id}/provider` 로 다른(켜져 있는) 런타임을 재할당해 복구한다.
- `Agent.mode`(권한 문자열)는 이 가드와 별개로 CLI 에 전달될 뿐이다.

## 실행 방법

### 사전 준비

- JDK 25: 이 개발 환경에서는 `C:\Users\chey.kim\.jdks\openjdk-25.0.2` 에 있고 PATH 에 없다.

```powershell
$env:JAVA_HOME='C:\Users\chey.kim\.jdks\openjdk-25.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

- Postgres 접속 정보는 OS 환경변수(`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`). 기본값 `jdbc:postgresql://localhost:5432/AGENT_DOCK`, `postgres`, 빈 비밀번호.

```
createdb AGENT_DOCK        # 최초 1회
npm install                # 프론트 의존성
npm run dev:backend        # 8080 (Gradle bootRun, 기동 시 Flyway 자동 적용)
npm run dev:frontend       # 3030 (Vite)
```

Flyway 마이그레이션은 `V<N>__<설명>.sql` 새 버전으로만 추가한다(기존 파일 수정 금지).

### AI 에이전트가 검증할 때의 포트

사용자 포트는 백엔드 `8080` / 프론트 `3030` 이다. 에이전트는 **8081 / 3031** 만 쓰고 끝나면 즉시 종료한다.

```
npm run dev:backend:test    # 8081
$env:VITE_API_BASE='http://localhost:8081'; npm run dev:frontend:test   # 3031
```

DB 확인: `C:\Program Files\PostgreSQL\17\bin\psql.exe -U postgres -d AGENT_DOCK -c "..."`

## 코딩 컨벤션

- 코드/식별자는 영어, 커밋 메시지·문서 설명은 한국어/영어 혼용 가능
- 새 Runtime/Probe/로그인 명령은 인터페이스(`AgentRuntime`/`AiRuntimeProbe`/`AiLoginCommand`)만 구현하고 `@Component` 로 등록한다. 레지스트리가 `List<...>` 주입으로 자동 색인하므로 기존 모듈을 고치지 않는다.
- 요청은 `record` + Jakarta Validation, 응답은 엔티티 대신 `record` Response DTO(Jackson 순환 방지). Controller 는 얇게, 로직은 Service 에.
- shadow FK 필드(`insertable=false, updatable=false`)는 저장 직후 비어 있다. 생성 응답에서는 관계로 보완하거나(기존 패턴) `@Transactional` 없이 커밋 후 새로 조회한다(`AgentService.assignProvider`).
- 오래 걸리는 작업(probe, 로그인 세션)에는 `@Transactional` 을 붙이지 않는다. 필요한 관계는 fetch join 으로 조회 시점에 함께 읽는다.
- POST 는 **201**, 삭제는 **204**(프론트 `request()` 가 204 를 따로 처리).
- 변하지 않는 규칙(권한, 런타임 enabled, 폴더 상태)은 백엔드가 강제하고, 자주 변하는 값(모델/모드)은 데이터로 두고 CLI 에 위임한다.

## 브랜치 흐름과 커밋 컨벤션

브랜치 흐름은 `기능 브랜치 → dev → test → release` 다. `dev`는 통합 브랜치이고, `test`와 `release`는 `dev`에서 병합해 만든다. 따라서 **`dev`에 쌓이는 커밋 이력이 곧 릴리스 노트의 원천**이고, 커밋 메시지 규칙이 두 단계로 나뉜다.

### 1) 기능 브랜치 — 글로벌 컨벤션([Conventional Commits](https://www.conventionalcommits.org/))

```
feat: 에이전트 실행에 permission enforcement 추가
fix(workspace): 폴더 선택에서 드라이브 루트 조회가 멈추던 문제 수정
```

- 타입: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`
- scope 는 선택: `fix(workspace): ...`. 제목은 소문자 타입 + 콜론 + 한 칸 + 설명(한국어 허용)
- 하나의 커밋은 하나의 논리적 단위로 나눈다.

### 2) `dev`에 반영되는 커밋 — squash merge + `[Type] 제목`

```
[Feat] NestJS/Next.js → Spring Boot + Vite React 스택 전환
[Fix] 폴더 선택에서 드라이브 루트 조회가 멈추던 문제 수정
```

- 대괄호 + 대문자 시작: `[Feat]` `[Fix]` `[Docs]` `[Style]` `[Refactor]` `[Perf]` `[Test]` `[Build]` `[Ci]` `[Chore]` `[Revert]`
- scope/콜론 형식은 쓰지 않는다. 기능 브랜치의 개별 커밋은 squash 되므로 `dev` 이력은 항상 `[Type]` 형식이다.
- Breaking change 는 본문에 `BREAKING CHANGE: <설명>`.
- AI 에이전트가 만든 커밋에도 `Co-Authored-By:` 트레일러를 붙이지 않는다.
