# AgentDock — 공용 작업 기준 문서

이 저장소에는 Claude Code, Codex, Command Code, Gemini CLI 등 여러 AI 코딩 에이전트가 함께 작업한다. 이 파일이 모든 에이전트가 공유하는 단일 기준 문서이며, 루트의 진입점 파일들은 각 툴이 자동으로 찾는 포인터일 뿐 실제 내용은 여기 있다. **어떤 에이전트든 작업을 시작하기 전에 이 파일을 먼저 읽고, 구조/컨벤션을 바꾸면 이 파일도 함께 갱신한다.**

## 진입점 파일 (루트)

각 툴이 시작 시 자동으로 읽는 파일명이 다르므로, 루트에 동일한 내용(이 파일로의 포인터)을 여러 이름으로 둔다.

| 파일 | 대상 툴 |
| --- | --- |
| `CLAUDE.md` | Claude Code |
| `AGENTS.md` | Codex, 그리고 `AGENTS.md` 규격을 지원하는 대부분의 에이전트 |
| `GEMINI.md` | Gemini CLI |

Command Code처럼 위 세 파일 중 아무것도 자동으로 읽지 않는 툴을 쓸 경우, 그 툴의 system prompt/설정(예: 커스텀 instructions, `--system` 옵션 등)에 "작업 전에 `agents/CONVENTIONS.md`를 읽어라"를 직접 지정해야 한다. 새로운 진입점 파일명이 필요해지면 위 표와 루트에 같은 패턴으로 파일을 추가하고 이 표도 갱신한다.

진행 상태와 다음 작업은 [`agents/HANDOVER.md`](./HANDOVER.md) 를 본다. 이 문서는 규칙·구조의 기준이고, HANDOVER 는 "지금 어디까지 됐고 다음에 뭘 할지"의 스냅샷이다.

멀티 에이전트 조직 운영 플랫폼. 상세 제품/아키텍처 스펙은 이 저장소를 만든 최초 이슈 본문(Multi-Agent Organization Platform - Product & Architecture Draft) 참고. 이 파일은 그 스펙을 구현 단위로 요약한 것이다.

## 기술 스택

- Backend: **Java 25 + Spring Boot 4.1.1** + Gradle(Groovy DSL) + Spring Data JPA(Hibernate) + QueryDSL + Flyway + Lombok + PostgreSQL (`apps/backend`)
- Frontend: **Vite 5 + React 18 + React Router 6** + TypeScript + CSS Modules (`apps/frontend`, 순수 SPA)
- Local Runtime 실행: `ProcessBuilder` (shell 미경유), 브라우저가 CLI를 직접 실행하지 않음
- 실시간 로그: SSE (Server-Sent Events, `SseEmitter`)

스택 전환 이력: NestJS + Prisma + Next.js → Spring Boot + Flyway + Vite. 전환 시점의 설계 브리프/계획은 `docs/superpowers/specs/2026-09-28-java-spring-react-migration-brief.md`, `docs/superpowers/plans/2026-09-28-java-spring-react-migration.md` 참고.

## 모노레포 구조

```
agents/    모든 AI 에이전트가 공유하는 작업 기준 문서 (이 폴더)
apps/
  backend/   Spring Boot API (Gradle 프로젝트, npm workspace 아님)
  frontend/  Vite + React SPA (npm workspace)
```

npm workspaces는 `apps/frontend`만 포함한다 (루트 `package.json`). 백엔드는 Gradle Wrapper로 빌드/실행한다.

## 핵심 설계 원칙 (스펙 33장)

1. Agent(논리적 직원) != AI Runtime(실행 엔진). `AgentRuntime` 인터페이스로 분리하고 Provider 설정에 따라 구현체(ClaudeCodeRuntime, CodexRuntime, ...)를 선택한다.
2. Task/Execution 중심 설계. Agent 자체보다 실행 단위(Execution)와 결과(Artifact/Log)를 추적한다.
3. 권한은 Prompt 설명만으로 강제하지 않는다. Backend Tool Layer(Permission Enforcement)에서 실제로 차단한다.
4. Agent 간 통신은 전체 대화 공유가 아니라 Context/Message/Artifact/Decision 단위로 제한한다.
5. 모든 실행은 DB에 기록해 추적 가능해야 한다 (execution, execution_log).

