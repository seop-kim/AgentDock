# AgentDock

멀티 에이전트 조직 운영 플랫폼. 상세 제품/아키텍처 스펙은 이 저장소를 만든 최초 이슈 본문(Multi-Agent Organization Platform - Product & Architecture Draft) 참고. 이 파일은 그 스펙을 구현 단위로 요약한 작업 기준 문서다. 새 작업을 시작하기 전에 이 파일을 먼저 읽고, 구조/컨벤션을 변경하면 이 파일도 함께 갱신한다.

## 기술 스택

- Backend: NestJS + TypeScript + Prisma + PostgreSQL (`apps/server`)
- Frontend: Next.js (App Router) + React + TypeScript (`apps/web`)
- Local Runtime 실행: `child_process.spawn(cmd, args, { shell: false })`, 브라우저가 CLI를 직접 실행하지 않음
- 실시간 로그: SSE (Server-Sent Events)

## 모노레포 구조

```
apps/
  server/   NestJS API. 아래 "Backend 모듈" 참고
  web/      Next.js UI
```

npm workspaces 사용 (`package.json` 루트).

## 핵심 설계 원칙 (스펙 33장)

1. Agent(논리적 직원) != AI Runtime(실행 엔진). `AgentRuntime` 인터페이스로 분리하고 Provider 설정에 따라 구현체(ClaudeCodeRuntime, CodexRuntime, ...)를 선택한다.
2. Task/Execution 중심 설계. Agent 자체보다 실행 단위(Execution)와 결과(Artifact/Log)를 추적한다.
3. 권한은 Prompt 설명만으로 강제하지 않는다. Backend Tool Layer(Permission Enforcement)에서 실제로 차단한다.
4. Agent 간 통신은 전체 대화 공유가 아니라 Context/Message/Artifact/Decision 단위로 제한한다.
5. 모든 실행은 DB에 기록해 추적 가능해야 한다 (execution, execution_log).

## Phase 1 (현재 구현 범위)

- AI Provider/Connection 등록
- Agent 생성 + Role/Permission 설정
- Workspace 등록 (허용된 로컬 경로만 사용, 임의 경로 접근 금지)
- Agent에게 Prompt 전달 → CLI 실행 (spawn) → 실시간 로그(SSE)
- 모든 Execution/Log는 PostgreSQL에 저장

Group/Workflow/Task/Shared Context/Message/Artifact/Review/Decision은 이후 Phase(2~4)에서 확장. Phase 1 스캐폴딩 단계에서는 해당 엔티티를 만들지 않는다.

## Backend 모듈 (`apps/server/src`)

- `prisma/` — PrismaService, PrismaModule (전역)
- `common/runtime/` — `AgentRuntime` 인터페이스, `ClaudeCodeRuntime` 구현체, `RuntimeRegistry` (provider key → runtime 매핑)
- `modules/ai-provider/` — AiProviderModule/Service/Controller: Provider·Connection CRUD (Credential은 DB에 평문 저장 금지, `credentialReference`만 저장)
- `modules/agent/` — Agent CRUD. Role/Permission/Provider/Workspace 참조
- `modules/role/`, `modules/permission/` — Role, PermissionProfile CRUD
- `modules/workspace/` — Workspace CRUD, 등록된 경로만 허용(allowlist 검증)
- `modules/process/` — ProcessService: spawn 래퍼, 실행 중 프로세스 관리/취소
- `modules/execution/` — Execution 생성, ExecutionLog 저장, SSE 스트리밍 (`GET /executions/:id/stream`)

## Prisma 모델 (Phase 1)

`ai_provider`, `ai_connection`, `agent_role`, `permission_profile`, `agent`, `workspace`, `execution`, `execution_log`. 이름/관계는 `apps/server/prisma/schema.prisma` 참고. 이후 Phase에서 `group`, `task`, `workflow`, `context`, `agent_message`, `artifact`, `review`, `decision` 등을 추가한다 (스펙 23장 Entity 목록).

## Permission Enforcement

Agent의 `permissionProfile`은 DB의 boolean 플래그(FILE_READ/FILE_WRITE/TERMINAL/GIT_*/DB_*/DEPLOY 등)로 저장한다. Execution 실행 전 `ProcessService`/`ExecutionService`가 요청된 작업이 프로필에서 허용되는지 검사하고, 허용되지 않으면 Runtime에 전달하지 않고 즉시 거부한다. Prompt에 권한을 설명하는 것으로 끝내지 않는다.

## 실행 방법

```
npm install
npm run dev:server   # apps/server, PORT 3001
npm run dev:web       # apps/web, PORT 3000
```

Postgres 접속 정보는 `apps/server/.env` (`DATABASE_URL`)에 설정. `.env.example` 참고.

## 코딩 컨벤션

- 코드/식별자는 영어, 커밋 메시지와 이 문서 등 설명 텍스트는 한국어/영어 혼용 가능
- 새 Runtime을 추가할 때는 `AgentRuntime` 인터페이스만 구현하고 `RuntimeRegistry`에 등록한다. 기존 모듈을 수정하지 않는다.
- DTO는 `class-validator`로 검증, Controller는 얇게, 비즈니스 로직은 Service에 둔다.
