# AgentDock 스택 전환 브리프: NestJS/Next.js → Spring Boot/React

이 문서는 다른 AI 코딩 에이전트(또는 개발자)가 이 문서만 읽고 바로 구현에 착수할 수 있도록 작성된 실행 브리프다. 구현자는 이 문서를 코드로 옮기는 역할이며, 설계 결정은 이미 사용자와 확정되었다.

## 1. 배경 / 목적

현재 `apps/backend`(NestJS + TypeScript + Prisma), `apps/frontend`(Next.js + React)로 구성된 Phase 1 MVP가 동작 중이다. 프로젝트 소유자가 Java/Spring 생태계에 더 익숙하여, 스택을 다음과 같이 전환하기로 결정했다:

- 백엔드: NestJS/TypeScript → **Java + Spring Boot**
- 프론트엔드: Next.js → **Vite + React + React Router** (순수 SPA, SSR/파일 기반 라우팅 없음)

이 전환은 **기능 재구현이 아니라 기존 Phase 1 기능의 1:1 포팅**이다. 새 기능을 추가하지 않는다. User/인증(멀티유저 + 역할) 기능은 **이 작업 범위에서 명시적으로 제외**한다 — 스택 전환이 끝난 뒤 별도 브리프로 진행한다.

## 2. 범위

### 포함
- `apps/backend`의 6개 모듈(ai-provider, agent, role, permission, workspace, execution)과 공통 런타임 계층(AgentRuntime/ClaudeCodeRuntime/RuntimeRegistry/ProcessService)을 Spring Boot로 동등 포팅
- `apps/frontend`의 5개 페이지(Dashboard, Providers, Workspaces, Agents, Execution 상세)를 Vite+React로 동등 포팅
- PostgreSQL DB는 유지하되 스키마 관리 주체를 Prisma → JPA/Flyway로 전환(아래 4절 참고)
- 기존 `apps/backend`, `apps/frontend` 디렉터리는 **삭제 후 같은 경로에 새로 생성**한다(모노레포 구조 `apps/backend`, `apps/frontend` 그대로 유지, 내용물만 교체). 병행 운영하지 않는다.

### 제외 (이번 작업에서 하지 않음)
- User/인증/세션/Spring Security — 별도 브리프에서 다룬다
- Phase 2 이후 기능(Group, Task, Workflow, Shared Context, Message, Artifact, Review, Decision) — 여전히 범위 밖
- 실데이터 마이그레이션 — 현재 로컬 DB에 실데이터 없음(전부 삭제된 상태). 새 스키마로 처음부터 시작해도 된다.

## 3. 기술 스택 결정

| 영역 | 선택 | 비고 |
|---|---|---|
| 백엔드 언어/프레임워크 | Java 21 (LTS) + Spring Boot 3.x | |
| 빌드 도구 | **Gradle (Kotlin DSL)** | 사용자 지정 |
| ORM | **Spring Data JPA (Hibernate)** | 사용자 지정 |
| 쿼리 | **QueryDSL** | 사용자 지정. 현재 쿼리는 대부분 단순 CRUD/findMany라 QueryDSL의 이점이 크지 않지만, Phase 2+에서 동적 필터링이 늘어날 것을 감안해 기반을 깔아둔다. 최소 1곳 이상(예: Agent 목록 조회)에 실제로 적용해 패턴을 남길 것 |
| DB 스키마 관리 | **Flyway** (권장, 아래 근거 참고) | 사용자가 명시하지 않아 구현자가 판단: JPA `ddl-auto`는 스키마 변경 이력 추적이 안 되고 프로덕션에 부적합. 기존 Prisma migrations와 사상이 같은 Flyway SQL 마이그레이션으로 대체 |
| DB | PostgreSQL (기존 유지) | DB명 `AGENT_DOCK`, 로컬 `postgresql://postgres@localhost:5432/AGENT_DOCK` |
| 프론트 프레임워크 | **Vite + React 18 + React Router** | 사용자 지정. Next.js의 App Router/SSR/파일 기반 라우팅 제거, 순수 SPA |
| 프론트 스타일링 | CSS Modules (기존과 동일한 방식 유지) | Vite가 `*.module.css`를 기본 지원하므로 기존 `globals.css` + 페이지별 `*.module.css` 구조를 그대로 가져올 수 있다 |
| 프론트 상태관리 | 없음 (컴포넌트 로컬 `useState` + `fetch`) | 기존 Next.js 프론트도 전역 상태관리 라이브러리를 쓰지 않는 단순 구조. 그대로 유지, Redux/Zustand 등 새로 도입하지 않는다(YAGNI) |
| Validation | Jakarta Bean Validation (`@NotBlank`, `@Min` 등) + Spring `@Valid` | 기존 NestJS `class-validator` DTO 검증과 1:1 대응 |