## Phase 1 (구현 완료)

- AI Provider/Connection 등록
- Agent 생성 + Role/Permission 설정
- Workspace 등록 (폴더 선택 UI로 로컬 경로 지정. 로컬 전용 도구이므로 경로 제한 없음. UNC/네트워크 경로만 차단)
- Agent에게 Prompt 전달 → CLI 실행 (spawn) → 실시간 로그(SSE)
- 모든 Execution/Log는 PostgreSQL에 저장

## Phase 2 (구현 완료)

- **Project** — 작업 디렉터리(워크스페이스)를 결정하는 최상위 단위
- **Group** — 프로젝트를 수행하는 팀. 리더(leader agent)와 멤버(agents)로 구성
- **Task** — 프로젝트에 속하고, 그룹 또는 개별 Agent에 할당되는 작업 단위
- **Task 실행** — 할당 대상에게 기존 Execution 파이프라인을 위임한다(그룹이면 리더 Agent). 실행/로그/SSE/상태 추적은 Phase 1 경로를 그대로 재사용한다

계층은 `Project → Group → Agent` 다. **워크스페이스는 프로젝트에 귀속**하며 Agent에는 워크스페이스가 없다. Agent 직접 실행(`POST /executions`)도 `projectId`를 함께 받아 프로젝트의 워크스페이스를 작업 디렉터리로 사용한다.

Workflow/WorkflowStep(순차·병렬 실행), Shared Context, Message, Artifact, Review, Decision, `max_parallel_agents`는 이후 Phase(3~4)에서 확장한다.

## Backend 모듈 (`apps/backend/src/main/java/com/agent/dock`)

- `common/` — `GlobalExceptionHandler`(400/403/404/409 응답 형태), `BadRequestException`/`NotFoundException`/`ForbiddenException`/`ConflictException`, `WebConfig`(CORS), `QueryDslConfig`(`JPAQueryFactory` 빈)
- `provider/` — AiProvider(**논리 삭제**, `deleted_at`·부분 유니크 인덱스)·AiConnection(**Provider 당 1개**) 관리와 연결 상태 확인. 실행 엔진별 probe(`AiConnectionProbe`/`ProbeRegistry`/`ClaudeCodeProbe`)로 CLI가 실제 응답하는지 확인한다(`POST /ai-connections/{id}/check`). 로그인 세션(`provider/login`: `AiLoginCommand`/`LoginCommandRegistry`/`LoginProcess`/`LoginSessionService`)이 Provider CLI 의 로그인 명령을 실행하고 출력을 SSE 로 스트리밍한다. 지원 모델/모드 목록(`capabilities`)은 `PUT /ai-providers/{id}/capabilities` 로 갱신한다. Credential은 DB에 평문 저장 금지, `credentialReference`만 저장
- `agent/` — Agent CRUD + `AgentAvailability`(사용 가능 여부 파생 판정). Role/Permission/Provider/Connection 참조. 목록 조회는 QueryDSL fetch join(`AgentRepositoryImpl`)
- `role/`, `permission/` — Role, PermissionProfile CRUD. `PermissionService.isAllowed(profile, action)`가 enforcement primitive
- `workspace/` — Workspace CRUD, 로컬 폴더 브라우징 API(`GET /workspaces/browse`), UNC 경로 차단(`WorkspaceFs`), 기동 시 드라이브 루트 메타데이터 워밍
- `project/` — Project CRUD. 워크스페이스 참조(프로젝트가 작업 디렉터리를 결정)
- `group/` — `AgentGroup`(프로젝트 소속 팀, 리더 지정)과 `AgentGroupMember`(멤버 추가/제거) 관리 API
- `task/` — `Task`(프로젝트 소속, 그룹 또는 개별 Agent 할당) CRUD와 실행(`POST /tasks/{id}/run`). 실행 종료 시 `ExecutionFinishedEvent`를 받아 상태 갱신
- `process/` — `ProcessService`: `ProcessBuilder` 래퍼, 실행 중 프로세스 Map 관리/취소
- `runtime/` — `AgentRuntime` 인터페이스, `ClaudeCodeRuntime` 구현체, `RuntimeRegistry` (provider key → runtime 매핑). CLI 인자 조립은 `buildArgs(request)` 한 곳에서 한다
- `execution/` — Execution 생성/조회(작업 디렉터리는 프로젝트의 워크스페이스), ExecutionLog 저장, SSE 스트리밍 (`GET /executions/{id}/stream`), 취소, 실행 종료 이벤트 발행. 실행 가드(연결 상태 종속)가 여기에 있다

