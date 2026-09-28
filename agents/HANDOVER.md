# 인수인계 문서 (2026-09-28 클라우드 세션 → 로컬 에이전트)

이 문서는 이번 클라우드 세션에서 한 작업의 상태 스냅샷이다. 상시 규칙/구조는 [`agents/CONVENTIONS.md`](./CONVENTIONS.md)에 있으니 그걸 먼저 읽고, 여기는 "지금 어디까지 됐고 다음에 뭘 할지"만 본다.

## 브랜치

`claude/clever-archimedes-ao2gcg` (origin에 push 완료, 로컬에도 동일하게 checkout해서 이어가면 됨)

## 지금까지 한 것 (커밋 순서)

1. `3742c54` — Phase 1 MVP 스캐폴딩. Prisma 스키마, NestJS 백엔드(Agent/Provider/Role/Permission/Workspace/Execution 모듈, AgentRuntime 추상화 + ClaudeCodeRuntime, SSE 로그 스트리밍), Next.js 프론트(Provider/Workspace/Agent 관리 화면, Execution 로그 뷰어). 로컬 Postgres로 마이그레이션 + 실제 Claude CLI 실행까지 E2E 검증함.
2. `ff25701` — `apps/server`→`apps/backend`, `apps/web`→`apps/frontend` 이름 정리, `.gitignore` 보강.
3. `2f5fd43` — `.env.example`의 `DATABASE_URL`을 실제 로컬 환경(postgres 계정, 비번 없음, DB명 `AGENT_DOCK`)에 맞게 수정.
4. `b529342`, `fb17f67`, `5ae6b99` — 문서 정리: Conventional Commits 컨벤션 추가, 공용 작업 문서를 `agents/CONVENTIONS.md`로 일원화하고 `CLAUDE.md`/`AGENTS.md`/`GEMINI.md`를 진입점(포인터)으로만 남김, DB 초기 세팅 절차(`createdb` → `prisma migrate`) 문서화.
5. `602bfc0` — 포트 변경: 백엔드 `8080`, 프론트 `3030`.

## 로컬에서 이어서 하려면

```powershell
cd C:\Users\chey.kim\Documents\GitHub\AgentDock
git fetch origin claude/clever-archimedes-ao2gcg
git checkout claude/clever-archimedes-ao2gcg   # 없으면 -b 로 추적
npm install
```

`apps/backend/.env`가 로컬에 없으면 직접 만들어야 한다 (`.gitignore` 대상이라 커밋에 안 들어있음). `apps/backend/.env.example` 내용 그대로 복사하고 `WORKSPACE_ALLOWED_ROOTS`만 본인 환경 경로로 바꾸면 됨.

DB(`AGENT_DOCK`)가 로컬에 없으면 `createdb AGENT_DOCK` 먼저. 그다음 `apps/backend`에서 `npm run prisma:migrate`.

실행:

```
npm run dev:backend    # 8080
npm run dev:frontend   # 3030
```

## 알아두면 좋은 것 / 알려진 제약

- **Permission Enforcement는 실제로 동작함**: Agent 생성 시 연결한 `PermissionProfile`에 `terminalExecute: false`면 `/executions` 호출이 403으로 막힌다. 에이전트 만들 때 permission profile을 먼저 만들어야 함 (프론트 Agents 페이지의 "+ Profile" 버튼으로 즉석 생성 가능, 기본값은 file read/write/terminal true).
- **Workspace 경로 allowlist**: `WORKSPACE_ALLOWED_ROOTS` 밖의 경로는 Workspace 등록이 400으로 막힌다 (`apps/backend/src/modules/workspace/workspace-allowlist.ts`).
- **Credential은 참조만 저장**: `AiConnection.credentialReference`는 실제 토큰이 아니라 참조 문자열이다. 실제 OS Credential Store 연동은 아직 구현 안 함 — Phase 1 스코프 밖.
- **ClaudeCodeRuntime**은 `claude` CLI가 로컬 PATH에 있어야 동작한다 (`CLAUDE_CODE_BIN` 환경변수로 경로 override 가능). Codex/Gemini 등 다른 Runtime은 아직 구현 안 됨 — `AgentRuntime` 인터페이스만 있고 `RuntimeRegistry`에 `ClaudeCodeRuntime`만 등록돼 있음.
- **SSE 스트림**은 Execution이 이미 끝난 뒤 `/executions/:id/stream`을 새로 구독하면 빈 스트림만 온다 (진행 중인 실행에만 라이브 로그가 붙음). 과거 로그는 `/executions/:id/logs`로 조회.
- Docker가 이 컨테이너에는 없어서, 이번 세션 검증은 컨테이너에 있던 로컬 Postgres 16 클러스터로 했음. 로컬 Windows 환경에선 사용자가 이미 Postgres/DB를 갖고 있다고 함(`postgres` 계정, 비번 없음, DB명 `AGENT_DOCK`).

## 다음 단계 (Phase 2+, 아직 시작 안 함)

`agents/CONVENTIONS.md`의 Phase 구분 그대로:

- **Phase 2** — Group, GroupAgent (여러 Agent를 팀으로 묶기), Group 단위 Task 할당
- **Phase 3** — Task/Workflow/WorkflowStep, Agent 순차/병렬 실행
- **Phase 4** — Shared Context, Agent Message, Artifact(+버전), Review, Decision
- **Phase 5** — 동적 Task 분해, 실패 재시도, Context 자동 압축, Long-term Memory

아직 Group/Task/Workflow/Context/Message/Artifact/Review/Decision 관련 Prisma 모델이나 모듈은 하나도 없음 (의도적으로 Phase 1 범위에서 제외함, `agents/CONVENTIONS.md` 참고).