## 4. 디렉터리 구조

```
apps/
  backend/                  # 삭제 후 Spring Boot 프로젝트로 교체
    build.gradle.kts
    settings.gradle.kts
    src/main/java/com/agentdock/backend/
      BackendApplication.java
      provider/            # AiProvider, AiConnection
      agent/                # Agent
      role/                 # AgentRole
      permission/           # PermissionProfile, PermissionAction
      workspace/             # Workspace, 폴더 브라우징
      execution/             # Execution, ExecutionLog, SSE
      runtime/               # AgentRuntime 인터페이스, ClaudeCodeRuntime, RuntimeRegistry
      process/                # ProcessService (ProcessBuilder 래퍼)
      common/                 # 공통 예외 핸들러 등
    src/main/resources/
      application.yml
      db/migration/          # Flyway SQL (V1__init.sql ...)
  frontend/                 # 삭제 후 Vite+React 프로젝트로 교체
    src/
      main.tsx
      App.tsx                # React Router 라우트 정의
      pages/
        Dashboard.tsx
        Providers.tsx
        Workspaces/
          Workspaces.tsx
          WorkspacePicker.tsx
      lib/api.ts
      *.module.css, globals.css (기존 그대로 이식)
```

Java 패키지 루트는 `com.agentdock.backend`로 한다(그룹ID `com.agentdock`, 아티팩트ID `backend`).

## 5. 도메인 모델 — Prisma → JPA 엔티티 매핑

원본: `apps/backend/prisma/schema.prisma` (삭제 전 마지막 상태 기준. 모든 PK/FK는 Int autoincrement, cuid 아님 — 최근 마이그레이션으로 이미 정수 ID로 전환 완료된 상태에서 포팅을 시작한다).

### Enum

```java
public enum ProviderKey { CLAUDE_CODE, CODEX, COMMAND_CODE, GEMINI }
public enum ConnectionStatus { CONNECTED, DISCONNECTED, ERROR }
public enum ExecutionStatus { PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED }
public enum LogStream { STDOUT, STDERR, SYSTEM }
```

### 엔티티 (테이블명은 기존 `@@map`과 동일하게 snake_case 유지)