## DB 스키마 (Phase 1~2)

`ai_provider`, `ai_connection`, `agent_role`, `permission_profile`, `workspace`, `agent`, `execution`, `execution_log`, `project`, `agent_group`, `agent_group_member`, `task`. 스키마의 단일 진실은 **Flyway 마이그레이션**(`apps/backend/src/main/resources/db/migration/`)이며, 컬럼명은 snake_case다. JPA는 `ddl-auto: validate`로 엔티티와 스키마 일치만 검증하고 스키마를 만들지 않는다.

- `project.workspace_id`가 NOT NULL 이며 작업 디렉터리를 결정한다. `agent.workspace_id`는 V2에서 제거됐다.
- `agent_group` 은 SQL 예약어 `group` 을 피한 테이블명이다(엔티티 `AgentGroup`). 같은 프로젝트 안에서 이름이 유일하다.
- `task` 는 `project_id` 가 필수이고 `group_id`/`agent_id` 중 정확히 하나만 가진다(DB CHECK + 서비스 검증).
- `execution.task_id` 는 Task 실행으로 만들어진 실행을 표시한다. 관계 매핑 없이 컬럼만 두어 `execution` → `task` 패키지 의존을 피한다.
- `ai_connection.last_checked_at`/`last_error` 는 연결 확인(V3)의 결과다. `status` 는 `CONNECTED`/`DISCONNECTED`/`ERROR` 이며 확인할 때마다 갱신된다.
- `ai_provider.deleted_at` 은 논리 삭제 시각이다. `key` 유일성은 부분 유니크 인덱스(`ai_provider_key_active`, `WHERE deleted_at IS NULL`)로 **삭제되지 않은 행에만** 적용된다. 따라서 삭제한 key 를 새 Provider 로 다시 등록할 수 있고, 그때는 새 행이 생기므로 옛 Agent 는 자동으로 붙지 않는다(명시적으로 재할당한다). V4 가 4종 Provider(CLAUDE_CODE/CODEX/COMMAND_CODE/GEMINI)와 각 Connection 1개를 시드한다.
- `ai_provider.capabilities`(JSONB)에는 지원 모델/모드 목록이 들어간다. V5 가 시드하며, 스키마 변경(컬럼 추가)은 없었다.

이후 Phase에서 `workflow`, `workflow_step`, `context`, `agent_message`, `artifact`, `review`, `decision` 등을 추가한다 (스펙 23장 Entity 목록).

## AI 연결과 자격증명

- 자격증명은 **CLI의 기존 로그인 세션을 그대로 사용**한다(스펙 6장 우선순위 1). 이 앱은 API Key/토큰을 저장하지 않고 DB에는 `accountName` 과 `credentialReference`(참조 문자열)만 둔다. 로그인/재로그인은 사용자가 각 CLI에서 직접 한다(`claude` 등). 웹 로그인 패널도 그 CLI 로그인을 대신 실행해 줄 뿐이다.
- 실행 바이너리는 환경변수로 override 한다: `CLAUDE_CODE_BIN`(기본 `claude`). probe 와 로그인 명령이 같은 변수를 쓴다.
- **연결 확인(probe)**: `POST /ai-connections/{id}/check` 가 해당 Provider의 CLI를 짧은 프롬프트로 한 번 실행해(최대 30초) 실제 응답 여부를 보고 `status`/`last_error`/`last_checked_at` 을 갱신한다. 로그인 만료 같은 실패를 실행 전에 UI에서 확인할 수 있게 하는 것이 목적이다.
- probe 구현체는 Provider별로 하나이며 `ProbeRegistry` 에 자동 등록된다. 현재는 `ClaudeCodeProbe`(CLAUDE_CODE)만 있다. CODEX/COMMAND_CODE/GEMINI 는 Runtime 구현체도 probe도 없어서 실행 시 404, 연결 확인 시 ERROR("이 Provider 는 아직 연결 확인을 지원하지 않습니다") 로 표시된다.
- **Provider 는 논리 삭제**(`deleted_at`)이고 **Connection 은 Provider 당 1개**다(서비스 계층이 409 로 보장). Provider 를 삭제해도 참조하는 Agent 는 남고 "사용 불가"가 되며, Connection 을 삭제하면 그 Provider 의 Agent 는 실행 가드에 막힌다.
- **웹 로그인 패널**: 연결 확인이 실패하면 화면에서 바로 CLI 로그인을 시작할 수 있다. `POST /ai-connections/{id}/login` 이 **서버 고정 로그인 명령**(현재 `claude auth login` 만, `ClaudeCodeLoginCommand`)을 실행하고 `GET /ai-connections/login-sessions/{sessionId}/stream`(SSE)으로 출력을, `POST .../input` 으로 stdin 입력을 주고받는다. 로그인 명령이 없는 Provider 는 "로그인 창 미지원" 안내(400)를 반환한다. 로그인 명령은 사용자 입력으로 만들지 않는다.

