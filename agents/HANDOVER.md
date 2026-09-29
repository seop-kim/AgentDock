# 인수인계 문서 (2026-09-29)

이 문서는 다음 작업을 이어받는 에이전트(Claude Code 등)를 위한 현재 상태 스냅샷이다. 작업 방식·구조·규칙은 [`agents/CONVENTIONS.md`](./CONVENTIONS.md)가 기준 문서이므로 그것을 먼저 읽고, 이 문서는 "지금 어디까지 됐고 다음에 뭘 할지"만 다룬다.

## 1. 먼저 읽을 문서 (순서대로)

1. `agents/CONVENTIONS.md` — 스택, 모듈 구조, DB 스키마, 실행 방법, 포트 규칙, 브랜치/커밋 규칙의 단일 기준 문서
2. `.superpowers/sdd/2026-09-28-java-spring-react-migration/progress.md` — 실제 작업 장부. 각 단계의 결정 사항, 검증 결과, 발견해서 고친 결함, 남긴 제약이 시간순으로 기록돼 있다. **gitignore 대상이라 커밋에는 없고 로컬에만 있다.**
3. `docs/superpowers/specs/2026-09-28-java-spring-react-migration-brief.md` — 스택 전환 브리프(설계 결정 확정본)
4. `docs/superpowers/plans/2026-09-28-java-spring-react-migration.md` — 스택 전환 실행 계획(Task 1~18)
5. 이 저장소를 만든 최초 이슈 본문(Multi-Agent Organization Platform - Product & Architecture Draft) — 전체 제품 스펙. Phase 구분과 최종 목표는 여기 있다.

## 2. 브랜치와 현재 상태

브랜치 흐름은 `기능 브랜치 → dev → test → release` 다. `dev`가 통합 브랜치이며 `test`/`release`는 아직 만들지 않았다.