| Prisma 모델 | 테이블명 | JPA 엔티티 필드 |
|---|---|---|
| `AiProvider` | `ai_provider` | `id`(PK, Int), `key`(ProviderKey, unique), `name`(String), `capabilities`(JSON — `@JdbcTypeCode(SqlTypes.JSON)` 또는 `jsonb` 컬럼), `connections`(OneToMany), `agents`(OneToMany), `createdAt`, `updatedAt` |
| `AiConnection` | `ai_connection` | `id`, `providerId`(FK→AiProvider), `accountName`(nullable), `credentialReference`(nullable — **평문 시크릿 저장 금지, 참조 문자열만**), `status`(ConnectionStatus, default DISCONNECTED), `agents`(OneToMany), `createdAt`, `updatedAt` |
| `AgentRole` | `agent_role` | `id`, `name`(unique), `description`(nullable), `agents`(OneToMany), `createdAt`, `updatedAt` |
| `PermissionProfile` | `permission_profile` | `id`, `name`(unique), boolean 필드 12개(아래 표), `agents`(OneToMany), `createdAt`, `updatedAt` |
| `Workspace` | `workspace` | `id`, `name`(unique), `path`(unique, String), `description`(nullable), `agents`(OneToMany), `executions`(OneToMany), `createdAt`, `updatedAt` |
| `Agent` | `agent` | `id`, `name`, `roleId`(FK), `permissionProfileId`(FK), `providerId`(FK), `connectionId`(FK, nullable), `workspaceId`(FK, nullable), `model`(nullable), `mode`(nullable), `profile`(JSON, nullable), `executions`(OneToMany), `createdAt`, `updatedAt` |
| `Execution` | `execution` | `id`, `agentId`(FK), `workspaceId`(FK), `prompt`(Text), `status`(ExecutionStatus, default PENDING), `startedAt`(nullable), `finishedAt`(nullable), `exitCode`(nullable Int), `errorMessage`(nullable Text), `logs`(OneToMany), `createdAt`, `updatedAt` |
| `ExecutionLog` | `execution_log` | `id`, `executionId`(FK, 인덱스), `stream`(LogStream), `content`(Text), `createdAt` |

**PermissionProfile의 boolean 필드 12개** (전부 `default false`, 단 `gitStatus`만 `default true`):
`fileRead, fileWrite, terminalExecute, gitStatus(default true), gitDiff, gitCommit, gitPush, dbRead, dbWrite, dbSchemaChange, deploy, externalNetworkAccess`

이 필드명은 `PermissionAction` enum과 1:1 대응하므로 이름을 바꾸지 말 것(아래 7.2절).

## 6. API 계약 — 전체 엔드포인트

프론트를 그대로 포팅하려면 **경로, HTTP 메서드, 요청/응답 JSON shape를 정확히 동일하게 유지**해야 한다. 필드명(camelCase)도 그대로 유지한다(Jackson 기본 설정으로 camelCase 직렬화됨 — 별도 설정 불필요).

### `/ai-providers`
- `GET /ai-providers` → `AiProvider[]` (각 항목에 `connections` include)
- `POST /ai-providers` body `{ key, name, capabilities? }` → 생성된 `AiProvider`. `key`는 `ProviderKey` enum 값 중 하나만 허용(잘못된 값이면 400)
- `POST /ai-providers/connections` body `{ providerId: number, accountName?, credentialReference? }` → 생성된 `AiConnection`
- `GET /ai-providers/:id/connections` (`:id`는 정수, 아니면 400) → `AiConnection[]`

### `/roles`
- `GET /roles` → `AgentRole[]`
- `POST /roles` body `{ name, description? }` → 생성된 `AgentRole`

### `/permission-profiles`
- `GET /permission-profiles` → `PermissionProfile[]`
- `POST /permission-profiles` body `{ name, fileRead?, fileWrite?, ... (12개 boolean, 전부 optional) }` → 생성된 `PermissionProfile`