### 모델과 모드 (`capabilities`)

- Provider별 **지원 모델/모드 목록은 코드가 아니라 데이터**다. `ai_provider.capabilities` 에 `{"models": [...], "modes": [...], "notes": "..."}` 형태로 저장하고, 화면("Agent 연결 설정" 카드의 "모델/모드 편집")에서 갱신하며 `PUT /ai-providers/{id}/capabilities` 로 저장한다(정규화: trim·빈값·중복 제거, 다른 키는 보존). **목록이 자주 바뀌므로 코드에 하드코딩하지 않는다.**
- CLI 는 모델 목록을 알려주는 명령을 제공하지 않는다(`claude models` 류는 서브커맨드가 아니라 프롬프트로 처리된다). 그래서 자동 수집은 불가능하고 **수동 편집이 유일한 현실적 방법**이다. V5 가 CLAUDE_CODE 에 `claude --help` 에 나온 alias 예시(`opus`/`sonnet`/`fable`)와 `--permission-mode` 선택지 6개(`acceptEdits`/`auto`/`bypassPermissions`/`manual`/`dontAsk`/`plan`)를 시드하고, 나머지 Provider 는 빈 목록 + "미확인" 메모로 둔다(추측 금지).
- `Agent.model` 은 CLI 의 `--model`(alias 또는 전체 이름), `Agent.mode` 는 **Provider CLI 의 실행/권한 모드**로 전달한다. Claude Code 는 `--permission-mode` 다. 값이 없으면 플래그를 붙이지 않고 CLI 기본값을 쓴다.
- 목록에 없는 값도 **막지 않는다**(최종 판단은 CLI). 화면은 datalist 로 목록을 제안하고, 목록에 없는 값이면 경고 문구만 보여 준다. 백엔드는 `model`/`mode` 를 자유 문자열로 저장한다.

## Permission Enforcement

Agent의 `permissionProfile`은 DB의 boolean 플래그(FILE_READ/FILE_WRITE/TERMINAL/GIT_*/DB_*/DEPLOY 등)로 저장한다. Execution을 만드는 경로(`POST /executions`, `POST /tasks/{id}/run`)는 모두 `ExecutionService`를 지나며, `PermissionService.isAllowed(profile, TERMINAL_EXECUTE)`가 false면 Runtime을 호출하지 않고 **403**으로 즉시 거부한다. Task 실행으로 위임하는 경우에도 검사 대상은 실행을 담당하는 Agent(그룹이면 리더)의 프로필이다. Prompt에 권한을 설명하는 것으로 끝내지 않는다.

실행은 **연결 상태에도 종속**된다. 권한 검사 직후 `ExecutionService` 가 `AgentAvailability.evaluate(...)` 로 Provider 의 `deleted_at` 과 Connection 상태를 확인해, Provider 가 삭제됐거나 Connection 이 `CONNECTED` 가 아니면 Runtime 을 호출하지 않고 **409**(`ConflictException`)로 거부한다. 새 실행만 차단하고 이미 실행 중인 Execution 은 건드리지 않는다. `POST /executions` 와 `POST /tasks/{id}/run` 이 모두 이 경로를 지나므로 우회할 수 없다. 사용 불가 Agent 는 `PUT /agents/{id}/provider` 로 다른 Provider 를 재할당해 복구하며, Agent 응답에는 파생값 `available`/`unavailableReason`(`PROVIDER_DELETED`/`CONNECTION_NOT_CONNECTED`)이 있다. 이 판정은 저장하지 않고 매번 계산한다.