| 구분 | 내용 |
| --- | --- |
| `dev` | `fe94644` — 지금까지의 모든 작업이 squash 병합돼 있다 |
| `feat/group-task` | `7536864` — dev에 병합 완료(PR #2). 브랜치는 남아 있고 삭제해도 무방 |
| `feat/ai-connection` | `5490769` — **현재 작업 브랜치**. 원격에 push 완료, PR은 아직 만들지 않음 |

이미 병합된 PR:

- PR #1 `[Feat] NestJS/Next.js → Spring Boot + Vite React 스택 전환` — Phase 1 전체를 Java 25 / Spring Boot 4.1.1 / Gradle / JPA + QueryDSL / Flyway 와 Vite + React + React Router 로 1:1 포팅
- PR #2 `[Feat] Project/Group/Task 계층 추가와 Task 실행` — Phase 2. 계층이 `Project → Group → Agent` 로 바뀌고 워크스페이스가 프로젝트로 이동했다

`feat/ai-connection` 에서 한 작업(AI 연결 상태 확인):

- 스키마 V3: `ai_connection.last_checked_at`, `last_error`
- `AiConnectionProbe` / `ProbeRegistry` / `ClaudeCodeProbe` — CLI를 짧은 프롬프트로 한 번 실행해 실제 응답 여부를 확인
- `POST /ai-connections/{id}/check` — 결과를 `status`(`CONNECTED`/`ERROR`), `last_error`, `last_checked_at` 에 저장
- Providers 화면에 connection 표(Account/Status/Last checked/오류), 연결 확인 버튼, Connection 추가 폼 추가
- 검증 중 결함 1건 수정: 지연 프록시에서 `getProvider().getKey()` 를 읽어 `LazyInitializationException` → fetch join 조회로 해결

PR 링크(아직 생성 전):

```
https://github.com/seop-kim/AgentDock/compare/dev...feat/ai-connection?expand=1
제목: [Feat] AI 연결 상태 확인(probe)과 Providers 연결 관리 UI
```

## 3. 바로 다음에 할 일 (우선순위 순)

1. **Provider별 probe/Runtime 추가 (CODEX / GEMINI / COMMAND_CODE)**
   현재 `ClaudeCodeRuntime` 과 `ClaudeCodeProbe` 만 있다. 다른 provider는 실행 시 404(`No AgentRuntime registered for provider`), 연결 확인 시 ERROR 로 표시된다.
   주의: 이 머신에는 `codex` / `gemini` / `cmdc` 가 설치돼 있지 않아 **실검증이 불가능**하다. 각 CLI의 비대화형 실행 플래그를 문서에서 확인한 뒤 구현하고, 설치 후 검증하는 순서가 맞다. 플래그를 추측해서 넣지 말 것.
2. **`capabilities` 기반 모델/모드 (스펙 5장)**
   `ai_provider.capabilities`(JSONB)는 저장·응답만 되고 읽는 코드가 없다. Provider별 지원 모델/모드를 정의해 API로 노출하고, Agents 화면에서 지원하는 모델/모드만 선택하게 만든다. `Agent.mode` 는 저장되고 `AgentExecutionRequest` 까지 전달되지만 `ClaudeCodeRuntime` 이 아직 사용하지 않으므로 Mode 의미(예: Plan/Execute)도 함께 정의해야 한다.
3. **CONNECTED 경로 검증**
   지금 `claude` CLI의 OAuth 세션이 만료돼 있어 모든 실행/연결 확인이 ERROR(exit 1, `Failed to authenticate: OAuth session expired`)로 끝난다. 파이프라인 자체는 정상이므로, 사용자가 `claude` 에 재로그인하면 성공 경로를 확인할 수 있다.
4. (선택) **로그인 안내 UX** — 연결 확인이 실패하면 사용자가 실행해야 할 명령을 UI에 함께 보여주기. 웹에서 대화형 로그인을 띄우는 것은 현실적으로 어렵다.
5. (선택) **OS Credential Store 연동**(스펙 6장 우선순위 2) — 현재는 CLI 세션 위임만 구현돼 있다. 후순위.

Phase 3 이후(Workflow/WorkflowStep 순차·병렬 실행, Shared Context, Message, Artifact, Review, Decision)는 아직 손대지 않았다.

## 4. 이 환경에서 작업할 때 (실측값)

- JDK 25는 `C:\Users\chey.kim\.jdks\openjdk-25.0.2` 에 있고 PATH에 없다. Gradle 호출 시 `JAVA_HOME` 을 지정해야 한다.
  ```powershell
  $env:JAVA_HOME='C:\Users\chey.kim\.jdks\openjdk-25.0.2'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
  ```
- DB는 PostgreSQL 17, DB명 `AGENT_DOCK`, 계정 `postgres`, 비밀번호 없음. `psql` 은 PATH에 없으므로 전체 경로를 쓴다: `C:\Program Files\PostgreSQL\17\bin\psql.exe`
- 백엔드 기동은 `apps/backend` 에서 `gradlew.bat bootRun`. 프론트는 루트에서 `npm run dev:frontend`(3030) 또는 `:test`(3031)
- **검증 포트 규칙**: 사용자 개발 포트는 백엔드 8080 / 프론트 3030 이다. 에이전트가 검증할 때는 8081 / 3031 을 쓰고, 끝나면 즉시 종료한다.
- 프론트를 8081 백엔드에 붙일 때는 `VITE_API_BASE=http://localhost:8081` 을 넘긴다.
- 브라우저 검증 도구로 `agent-browser`(0.38.1 + Chrome 154, `~/.agent-browser`)가 설치돼 있다. 세션을 나눠 쓰고 끝나면 `close` 한다.
- `gh` CLI 는 없다. PR 생성·병합은 GitHub 웹에서 한다.
- 테스트로 만든 데이터는 검증 후 TRUNCATE 한다(`flyway_schema_history` 는 유지). 현재 DB는 비어 있고 Flyway는 v1, v2, v3 까지 적용된 상태다.

## 5. 반복해서 밟은 함정 (중요)

- **`gradlew bootRun` 이 실패해도 BUILD SUCCESSFUL 로 보인다.** spring-boot-devtools 의 `RestartLauncher` 때문이다. 컴파일 통과만으로 끝내지 말고 반드시 기동 로그(`Started AgentDockApplication`)나 실제 HTTP 응답을 확인한다. 이 함정 때문에 컨텍스트 기동 실패를 놓칠 뻔했다.
- **`spring.jpa.open-in-view: false`** 다. 서비스에서 엔티티의 LAZY 관계를 그냥 읽으면 `LazyInitializationException` 이 난다. fetch join 조회를 쓰거나, 오래 걸리는 작업(예: CLI probe)에는 `@Transactional` 로 감싸지 말고 조회 시점에 함께 읽는다.
- **shadow FK 필드**(`insertable = false, updatable = false`)는 저장 직후 엔티티에서 비어 있다. 생성 응답에서 FK id 가 필요하면 관계에서 보완하는 기존 패턴(`ExecutionResponse.from` 등)을 따른다.
- 응답 DTO 는 엔티티를 직접 반환하지 않는다(`record` Response DTO). 양방향 연관관계 때문에 Jackson 순환이 생긴다.
- `POST` 는 201 을 반환한다(`@ResponseStatus(HttpStatus.CREATED)`). 기존 NestJS 동작과 맞춘 것이다.
- 검증용 스크립트에서 PowerShell 과 `curl.exe` 를 섞어 쓸 때 JSON 인용부호가 벗겨지는 문제가 있었다. `Invoke-WebRequest` 에 body 를 넘기는 방식을 쓰는 편이 안전하다.

## 6. 작업 방식 (기존 관례)

- 기능 브랜치 커밋은 Conventional Commits(`feat: ...`, `fix: ...`), `dev` 에 반영할 때는 squash merge 하고 그 커밋 메시지(PR 제목)를 `[Type] 제목` 형식으로 쓴다. 자세한 규칙은 `agents/CONVENTIONS.md` 의 "브랜치 흐름과 커밋 컨벤션" 참고.
- `Co-Authored-By:` 트레일러는 붙이지 않는다.
- 한 PR 은 하나의 논리적 단위로 유지한다.
- 구조나 컨벤션을 바꾸면 `agents/CONVENTIONS.md` 도 함께 갱신한다.
- 권한은 Prompt 설명이 아니라 백엔드에서 실제로 차단한다(`PermissionService.isAllowed`, `terminalExecute` 없으면 실행 전 403). 이 원칙은 유지한다.
- Agent(논리적 직원)와 Runtime(실행 엔진)은 분리한다. 새 Runtime/Probe 는 인터페이스만 구현하고 등록하면 나머지 코드는 건드리지 않는다.