### `/workspaces`
- `GET /workspaces` → `Workspace[]` (name 오름차순 정렬)
- `POST /workspaces` body `{ name, path, description? }` → 생성된 `Workspace`. 서버는 **경로 존재 여부와 디렉터리 여부만** 검증한다(더 이상 allowlist 없음 — 로컬 전용 도구이므로 임의 경로 허용). 존재하지 않으면 400, 디렉터리가 아니면 400. UNC 경로(`\\server\share`, `//server/share`)는 무조건 400 (NTLM 크리덴셜 유출 방지)
- `GET /workspaces/browse?path=<optional>` → `{ path: string|null, parentPath: string|null, entries: {name, path}[] }`
  - `path` 파라미터 없으면 최상위 시작점 반환: Windows는 존재하는 드라이브 목록(`C:\`, `D:\` ...), 그 외 OS는 `/` 하나
  - `path` 있으면 그 하위의 **디렉터리만**(파일 제외) 이름순 정렬해서 반환, `parentPath`는 파일시스템 루트에 도달하면 `null`
  - UNC 경로는 400, 존재하지 않는 경로는 404, 디렉터리가 아니면 400

### `/agents`
- `GET /agents` → `Agent[]` (name 오름차순, `role`/`permissionProfile`/`provider`/`connection`/`workspace` 전부 include)
- `GET /agents/:id` (`:id` 정수 아니면 400) → 단건, 없으면 404
- `POST /agents` body `{ name, roleId: number, permissionProfileId: number, providerId: number, connectionId?: number, workspaceId?: number, model?: string, mode?: string, profile?: object }` → 생성된 `Agent`

### `/executions`
- `POST /executions` body `{ agentId: number, prompt: string }` → 생성된 `Execution` (status=PENDING으로 즉시 응답, 실제 실행은 비동기). 처리 흐름은 7.3절 참고
- `GET /executions/:id` → 단건
- `GET /executions/:id/logs` → `ExecutionLog[]` (createdAt 오름차순)
- `GET /executions/:id/stream` (**SSE**, Server-Sent Events) → 실행 중인 프로세스의 실시간 로그. 이미 끝난 실행을 구독하면 빈 스트림. 이벤트 데이터 shape: `{ stream: 'stdout'|'stderr', content: string }`
- `POST /executions/:id/cancel` → `{ cancelled: true }`, 해당 실행의 실제 OS 프로세스를 SIGTERM으로 종료

## 7. 핵심 설계 원칙 — 반드시 유지할 것

이 원칙들은 `agents/CONVENTIONS.md`(저장소 루트)에 명시된 설계 원칙이며, 스택이 바뀌어도 그대로 유지해야 한다.

### 7.1 Agent != AI Runtime
`AgentRuntime` 인터페이스로 실행 엔진을 추상화한다. Java 버전:

```java
public interface AgentRuntime {
    String getProviderKey();
    AgentExecutionResult execute(AgentExecutionRequest request) throws Exception;
    void cancel(String executionId);
}

public record AgentExecutionRequest(
    String executionId,      // Execution PK를 String으로 변환한 값 (7.3절 참고)
    String prompt,
    String workspacePath,
    String model,             // nullable
    String mode,               // nullable
    BiConsumer<String, LogStreamType> onLog   // (chunk, stdout|stderr)
) {}

public record AgentExecutionResult(int exitCode) {}
```

`ClaudeCodeRuntime`이 유일한 현재 구현체다(`providerKey = "CLAUDE_CODE"`). `RuntimeRegistry`(Map 기반, provider key → 구현체)가 Spring Bean으로 등록된 모든 `AgentRuntime`을 주입받아 관리한다(`List<AgentRuntime>` 생성자 주입 → Map으로 색인). 새 Runtime 추가 시 이 인터페이스만 구현하고 Spring Bean으로 등록하면 되고, 다른 코드는 건드리지 않는다.

`ClaudeCodeRuntime`은 `claude` CLI를 비대화형 모드로 실행한다: `claude -p "<prompt>" --output-format text [--model <model>]`, `cwd`는 Agent의 workspace path. 바이너리 경로는 환경변수 `CLAUDE_CODE_BIN`으로 override 가능(기본값 `claude`).

### 7.2 Permission Enforcement — 백엔드에서 실제로 차단
Prompt 설명으로 권한을 강제하지 않는다. `Execution` 생성 시(`/executions` POST) Agent의 `PermissionProfile`에서 `TERMINAL_EXECUTE`(=`terminalExecute` 필드)가 `false`면 **즉시 403**으로 거부하고 Runtime을 호출하지 않는다.

```java
public enum PermissionAction {
    FILE_READ, FILE_WRITE, TERMINAL_EXECUTE, GIT_STATUS, GIT_DIFF,
    GIT_COMMIT, GIT_PUSH, DB_READ, DB_WRITE, DB_SCHEMA_CHANGE,
    DEPLOY, EXTERNAL_NETWORK_ACCESS
}
```
각 값은 `PermissionProfile`의 동명 boolean 필드(camelCase→다른 케이스 변환 없이 그대로, 예: `TERMINAL_EXECUTE` → `terminalExecute`)와 매핑된다. reflection이나 필드명 문자열 매칭으로 구현하거나, 단순히 switch문으로 매핑해도 된다 — 어느 쪽이든 기존 NestJS 구현(`Boolean(profile[action])`)과 같은 결과를 내면 된다.

### 7.3 Execution ID: DB용 숫자 ID vs Runtime/스트림용 문자열 키
`Execution.id`는 DB PK(정수)다. 하지만 SSE 스트림 관리(`Map<String, Subject/Emitter>`)와 `AgentRuntime`/`ProcessService`에 넘기는 `executionId`는 **문자열**로 통일한다(프로세스 관리 key로 자연스럽고, 기존 NestJS 구현과 동일한 설계). `Execution` 생성 직후 `String.valueOf(execution.getId())`를 스트림 키/Runtime executionId로 사용한다.

SSE 컨트롤러의 `:id` 경로 파라미터는 **문자열 그대로** 받아 스트림 Map 조회에 쓴다(정수 파싱 불필요 — DB 조회를 하지 않는 엔드포인트이기 때문). 반면 `findOne`/`getLogs`/`cancel`은 DB 조회가 필요하므로 정수로 파싱한다(Spring이면 `@PathVariable Long id` 또는 `Integer id`로 자동 바인딩, 파싱 실패 시 400).

`cancel`은 DB에서 Execution을 조회해 연결된 Agent/Provider를 찾고, `runtime.cancel(String.valueOf(execution.getId()))`를 호출한다.

### 7.4 ProcessService — spawn 래퍼
`ProcessBuilder`로 구현한다. **shell 경유 금지**(`ProcessBuilder`는 기본적으로 shell을 거치지 않으므로 NestJS의 `shell: false`와 동등) — 사용자 prompt가 셸 명령으로 해석되지 않도록 하기 위함(명령 인젝션 방지). `executionId(String) → Process` Map으로 실행 중 프로세스를 관리하고, `cancel`은 `Process.destroy()`(또는 `destroyForcibly()`, SIGTERM 상당)를 호출한다. 프로세스 종료 시 Map에서 제거한다.

### 7.5 Workspace — 경로 제한 없음, UNC만 차단
`WORKSPACE_ALLOWED_ROOTS` 같은 allowlist는 **의도적으로 없다**(로컬 전용 단일 사용자 도구이므로, 사용자가 폴더 선택 UI로 고른 경로는 그대로 신뢰하고 저장한다). 단, UNC/네트워크 경로(`\\...`, `//...`)는 NTLM 자격 증명 유출 위험이 있어 무조건 거부한다. Workspace 생성 시에도, 폴더 브라우징 API 호출 시에도 이 검사를 적용한다.

## 8. 프론트엔드 포팅 가이드

기존 5개 페이지를 React Router 라우트로 그대로 옮긴다. Next.js 전용 요소(`'use client'`, 파일 기반 라우팅, `app/layout.tsx`)만 제거하고 나머지 컴포넌트 로직/JSX/CSS Modules는 최대한 그대로 가져온다.

| 기존 (Next.js) | 신규 (React Router) |
|---|---|
| `src/app/layout.tsx` (nav) | `src/App.tsx`의 공통 레이아웃 컴포넌트 (`<Outlet/>` 사용) |
| `src/app/page.tsx` (Dashboard) | 라우트 `/` → `pages/Dashboard.tsx` |
| `src/app/providers/page.tsx` | 라우트 `/providers` → `pages/Providers.tsx` |
| `src/app/workspaces/page.tsx` + `WorkspacePicker.tsx` | 라우트 `/workspaces` → `pages/Workspaces/*` |
| `src/app/agents/page.tsx` | 라우트 `/agents` → `pages/Agents.tsx` |
| `src/app/executions/[id]/page.tsx` | 라우트 `/executions/:id` → `pages/ExecutionDetail.tsx`. `useParams()`로 `id` 획득 (기존 `params.id` prop 대체) |

`src/lib/api.ts`는 거의 변경 없이 그대로 이식 가능(순수 `fetch` 래퍼, Next.js 의존성 없음). `EventSource`(SSE 클라이언트) 코드도 그대로 이식 가능.

CSS: `globals.css`, `layout.module.css`, 각 페이지 `page.module.css`, `WorkspacePicker.module.css` — Vite가 `*.module.css`를 기본 지원하므로 내용 변경 없이 파일만 옮기면 된다. `globals.css`는 `main.tsx`에서 import한다.

환경변수: Next.js의 `NEXT_PUBLIC_API_BASE` → Vite는 `VITE_` 접두사 필요. `import.meta.env.VITE_API_BASE`로 대체하고 `.env.local`도 `VITE_API_BASE=http://localhost:8080`으로 이름을 바꾼다.

## 9. 로컬 실행/개발 포트 (기존 컨벤션 유지)

`agents/CONVENTIONS.md`에 명시된 포트 규칙을 그대로 따른다:
- 사용자 개발 포트: 백엔드 `8080`, 프론트 `3030` (Spring Boot `server.port=8080`, Vite `--port 3030`)
- AI 에이전트가 검증용으로 직접 띄울 때는 `8081`/`3031` 사용, 검증 끝나면 즉시 종료

## 10. 완료 기준 (Definition of Done)

다음을 모두 만족하면 포팅 완료로 간주한다:

1. `POST /ai-providers` → `POST /roles` → `POST /permission-profiles` → `POST /workspaces` → `POST /agents` → `POST /executions` 순서로 curl/httpie 호출했을 때 기존과 동일한 응답 shape와 상태 코드를 반환한다
2. 잘못된 정수 경로 파라미터(`GET /agents/abc`)에 400을 반환한다
3. `terminalExecute: false`인 PermissionProfile을 가진 Agent로 Execution을 생성하면 403을 반환하고 프로세스가 실행되지 않는다
4. UNC 경로(`\\server\share`)로 Workspace 생성/브라우징 시도 시 400을 반환한다
5. 존재하지 않는 로컬 경로로 Workspace 생성 시 400, 존재하는 파일(디렉터리 아님)로 시도 시 400을 반환한다
6. `GET /workspaces/browse`(path 없이) 호출 시 OS의 드라이브/루트 목록을 반환한다
7. 프론트 5개 페이지가 모두 렌더링되고, Workspace 폴더 선택 모달로 하위 폴더 탐색 및 등록이 동작한다
8. Agent 생성 후 Run 버튼으로 Execution을 생성하면 `/executions/:id` 상세 페이지로 이동하고, `claude` CLI가 로컬에 설치되어 있다면 실시간 로그가 SSE로 스트리밍된다
9. `agents/CONVENTIONS.md`를 이 전환 내용에 맞게 갱신한다(기술 스택 표, Backend 모듈 절 등) — CLAUDE.md/AGENTS.md/GEMINI.md는 포인터만 유지하므로 손댈 필요 없음

## 11. 참고 — 이번에 하지 않는 것 (재확인)

- User/인증/Spring Security: 별도 브리프
- Phase 2 이후 엔티티(Group/Task/Workflow/Context/Message/Artifact/Review/Decision): 범위 밖
- CI/CD, Docker화: 별도 요청 없는 한 범위 밖