`Agent.mode`(권한/실행 모드 문자열)는 이 가드와 별개다. 그 값은 CLI 에 전달될 뿐이고, 앱이 강제하는 것은 위 두 가지(권한 프로필, 연결 상태)다.

## 실행 방법

### 사전 준비

- JDK 25가 필요하다. 이 개발 환경에서는 `C:\Users\chey.kim\.jdks\openjdk-25.0.2`에 있고 PATH에 없으므로, Gradle을 직접 호출할 때는 `JAVA_HOME`을 지정한다 (Gradle Wrapper는 `JAVA_HOME` 또는 PATH의 `java`를 요구한다).

```powershell
$env:JAVA_HOME='C:\Users\chey.kim\.jdks\openjdk-25.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

- Postgres 접속 정보는 OS 환경변수(`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`)로 설정한다. Spring Boot는 `.env`를 자동으로 읽지 않으므로 `apps/backend/.env.example`을 참고해 export 하거나 `application-local.yaml`을 만들어 override한다. 기본값은 `jdbc:postgresql://localhost:5432/AGENT_DOCK`, `postgres`, 빈 비밀번호.

```
# 1. 빈 데이터베이스 생성 (최초 1회)
createdb AGENT_DOCK
# 또는: psql -U postgres -c 'CREATE DATABASE "AGENT_DOCK";'

# 2. 프론트 의존성 설치
npm install

# 3. 서버 실행 (기동 시 Flyway가 db/migration의 SQL을 자동 적용한다)
npm run dev:backend    # apps/backend, PORT 8080 (Gradle bootRun)
npm run dev:frontend   # apps/frontend, PORT 3030 (Vite)
```

Flyway 마이그레이션을 추가할 때는 `apps/backend/src/main/resources/db/migration/V<N>__<설명>.sql`을 새 버전 번호로 만들어 추가한다(기존 파일은 수정하지 않는다).

### AI 에이전트가 직접 실행/테스트할 때의 포트

사용자가 로컬에서 쓰는 개발 포트는 백엔드 `8080`, 프론트 `3030`이다. AI 에이전트가 기능 검증을 위해 서버를 직접 띄울 때는 이 포트를 절대 쓰지 않는다 (사용자가 동시에 같은 포트로 작업 중일 수 있어 충돌한다). 대신 백엔드 `8081`, 프론트 `3031`을 쓴다.

```
npm run dev:backend:test    # apps/backend, PORT 8081
npm run dev:frontend:test   # apps/frontend, PORT 3031
```

프론트를 백엔드 8081에 붙여 검증할 때는 `VITE_API_BASE`를 넘긴다 (`apps/frontend/.env.local`의 기본값은 8080이다).

```powershell
$env:VITE_API_BASE='http://localhost:8081'; npm run dev:frontend:test
```

`.claude/launch.json`의 `frontend`/`backend` 항목이 이미 이 테스트 포트(3031/8081)로 설정되어 있으니, Claude Code 브라우저 프리뷰(`preview_start`)는 그대로 쓰면 된다. 검증이 끝나면 띄운 서버(포트)를 바로 종료한다 — 백그라운드에 남겨두지 않는다.

DB 확인이 필요하면 `psql`이 PATH에 없으므로 전체 경로를 쓴다: `C:\Program Files\PostgreSQL\17\bin\psql.exe -U postgres -d AGENT_DOCK -c "..."`.

## 코딩 컨벤션

- 코드/식별자는 영어, 커밋 메시지와 문서의 설명 텍스트는 한국어/영어 혼용 가능
- 새 Runtime을 추가할 때는 `AgentRuntime` 인터페이스만 구현하고 `@Component`로 등록한다. `RuntimeRegistry`가 `List<AgentRuntime>` 주입으로 자동 색인하므로 기존 모듈을 수정하지 않는다. probe(`AiConnectionProbe`)와 로그인 명령(`AiLoginCommand`)도 같은 패턴이다.
- DTO는 요청은 `record` + Jakarta Bean Validation(`@NotBlank`, `@NotNull` 등)과 `@Valid`로 검증하고, 응답은 엔티티를 직접 반환하지 않고 `record` Response DTO로 매핑한다(양방향 연관관계로 인한 Jackson 순환 참조 방지). Controller는 얇게, 비즈니스 로직은 Service에 둔다.
- FK를 읽기 전용 shadow 필드(`insertable = false, updatable = false`)로 함께 두는 엔티티는 저장 직후 그 값이 비어 있다. 생성 응답에서 FK id가 필요하면 관계에서 보완한다(`ExecutionResponse.from` 참고).
- 저장 후 응답에 FK 가 필요하지만 같은 트랜잭션에서 다시 읽으면 shadow 필드가 옛 값으로 남는 경우(`AgentService.assignProvider`)는 `@Transactional` 없이 저장을 커밋한 뒤 새로 조회한다.
- 오래 걸리는 작업(CLI probe, 로그인 세션)에는 `@Transactional` 을 붙이지 않는다. DB 세션을 오래 잡으면 안 되므로 조회 시점에 필요한 관계를 fetch join 으로 함께 읽는다.
- POST 엔드포인트는 기존 NestJS 동작과 맞추기 위해 **201**을 반환한다(`@ResponseStatus(HttpStatus.CREATED)`). 삭제(204)처럼 본문이 없는 응답은 프론트 `request()` 가 204 를 따로 처리한다.
- 변하지 않는 규칙(권한, 연결 상태)은 백엔드가 강제하고, 자주 변하는 값(모델/모드 문자열)은 데이터로 두고 CLI 에 위임한다. 새 값이 생겼다고 코드를 고치지 않는다.

## 브랜치 흐름과 커밋 컨벤션

브랜치 흐름은 `기능 브랜치 → dev → test → release` 다. `dev`는 통합 브랜치이고, `test`와 `release`는 `dev`에서 병합해 만든다. 따라서 **`dev`에 쌓이는 커밋 이력이 곧 릴리스 노트의 원천**이고, 커밋 메시지 규칙이 두 단계로 나뉜다.

### 1) 기능 브랜치 — 글로벌 컨벤션([Conventional Commits](https://www.conventionalcommits.org/))

기능 브랜치에서 작업할 때는 Conventional Commits 형식을 쓴다.

```
feat: 에이전트 실행에 permission enforcement 추가
fix(workspace): 폴더 선택에서 드라이브 루트 조회가 멈추던 문제 수정

본문(선택): 왜 변경했는지
```

- 타입: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`
- scope는 선택: `fix(workspace): ...`, `feat(backend): ...`
- 제목은 소문자 타입 + 콜론 + 한 칸 + 간결한 설명 (한국어 허용)
- 여러 변경을 한 커밋에 몰아넣지 않는다. 하나의 커밋은 하나의 논리적 단위로 나눈다.

### 2) `dev`에 반영되는 커밋 — squash merge + `[Type] 제목`

기능 브랜치를 `dev`에 넣을 때는 **squash merge**하고, 그 squash 커밋 메시지(= PR 제목)를 `[Type] 제목` 형식으로 쓴다.

```
[Feat] NestJS/Next.js → Spring Boot + Vite React 스택 전환
[Fix] 폴더 선택에서 드라이브 루트 조회가 멈추던 문제 수정
```

- 타입은 대괄호 + 대문자 시작: `[Feat]` `[Fix]` `[Docs]` `[Style]` `[Refactor]` `[Perf]` `[Test]` `[Build]` `[Ci]` `[Chore]` `[Revert]`
- 대괄호 뒤에 한 칸 띄우고 제목을 쓴다. scope/콜론 형식(`feat(backend): ...`)은 쓰지 않는다. (한국어 허용)
- 기능 브랜치의 개별 커밋은 squash되므로 `dev`에는 남지 않는다. 즉 `dev` 이력은 항상 `[Type]` 형식이다.
- Breaking change는 본문에 `BREAKING CHANGE: <설명>`을 추가한다.
- AI 에이전트가 만든 커밋에도 `Co-Authored-By:` 트레일러를 붙이지 않는다.
